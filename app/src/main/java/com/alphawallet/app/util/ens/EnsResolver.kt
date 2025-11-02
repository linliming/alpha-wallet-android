package com.alphawallet.app.util.ens

import com.alphawallet.app.entity.tokenscript.TokenscriptFunction
import com.alphawallet.app.util.Utils
import com.alphawallet.app.web3j.ens.Contracts
import com.alphawallet.app.web3j.ens.EnsGatewayRequestDTO
import com.alphawallet.app.web3j.ens.EnsGatewayResponseDTO
import com.alphawallet.app.web3j.ens.EnsResolutionException
import com.alphawallet.app.web3j.ens.EnsUtils
import com.alphawallet.app.web3j.ens.NameHash
import com.alphawallet.app.web3j.ens.OffchainLookup
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import com.alphawallet.token.entity.ContractAddress
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.web3j.abi.DefaultFunctionReturnDecoder
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.crypto.Keys
import org.web3j.crypto.WalletUtils
import org.web3j.protocol.ObjectMapperFactory
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.EthCall
import org.web3j.utils.Numeric
import timber.log.Timber
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Suppress("UNCHECKED_CAST")
private fun <T : Type<*>> singleOutput(ref: TypeReference<T>): MutableList<TypeReference<Type<*>>> =
    mutableListOf(ref as TypeReference<Type<*>>)

/**
 * ENS 解析器，负责对 ENS 名称进行正向与反向解析，同时支持 CCIP Read 协议。
 * 该实现基于协程异步查询链上与 CCIP 网关接口。
 */
class EnsResolver @JvmOverloads constructor(
    private val web3j: Web3j,
    protected val addressLength: Int = Keys.ADDRESS_LENGTH_IN_HEX,
) : Resolvable {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(EnsResolver::class.java)

        val JSON: MediaType = "application/json".toMediaTypeOrNull()!!
        const val LOOKUP_LIMIT = 4
        private const val ENS_CACHE_TIME_VALIDITY = 10 * 60_000L
        const val REVERSE_NAME_SUFFIX = ".addr.reverse"
        const val CANCELLED_REQUEST = "##C"
        const val USE_ENS_CHAIN: Long = MAINNET_ID
        const val FUNC_SUPPORTS_INTERFACE = "supportsInterface"
        const val FUNC_ADDR = "addr"
        const val FUNC_RESOLVE = "resolve"
        const val FUNC_RESOLVE_WITH_PROOF = "resolveWithProof"
        const val FUNC_NAME = "name"

        private val cachedNameReads = ConcurrentHashMap<String, CachedEnsRead>()
        private val cachedResolver = ConcurrentHashMap<String, String>()
        private val cachedSupportsWildcard = ConcurrentHashMap<String, Boolean>()

        private var decoder: DefaultFunctionReturnDecoder? = null

        @JvmStatic
        fun isValidEnsName(input: String?): Boolean = isValidEnsName(input, Keys.ADDRESS_LENGTH_IN_HEX)

        @JvmStatic
        fun isValidEnsName(input: String?, addressLength: Int): Boolean =
            input != null && (input.contains(".") || !WalletUtils.isValidAddress(input, addressLength))

        /**
         * 解码链上返回的动态字节结果。
         */
        private fun decodeDynamicBytes(rawInput: String): ByteArray? {
            val localDecoder =
                decoder ?: DefaultFunctionReturnDecoder().also { decoder = it }
            val outputParameters = singleOutput(object : TypeReference<DynamicBytes>() {})
            val typeList = localDecoder.decodeFunctionResult(rawInput, outputParameters)
            return if (typeList.isEmpty()) {
                null
            } else {
                (typeList[0] as DynamicBytes).value
            }
        }

        /**
         * 解码链上返回的地址结果。
         */
        private fun decodeAddress(rawInput: String): String? {
            val localDecoder =
                decoder ?: DefaultFunctionReturnDecoder().also { decoder = it }
            val outputParameters = singleOutput(object : TypeReference<Address>() {})
            val typeList = localDecoder.decodeFunctionResult(rawInput, outputParameters)
            return if (typeList.isEmpty()) {
                null
            } else {
                (typeList[0] as Address).value
            }
        }
    }

    protected var chainId: Long = USE_ENS_CHAIN
    private var client: OkHttpClient = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentResolveRequestId: String? = null
    private val cancelledRequests = ConcurrentLinkedQueue<String>()

    init {
        scope.launch {
            runCatching {
                web3j.netVersion().send().netVersion.toLong()
            }.onSuccess { chainId = it }.onFailure { Timber.w(it) }
        }
    }

    /**
     * 从缓存或链上获取指定 ENS 名称的解析器地址。
     */
    @Throws(Exception::class)
    protected fun obtainOffChainResolverAddress(ensName: String): ContractAddress {
        val resolverAddress = cachedResolver[ensName] ?: getResolverAddress(ensName)
        if (!resolverAddress.isNullOrEmpty()) {
            cachedResolver[ensName] = resolverAddress
        }
        return ContractAddress(chainId, resolverAddress)
    }

    /**
     * 取消当前正在执行的解析请求。
     */
    fun cancelCurrentResolve() {
        currentResolveRequestId?.let { cancelledRequests.add(it) }
    }

    /**
     * 判断给定请求是否已被取消。
     */
    private fun isRequestCancelled(requestId: String): Boolean = cancelledRequests.contains(requestId)

    /**
     * 从取消队列中移除指定请求 ID。
     */
    private fun removeCancelledRequest(requestId: String) {
        cancelledRequests.remove(requestId)
    }

    /**
     * 根据 ENS 名称执行解析。
     */
    @Throws(Exception::class)
    override fun resolve(ensName: String?): String? {
        val requestId = UUID.randomUUID().toString()
        currentResolveRequestId = requestId

        return try {
            val result = resolveInternal(ensName, requestId)
            if (isRequestCancelled(requestId)) {
                removeCancelledRequest(requestId)
                CANCELLED_REQUEST
            } else {
                result
            }
        } finally {
            if (requestId == currentResolveRequestId) {
                currentResolveRequestId = null
            }
        }
    }

    /**
     * 生成缓存键，避免重复链上查询。
     */
    private fun cacheKey(ensName: String?, addrFunction: String?): String =
        "${ensName.orEmpty()}%${addrFunction.orEmpty()}"

    /**
     * 根据缓存策略调用解析器合约，返回十六进制字符串。
     */
    @Throws(Exception::class)
    private fun resolveWithCaching(ensName: String, nameHash: ByteArray, resolverAddr: String): String? {
        val dnsEncoded = NameHash.dnsEncode(ensName)
        val addrFunction = encodeResolverAddr(nameHash)

        val lookupData = cachedNameReads[cacheKey(ensName, addrFunction)]
        var lookupDataHex = lookupData?.cachedResult

        if (lookupData == null || !lookupData.isValid()) {
            val result = resolve(
                Numeric.hexStringToByteArray(dnsEncoded),
                Numeric.hexStringToByteArray(addrFunction),
                resolverAddr,
            )
            lookupDataHex =
                if (result.isReverted) {
                    Utils.removeDoubleQuotes(result.error.data)
                } else {
                    result.value
                }

            if (!lookupDataHex.isNullOrEmpty() && lookupDataHex != "0x") {
                cachedNameReads[cacheKey(ensName, addrFunction)] = CachedEnsRead(lookupDataHex)
            }
        }

        return lookupDataHex
    }

    /**
     * 处理 ENS 名称解析主流程。
     */

    private fun resolveInternal(ensName: String?, requestId: String): String? {
        if (ensName.isNullOrBlank() || (ensName.trim().length == 1 && ensName.contains("."))) {
            return null
        }

        try {
            return if (isValidEnsName(ensName, addressLength)) {
                val resolverAddress = obtainOffChainResolverAddress(ensName)
                val supportWildcard = getSupportsWildcard(resolverAddress.address)
                val nameHash = NameHash.nameHashAsBytes(ensName)

                val resolvedName =
                    if (supportWildcard) {
                        val lookupDataHex = resolveWithCaching(ensName, nameHash, resolverAddress.address)
                        resolveOffchain(lookupDataHex, resolverAddress, LOOKUP_LIMIT, requestId)
                    } else {
                        try {
                            resolverAddr(nameHash, resolverAddress.address)
                        } catch (e: Exception) {
                            throw RuntimeException("Unable to execute Ethereum request: ", e)
                        }
                    }

                if (resolvedName.isNullOrEmpty() || !WalletUtils.isValidAddress(resolvedName)) {
                    throw EnsResolutionException("Unable to resolve address for name: $ensName")
                }
                resolvedName
            } else {
                ensName
            }
        } catch (e: Exception) {
            throw EnsResolutionException(e)
        }
    }

    /**
     * 查询解析器是否支持 ENS 通配符解析。
     */
    @Throws(Exception::class)
    private fun getSupportsWildcard(address: String): Boolean {
        cachedSupportsWildcard[address]?.let { return it }
        val supportWildcard = supportsInterface(EnsUtils.ENSIP_10_INTERFACE_ID, address)
        cachedSupportsWildcard[address] = supportWildcard
        return supportWildcard
    }

    /**
     * 处理 CCIP Read 链外查询流程，必要时可递归继续调用。
     */
    @Throws(Exception::class)
    protected fun resolveOffchain(
        lookupData: String?,
        resolverAddress: ContractAddress,
        lookupCounter: Int,
        requestId: String,
    ): String? {
        if (isRequestCancelled(requestId)) {
            return null
        }

        if (lookupData != null && EnsUtils.isEIP3668(lookupData)) {
            val offchainLookup = OffchainLookup.build(Numeric.hexStringToByteArray(lookupData.substring(10)))
            if (resolverAddress.address != offchainLookup.sender) {
                throw EnsResolutionException("Cannot handle OffchainLookup raised inside nested call")
            }

            val gatewayResult =
                ccipReadFetch(
                    offchainLookup.urls,
                    offchainLookup.sender,
                    Numeric.toHexString(offchainLookup.callData),
                )
            if (gatewayResult == null) {
                throw EnsResolutionException("CCIP Read disabled or provided no URLs.")
            }

            val objectMapper = ObjectMapperFactory.getObjectMapper()
            val gatewayResponseDTO = objectMapper.readValue(gatewayResult, EnsGatewayResponseDTO::class.java)

            val result = resolveWithProof(
                Numeric.hexStringToByteArray(gatewayResponseDTO.data),
                offchainLookup.extraData,
                resolverAddress.address,
            ) ?: return null

            val resolvedNameHex =
                if (result.isReverted) {
                    Utils.removeDoubleQuotes(result.error.data)
                } else {
                    result.value
                }

            return if (resolvedNameHex != null && EnsUtils.isEIP3668(resolvedNameHex)) {
                val remaining = lookupCounter - 1
                if (remaining <= 0) {
                    throw EnsResolutionException("Lookup calls is out of limit.")
                }
                resolveOffchain(lookupData, resolverAddress, remaining, requestId)
            } else {
                val resolvedNameBytes = resolvedNameHex?.let { decodeDynamicBytes(it) } ?: return null
                decodeAddress(Numeric.toHexString(resolvedNameBytes))
            }
        }
        return lookupData
    }

    /**
     * 通过 CCIP Read 网关取回链外数据。
     */
    protected fun ccipReadFetch(urls: List<String>, sender: String, data: String): String? {
        val errorMessages = mutableListOf<String>()

        for (url in urls) {
            if (currentResolveRequestId?.let { isRequestCancelled(it) } == true) {
                return null
            }

            val request =
                try {
                    buildRequest(url, sender, data)
                } catch (e: JsonProcessingException) {
                    log.error(e.message, e)
                    break
                } catch (e: EnsResolutionException) {
                    log.error(e.message, e)
                    break
                }

            try {
                val response = client.newCall(request).execute()
                try {
                    if (currentResolveRequestId?.let { isRequestCancelled(it) } == true) {
                        return null
                    }

                    if (response.isSuccessful) {
                        val body = response.body
                        if (body == null) {
                            log.warn("Response body is null, url: {}", url)
                            break
                        }

                        try {
                            BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                                val sb = StringBuilder()
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    if (currentResolveRequestId?.let { isRequestCancelled(it) } == true) {
                                        return null
                                    }
                                    sb.append(line).append("\n")
                                }
                                return sb.toString()
                            }
                        } catch (e: Exception) {
                            return ""
                        }
                    } else {
                        val statusCode = response.code
                        if (statusCode in 400..499) {
                            log.error("Response error during CCIP fetch: url {}, error: {}", url, response.message)
                            throw EnsResolutionException(response.message)
                        }
                        errorMessages.add(response.message)
                        log.warn("Response error 500 during CCIP fetch: url {}, error: {}", url, response.message)
                    }
                } finally {
                    response.close()
                }
            } catch (e: IOException) {
                log.error(e.message, e)
            }
        }

        log.warn(errorMessages.toTypedArray().contentToString())
        return null
    }

    /**
     * 构造 CCIP Read 请求。
     */
    @Throws(JsonProcessingException::class)
    protected fun buildRequest(url: String, sender: String?, data: String?): Request {
        if (sender.isNullOrEmpty() || !WalletUtils.isValidAddress(sender)) {
            throw EnsResolutionException("Sender address is null or not valid")
        }
        if (data == null) {
            throw EnsResolutionException("Data is null")
        }
        if (!url.contains("{sender}")) {
            throw EnsResolutionException("Url is not valid, sender parameter is not exist")
        }

        val href = url.replace("{sender}", sender).replace("{data}", data)
        val builder = Request.Builder().url(href)

        return if (url.contains("{data}")) {
            builder.get().build()
        } else {
            val requestDTO = EnsGatewayRequestDTO(data)
            val mapper = ObjectMapperFactory.getObjectMapper()
            builder.post(RequestBody.create(JSON, mapper.writeValueAsString(requestDTO)))
                .addHeader("Content-Type", "application/json")
                .build()
        }
    }

    /**
     * 根据地址执行 ENS 反向解析。
     */
    @Throws(Exception::class)
    fun reverseResolve(address: String): String {
        if (!WalletUtils.isValidAddress(address, addressLength)) {
            throw EnsResolutionException("Address is invalid: $address")
        }

        val reverseName = Numeric.cleanHexPrefix(address) + REVERSE_NAME_SUFFIX
        val resolverAddress = obtainOffChainResolverAddress(reverseName)
        val nameHash = NameHash.nameHashAsBytes(reverseName)

        val name =
            try {
                resolveName(nameHash, resolverAddress.address)
            } catch (e: Exception) {
                throw RuntimeException("Unable to execute Ethereum request", e)
            }

        if (!isValidEnsName(name, addressLength)) {
            throw RuntimeException("Unable to resolve name for address: $address")
        }
        return name
    }

    /**
     * 构造 resolver() 调用。
     */
    private fun getResolver(nameHash: ByteArray): Function =
        Function(
            "resolver",
            listOf(org.web3j.abi.datatypes.generated.Bytes32(nameHash)),
            listOf(object : TypeReference<Address>() {}),
        )

    /**
     * 获取指定 ENS 名称的解析器地址。
     */
    @Throws(Exception::class)
    fun getResolverAddress(ensName: String): String {
        val registryContract = Contracts.resolveRegistryContract(chainId)
        val nameHash = NameHash.nameHashAsBytes(ensName)
        val resolver = getResolver(nameHash)
        var address = getContractData(registryContract, resolver, "") ?: ""
        if (EnsUtils.isAddressEmpty(address)) {
            val parent = EnsUtils.getParent(ensName)
            address = parent?.let { getResolverAddress(it) } ?: address
        }
        return address
    }

    /**
     * 校验给定字符串是否为有效 ENS 名称。
     */
    fun validate(input: String?): Boolean = isValidEnsName(input, addressLength)

    /**
     * 覆盖网络客户端，便于测试。
     */
    fun setHttpClient(client: OkHttpClient) {
        this.client = client
    }

    /**
     * 查询合约是否支持指定接口。
     */
    @Throws(Exception::class)
    fun supportsInterface(interfaceID: ByteArray, address: String): Boolean {
        val function =
            Function(
                FUNC_SUPPORTS_INTERFACE,
                listOf(org.web3j.abi.datatypes.generated.Bytes4(interfaceID)),
                listOf(object : TypeReference<Address>() {}),
            )
        return getContractData(address, function, true) ?: false
    }

    /**
     * 获取地址解析结果，使用缓存减少重复调用。
     */
    @Throws(Exception::class)
    fun resolverAddr(node: ByteArray, address: String): String? {
        val nodeData = Numeric.toHexString(node)
        val cached = cachedNameReads[cacheKey(nodeData, address)]
        var resolverAddr = cached?.cachedResult

        if (cached == null || !cached.isValid()) {
            val function =
                Function(
                    FUNC_ADDR,
                    listOf(org.web3j.abi.datatypes.generated.Bytes32(node)),
                    listOf(object : TypeReference<Address>() {}),
                )
            resolverAddr = getContractData(address, function, "")
            if (!resolverAddr.isNullOrEmpty() && resolverAddr.length > 2) {
                cachedNameReads[cacheKey(nodeData, address)] = CachedEnsRead(resolverAddr)
            }
        }
        return resolverAddr
    }

    /**
     * 编码 resolver.addr 调用数据。
     */
    fun encodeResolverAddr(node: ByteArray): String {
        val function =
            Function(
                FUNC_ADDR,
                listOf(org.web3j.abi.datatypes.generated.Bytes32(node)),
                listOf(object : TypeReference<Address>() {}),
            )
        return FunctionEncoder.encode(function)
    }

    /**
     * 调用 resolver.resolve 并返回链上执行结果。
     */
    @Throws(Exception::class)
    fun resolve(name: ByteArray, data: ByteArray, address: String): EthCall {
        val function =
            Function(
                FUNC_RESOLVE,
                listOf(
                    org.web3j.abi.datatypes.DynamicBytes(name),
                    org.web3j.abi.datatypes.DynamicBytes(data),
                ),
                listOf(object : TypeReference<Address>() {}),
            )
        val encodedFunction = FunctionEncoder.encode(function)
        val transaction =
            org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                TokenscriptFunction.ZERO_ADDRESS,
                address,
                encodedFunction,
            )
        return web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send()
    }

    /**
     * 调用 resolver.resolveWithProof。
     */
    @Throws(Exception::class)
    fun resolveWithProof(response: ByteArray, extraData: ByteArray, address: String): EthCall? {
        if (currentResolveRequestId?.let { isRequestCancelled(it) } == true) {
            return null
        }
        val function =
            Function(
                FUNC_RESOLVE_WITH_PROOF,
                listOf(
                    org.web3j.abi.datatypes.DynamicBytes(response),
                    org.web3j.abi.datatypes.DynamicBytes(extraData),
                ),
                listOf(object : TypeReference<Address>() {}),
            )
        val encodedFunction = FunctionEncoder.encode(function)
        val transaction =
            org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                TokenscriptFunction.ZERO_ADDRESS,
                address,
                encodedFunction,
            )
        return web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send()
    }

    /**
     * 调用 resolver.name 并返回字符串结果。
     */
    @Throws(Exception::class)
    private fun resolveName(node: ByteArray, address: String): String {
        val function =
            Function(
                FUNC_NAME,
                listOf(org.web3j.abi.datatypes.generated.Bytes32(node)),
                listOf(object : TypeReference<Address>() {}),
            )
        return getContractData(address, function, "") ?: ""
    }

    /**
     * 从合约调用结果中解析返回值。
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(Exception::class)
    fun <T> getContractData(address: String, function: Function, type: T): T? {
        val responseValue = callSmartContractFunction(function, address)
        if (responseValue.isNullOrEmpty()) {
            throw Exception("Bad contract value")
        } else if (responseValue == "0x") {
            return if (type is Boolean) {
                false as T
            } else {
                null
            }
        }

        val response = FunctionReturnDecoder.decode(responseValue, function.outputParameters)
        if (response.size == 1) {
            return response[0].value as T
        }
        return if (type is Boolean) {
            false as T
        } else {
            null
        }
    }

    /**
     * 低层合约调用封装，负责捕获网络异常。
     */
    @Throws(Exception::class)
    private fun callSmartContractFunction(function: Function, contractAddress: String): String {
        return try {
            if (currentResolveRequestId?.let { isRequestCancelled(it) } == true) {
                return "0x"
            }

            val encodedFunction = FunctionEncoder.encode(function)
            val transaction =
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    TokenscriptFunction.ZERO_ADDRESS,
                    contractAddress,
                    encodedFunction,
                )
            val response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send()

            if (currentResolveRequestId?.let { isRequestCancelled(it) } == true) {
                return "0x"
            }

            response.value
        } catch (e: InterruptedIOException) {
            "0x"
        } catch (e: UnknownHostException) {
            "0x"
        } catch (e: JsonParseException) {
            "0x"
        } catch (e: Exception) {
            "0x"
        }
    }

    /**
     * ENS 读取结果缓存模型。
     */
    private data class CachedEnsRead(val cachedResult: String) {
        private val cachedResultTime: Long = System.currentTimeMillis()

        fun isValid(): Boolean =
            System.currentTimeMillis() < cachedResultTime + ENS_CACHE_TIME_VALIDITY
    }
}
