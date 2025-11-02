package com.alphawallet.app.entity.tokenscript

import android.os.Build
import android.text.TextUtils
import com.alphawallet.app.entity.UpdateType
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.app.util.BalanceUtils
import com.alphawallet.app.util.Utils
import com.alphawallet.token.entity.As
import com.alphawallet.token.entity.Attribute
import com.alphawallet.token.entity.AttributeInterface
import com.alphawallet.token.entity.ContractAddress
import com.alphawallet.token.entity.EventDefinition
import com.alphawallet.token.entity.FunctionDefinition
import com.alphawallet.token.entity.TokenScriptResult
import com.alphawallet.token.entity.TokenscriptElement
import com.alphawallet.token.entity.TransactionResult
import com.alphawallet.token.entity.ViewType
import com.alphawallet.token.tools.TokenDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Int
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Uint
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Bytes1
import org.web3j.abi.datatypes.generated.Bytes10
import org.web3j.abi.datatypes.generated.Bytes11
import org.web3j.abi.datatypes.generated.Bytes12
import org.web3j.abi.datatypes.generated.Bytes13
import org.web3j.abi.datatypes.generated.Bytes14
import org.web3j.abi.datatypes.generated.Bytes15
import org.web3j.abi.datatypes.generated.Bytes16
import org.web3j.abi.datatypes.generated.Bytes17
import org.web3j.abi.datatypes.generated.Bytes18
import org.web3j.abi.datatypes.generated.Bytes19
import org.web3j.abi.datatypes.generated.Bytes2
import org.web3j.abi.datatypes.generated.Bytes20
import org.web3j.abi.datatypes.generated.Bytes21
import org.web3j.abi.datatypes.generated.Bytes22
import org.web3j.abi.datatypes.generated.Bytes23
import org.web3j.abi.datatypes.generated.Bytes24
import org.web3j.abi.datatypes.generated.Bytes25
import org.web3j.abi.datatypes.generated.Bytes26
import org.web3j.abi.datatypes.generated.Bytes27
import org.web3j.abi.datatypes.generated.Bytes28
import org.web3j.abi.datatypes.generated.Bytes29
import org.web3j.abi.datatypes.generated.Bytes3
import org.web3j.abi.datatypes.generated.Bytes30
import org.web3j.abi.datatypes.generated.Bytes31
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Bytes4
import org.web3j.abi.datatypes.generated.Bytes5
import org.web3j.abi.datatypes.generated.Bytes6
import org.web3j.abi.datatypes.generated.Bytes7
import org.web3j.abi.datatypes.generated.Bytes8
import org.web3j.abi.datatypes.generated.Bytes9
import org.web3j.abi.datatypes.generated.Int104
import org.web3j.abi.datatypes.generated.Int112
import org.web3j.abi.datatypes.generated.Int120
import org.web3j.abi.datatypes.generated.Int128
import org.web3j.abi.datatypes.generated.Int136
import org.web3j.abi.datatypes.generated.Int144
import org.web3j.abi.datatypes.generated.Int152
import org.web3j.abi.datatypes.generated.Int16
import org.web3j.abi.datatypes.generated.Int160
import org.web3j.abi.datatypes.generated.Int168
import org.web3j.abi.datatypes.generated.Int176
import org.web3j.abi.datatypes.generated.Int184
import org.web3j.abi.datatypes.generated.Int192
import org.web3j.abi.datatypes.generated.Int200
import org.web3j.abi.datatypes.generated.Int208
import org.web3j.abi.datatypes.generated.Int216
import org.web3j.abi.datatypes.generated.Int224
import org.web3j.abi.datatypes.generated.Int232
import org.web3j.abi.datatypes.generated.Int24
import org.web3j.abi.datatypes.generated.Int240
import org.web3j.abi.datatypes.generated.Int248
import org.web3j.abi.datatypes.generated.Int256
import org.web3j.abi.datatypes.generated.Int32
import org.web3j.abi.datatypes.generated.Int40
import org.web3j.abi.datatypes.generated.Int48
import org.web3j.abi.datatypes.generated.Int56
import org.web3j.abi.datatypes.generated.Int64
import org.web3j.abi.datatypes.generated.Int72
import org.web3j.abi.datatypes.generated.Int8
import org.web3j.abi.datatypes.generated.Int80
import org.web3j.abi.datatypes.generated.Int88
import org.web3j.abi.datatypes.generated.Int96
import org.web3j.abi.datatypes.generated.Uint104
import org.web3j.abi.datatypes.generated.Uint112
import org.web3j.abi.datatypes.generated.Uint120
import org.web3j.abi.datatypes.generated.Uint128
import org.web3j.abi.datatypes.generated.Uint136
import org.web3j.abi.datatypes.generated.Uint144
import org.web3j.abi.datatypes.generated.Uint152
import org.web3j.abi.datatypes.generated.Uint16
import org.web3j.abi.datatypes.generated.Uint160
import org.web3j.abi.datatypes.generated.Uint168
import org.web3j.abi.datatypes.generated.Uint176
import org.web3j.abi.datatypes.generated.Uint184
import org.web3j.abi.datatypes.generated.Uint192
import org.web3j.abi.datatypes.generated.Uint200
import org.web3j.abi.datatypes.generated.Uint208
import org.web3j.abi.datatypes.generated.Uint216
import org.web3j.abi.datatypes.generated.Uint224
import org.web3j.abi.datatypes.generated.Uint232
import org.web3j.abi.datatypes.generated.Uint24
import org.web3j.abi.datatypes.generated.Uint240
import org.web3j.abi.datatypes.generated.Uint248
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint32
import org.web3j.abi.datatypes.generated.Uint40
import org.web3j.abi.datatypes.generated.Uint48
import org.web3j.abi.datatypes.generated.Uint56
import org.web3j.abi.datatypes.generated.Uint64
import org.web3j.abi.datatypes.generated.Uint72
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.abi.datatypes.generated.Uint80
import org.web3j.abi.datatypes.generated.Uint88
import org.web3j.abi.datatypes.generated.Uint96
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.Log
import org.web3j.utils.Numeric
import timber.log.Timber
import java.io.IOException
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

/**
 * TokenscriptFunction - TokenScript 函数处理类
 *
 * 这是 AlphaWallet 中处理 TokenScript 函数的核心类，负责：
 * 1. 生成智能合约交易函数
 * 2. 调用以太坊智能合约
 * 3. 解析交易结果
 * 4. 处理 TokenScript 属性
 * 5. 管理缓存和数据库操作
 *
 * 协程优化：
 * - 使用协程替代 RxJava 进行异步操作
 * - 提供更好的错误处理和资源管理
 * - 支持并发操作和性能优化
 *
 * @author AlphaWallet Team
 * @since 2024
 */
abstract class TokenscriptFunction {
    companion object {
        /**
         * TokenScript 转换错误标识
         */
        const val TOKENSCRIPT_CONVERSION_ERROR = "<error>"

        /**
         * 零地址常量
         */
        const val ZERO_ADDRESS = "0x0000000000000000000000000000000000000000"
    }

    /**
     * 本地属性映射表
     * 用于存储临时计算的属性值
     */
    private val localAttrs = ConcurrentHashMap<String, Attribute>()

    /**
     * 引用标签映射表
     * 用于存储本地引用和标签
     */
    private val refTags = ConcurrentHashMap<String, String>()

    /**
     * 生成交易函数
     *
     * 根据 Token、TokenId、定义和函数定义生成智能合约调用函数
     *
     * @param token Token 对象
     * @param tokenId Token ID
     * @param definition Token 定义
     * @param function 函数定义
     * @param attrIf 属性接口
     * @return 生成的函数对象
     */
    fun generateTransactionFunction(
        token: Token?,
        tokenId: BigInteger,
        definition: TokenDefinition?,
        function: FunctionDefinition?,
        attrIf: AttributeInterface,
    ): Function {
        requireNotNull(function) { "Function definition cannot be null" }

        var valueNotFound = false

        // 预解析 tokenId
        var processedTokenId = tokenId
        if (tokenId != null) {
            if (tokenId.bitCount() > 256) {
                processedTokenId = tokenId.or(BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE))
            }
        }

        val params = mutableListOf<Type<*>>()
        val returnTypes = mutableListOf<TypeReference<*>>()

        for (arg in function.parameters) {
            val value = resolveReference(token, arg.element, processedTokenId, definition, attrIf)
            var argValueBytes = byteArrayOf(0)
            var argValueBI = BigInteger.ZERO

            if (valueNotFound) {
                params.clear()
                continue
            }

            if (value != null && arg.parameterType != "string") {
                argValueBytes = convertArgToBytes(value)
                argValueBI = BigInteger(1, argValueBytes)
            }

            try {
                when (arg.parameterType) {
                    "int" -> params.add(Int(argValueBI))
                    "int8" -> params.add(Int8(argValueBI))
                    "int16" -> params.add(Int16(argValueBI))
                    "int24" -> params.add(Int24(argValueBI))
                    "int32" -> params.add(Int32(argValueBI))
                    "int40" -> params.add(Int40(argValueBI))
                    "int48" -> params.add(Int48(argValueBI))
                    "int56" -> params.add(Int56(argValueBI))
                    "int64" -> params.add(Int64(argValueBI))
                    "int72" -> params.add(Int72(argValueBI))
                    "int80" -> params.add(Int80(argValueBI))
                    "int88" -> params.add(Int88(argValueBI))
                    "int96" -> params.add(Int96(argValueBI))
                    "int104" -> params.add(Int104(argValueBI))
                    "int112" -> params.add(Int112(argValueBI))
                    "int120" -> params.add(Int120(argValueBI))
                    "int128" -> params.add(Int128(argValueBI))
                    "int136" -> params.add(Int136(argValueBI))
                    "int144" -> params.add(Int144(argValueBI))
                    "int152" -> params.add(Int152(argValueBI))
                    "int160" -> params.add(Int160(argValueBI))
                    "int168" -> params.add(Int168(argValueBI))
                    "int176" -> params.add(Int176(argValueBI))
                    "int184" -> params.add(Int184(argValueBI))
                    "int192" -> params.add(Int192(argValueBI))
                    "int200" -> params.add(Int200(argValueBI))
                    "int208" -> params.add(Int208(argValueBI))
                    "int216" -> params.add(Int216(argValueBI))
                    "int224" -> params.add(Int224(argValueBI))
                    "int232" -> params.add(Int232(argValueBI))
                    "int240" -> params.add(Int240(argValueBI))
                    "int248" -> params.add(Int248(argValueBI))
                    "int256" -> params.add(Int256(argValueBI))
                    "uint" -> params.add(Uint(argValueBI))
                    "uint8" -> params.add(Uint8(argValueBI))
                    "uint16" -> params.add(Uint16(argValueBI))
                    "uint24" -> params.add(Uint24(argValueBI))
                    "uint32" -> params.add(Uint32(argValueBI))
                    "uint40" -> params.add(Uint40(argValueBI))
                    "uint48" -> params.add(Uint48(argValueBI))
                    "uint56" -> params.add(Uint56(argValueBI))
                    "uint64" -> params.add(Uint64(argValueBI))
                    "uint72" -> params.add(Uint72(argValueBI))
                    "uint80" -> params.add(Uint80(argValueBI))
                    "uint88" -> params.add(Uint88(argValueBI))
                    "uint96" -> params.add(Uint96(argValueBI))
                    "uint104" -> params.add(Uint104(argValueBI))
                    "uint112" -> params.add(Uint112(argValueBI))
                    "uint120" -> params.add(Uint120(argValueBI))
                    "uint128" -> params.add(Uint128(argValueBI))
                    "uint136" -> params.add(Uint136(argValueBI))
                    "uint144" -> params.add(Uint144(argValueBI))
                    "uint152" -> params.add(Uint152(argValueBI))
                    "uint160" -> params.add(Uint160(argValueBI))
                    "uint168" -> params.add(Uint168(argValueBI))
                    "uint176" -> params.add(Uint176(argValueBI))
                    "uint184" -> params.add(Uint184(argValueBI))
                    "uint192" -> params.add(Uint192(argValueBI))
                    "uint200" -> params.add(Uint200(argValueBI))
                    "uint208" -> params.add(Uint208(argValueBI))
                    "uint216" -> params.add(Uint216(argValueBI))
                    "uint224" -> params.add(Uint224(argValueBI))
                    "uint232" -> params.add(Uint232(argValueBI))
                    "uint240" -> params.add(Uint240(argValueBI))
                    "uint248" -> params.add(Uint248(argValueBI))
                    "uint256" -> {
                        when (arg.element.ref) {
                            "tokenId" -> params.add(Uint256(processedTokenId))
                            else -> params.add(Uint256(argValueBI))
                        }
                    }
                    "address" -> {
                        when (arg.element.ref) {
                            "ownerAddress" -> params.add(Address(token?.getWallet()))
                            else -> params.add(Address(Numeric.toHexString(argValueBytes)))
                        }
                    }
                    "string" -> {
                        if (value == null) throw Exception("Attempt to use null value")
                        params.add(Utf8String(value))
                    }
                    "bytes" -> {
                        if (value == null) throw Exception("Attempt to use null value")
                        params.add(DynamicBytes(Numeric.hexStringToByteArray(value)))
                    }
                    "struct" -> {
                        var intrinsicType = token?.getIntrinsicType(arg.element.ref)
                        if (intrinsicType == null) {
                            intrinsicType = token?.getIntrinsicType(arg.element.localRef)
                        }
                        if (intrinsicType != null) {
                            params.add(intrinsicType)
                        }
                    }
                    "bytes1" -> params.add(Bytes1(argValueBytes))
                    "bytes2" -> params.add(Bytes2(argValueBytes))
                    "bytes3" -> params.add(Bytes3(argValueBytes))
                    "bytes4" -> params.add(Bytes4(argValueBytes))
                    "bytes5" -> params.add(Bytes5(argValueBytes))
                    "bytes6" -> params.add(Bytes6(argValueBytes))
                    "bytes7" -> params.add(Bytes7(argValueBytes))
                    "bytes8" -> params.add(Bytes8(argValueBytes))
                    "bytes9" -> params.add(Bytes9(argValueBytes))
                    "bytes10" -> params.add(Bytes10(argValueBytes))
                    "bytes11" -> params.add(Bytes11(argValueBytes))
                    "bytes12" -> params.add(Bytes12(argValueBytes))
                    "bytes13" -> params.add(Bytes13(argValueBytes))
                    "bytes14" -> params.add(Bytes14(argValueBytes))
                    "bytes15" -> params.add(Bytes15(argValueBytes))
                    "bytes16" -> params.add(Bytes16(argValueBytes))
                    "bytes17" -> params.add(Bytes17(argValueBytes))
                    "bytes18" -> params.add(Bytes18(argValueBytes))
                    "bytes19" -> params.add(Bytes19(argValueBytes))
                    "bytes20" -> params.add(Bytes20(argValueBytes))
                    "bytes21" -> params.add(Bytes21(argValueBytes))
                    "bytes22" -> params.add(Bytes22(argValueBytes))
                    "bytes23" -> params.add(Bytes23(argValueBytes))
                    "bytes24" -> params.add(Bytes24(argValueBytes))
                    "bytes25" -> params.add(Bytes25(argValueBytes))
                    "bytes26" -> params.add(Bytes26(argValueBytes))
                    "bytes27" -> params.add(Bytes27(argValueBytes))
                    "bytes28" -> params.add(Bytes28(argValueBytes))
                    "bytes29" -> params.add(Bytes29(argValueBytes))
                    "bytes30" -> params.add(Bytes30(argValueBytes))
                    "bytes31" -> params.add(Bytes31(argValueBytes))
                    "bytes32" -> {
                        when (arg.element.ref) {
                            "tokenId" -> params.add(Bytes32(Numeric.toBytesPadded(processedTokenId, 32)))
                            "value" -> params.add(Bytes32(argValueBytes))
                            else -> params.add(Bytes32(Numeric.toBytesPadded(argValueBI, 32)))
                        }
                    }
                    else -> {
                        Timber.d("NOT IMPLEMENTED: %s", arg.parameterType)
                    }
                }
            } catch (e: Exception) {
                // 尝试使用未成形的值
                valueNotFound = true
            }
        }

        // 根据函数类型设置返回类型
        when (function.asDefin) {
            As.UTF8 -> returnTypes.add(object : TypeReference<Utf8String>() {})
            As.Signed, As.Unsigned, As.UnsignedInput, As.TokenId -> returnTypes.add(object : TypeReference<Uint256>() {})
            As.Address -> returnTypes.add(object : TypeReference<Address>() {})
            else -> returnTypes.add(object : TypeReference<Bytes32>() {})
        }

        if (valueNotFound) {
            params.clear()
        }

        return Function(function.method, params, returnTypes)
    }

    /**
     * 从以太坊获取结果
     *
     * 异步调用智能合约并获取交易结果
     *
     * @param token Token 对象
     * @param contractAddress 合约地址
     * @param attr 属性对象
     * @param tokenId Token ID
     * @param definition Token 定义
     * @param attrIf 属性接口
     * @return 交易结果
     */
    suspend fun fetchResultFromEthereum(
        token: Token,
        contractAddress: ContractAddress,
        attr: Attribute,
        tokenId: BigInteger,
        definition: TokenDefinition?,
        attrIf: AttributeInterface,
    ): TransactionResult =
        withContext(Dispatchers.IO) {
            val transactionResult =
                TransactionResult(contractAddress.chainId, contractAddress.address, tokenId, attr,)

            val transaction = generateTransactionFunction(token, tokenId, definition, attr.function, attrIf)

            val result =
                if (transaction.inputParameters == null) {
                    // 无法验证所有输入参数值
                    ""
                } else {
                    // 推送交易
                    callSmartContractFunction(
                        TokenRepository.getWeb3jService(contractAddress.chainId),
                        transaction,
                        contractAddress.address,
                        token.getWallet(),
                    )
                }

            transactionResult.result =
                handleTransactionResult(
                    transactionResult,
                    transaction,
                    result,
                    attr,
                    System.currentTimeMillis(),
                )

            transactionResult
        }

    /**
     * 调用智能合约
     *
     * @param chainId 链 ID
     * @param contractAddress 合约地址
     * @param function 函数对象
     * @return 调用结果
     */
    fun callSmartContract(
        chainId: Long,
        contractAddress: String,
        function: Function,
    ): String? =
        callSmartContractFunction(
            TokenRepository.getWeb3jService(chainId),
            function,
            contractAddress,
            ZERO_ADDRESS,
        )

    /**
     * 调用智能合约函数
     *
     * @param web3j Web3j 实例
     * @param function 函数对象
     * @param contractAddress 合约地址
     * @param walletAddr 钱包地址
     * @return 调用结果
     */
    private fun callSmartContractFunction(
        web3j: Web3j,
        function: Function,
        contractAddress: String,
        walletAddr: String,
    ): String? {
        val encodedFunction = FunctionEncoder.encode(function)

        return try {
            val transaction =
                Transaction.createEthCallTransaction(
                    walletAddr,
                    contractAddress,
                    encodedFunction,
                )
            val response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send()
            response.value
        } catch (e: IOException) {
            // 连接错误，使用缓存值
            Timber.e(e, "网络连接错误，使用缓存值")
            null
        } catch (e: Exception) {
            Timber.e(e, "调用智能合约时发生错误")
            null
        }
    }

    /**
     * 解析引用
     *
     * 根据 TokenScript 元素解析引用值
     *
     * @param token Token 对象
     * @param element TokenScript 元素
     * @param tokenId Token ID
     * @param definition Token 定义
     * @param attrIf 属性接口
     * @return 解析后的值
     */
    fun resolveReference(
        token: Token?,
        element: TokenscriptElement,
        tokenId: BigInteger?,
        definition: TokenDefinition?,
        attrIf: AttributeInterface,
    ): String? {
        val attrRes = token?.getAttributeResult(element.ref, tokenId)

        return when {
            !TextUtils.isEmpty(element.value) -> element.value
            attrRes != null -> attrRes.text // 从结果映射解析
            !TextUtils.isEmpty(element.localRef) && refTags.containsKey(element.localRef) ->
                refTags[element.localRef] // 本地输入在属性之前
            !TextUtils.isEmpty(element.ref) && refTags.containsKey(element.ref) ->
                refTags[element.ref]
            definition != null && definition.attributes.containsKey(element.ref) -> {
                // 从属性解析
                val attr = definition.attributes[element.ref]
                fetchArgValue(token, element, attr, tokenId, definition, attrIf)
            }
            localAttrs.containsKey(element.ref) -> {
                // 无法解析，尝试从本地属性解析或标记为 null
                val attr = localAttrs[element.ref]
                fetchArgValue(token, element, attr, tokenId, definition, attrIf)
            }
            localAttrs.containsKey(element.localRef) -> {
                val attr = localAttrs[element.localRef]
                fetchArgValue(token, element, attr, tokenId, definition, attrIf)
            }
            else -> null
        }
    }

    /**
     * 获取参数值
     *
     * @param token Token 对象
     * @param element TokenScript 元素
     * @param attr 属性对象
     * @param tokenId Token ID
     * @param definition Token 定义
     * @param attrIf 属性接口
     * @return 参数值
     */
    private fun fetchArgValue(
        token: Token?,
        element: TokenscriptElement,
        attr: Attribute?,
        tokenId: BigInteger?,
        definition: TokenDefinition?,
        attrIf: AttributeInterface,
    ): String? {
        if (attr?.userInput == true) {
            return if (!TextUtils.isEmpty(element.value)) element.value else null
        }

        // 处理其他类型的参数获取逻辑
        return element.value
    }

    /**
     * 获取属性结果
     *
     * 异步获取 TokenScript 属性结果，支持缓存和网络请求
     *
     * @param token Token 对象
     * @param attr 属性对象
     * @param tokenId Token ID
     * @param td Token 定义
     * @param attrIf 属性接口
     * @param itemView 视图类型
     * @param update 更新类型
     * @return 属性结果
     */
    suspend fun fetchAttrResult(
        token: Token,
        attr: Attribute?,
        tokenId: BigInteger,
        td: TokenDefinition?,
        attrIf: AttributeInterface,
        itemView: ViewType?,
        update: UpdateType?,
    ): TokenScriptResult.Attribute =
        withContext(Dispatchers.IO) {
            if (attr == null) {
                return@withContext TokenScriptResult.Attribute("bd", "bd", BigInteger.ZERO, "")
            }

            val useTokenId = if (attr.usesTokenId()) tokenId else BigInteger.ZERO

            // 检查是否有缓存的属性结果
            token.getAttributeResult(attr.name, useTokenId)?.let { cachedResult ->
                return@withContext cachedResult
            }

            when (val event = attr.event) {
                null -> {
                    when {
                        attr.function == null -> {
                            // 静态属性（例如从 tokenId 映射城市）
                            staticAttribute(attr, useTokenId)
                        }
                        else -> {
                            val useAddress = ContractAddress(attr.function)
                            val lastTxUpdate = attrIf.getLastTokenUpdate(useAddress.chainId, useAddress.address)
                            val cachedResult = attrIf.getFunctionResult(useAddress, attr, useTokenId)

                            val shouldUseCache =
                                checkUpdateRequired(
                                    attrIf,
                                    attr,
                                    cachedResult,
                                    update,
                                    itemView == ViewType.ITEM_VIEW,
                                    lastTxUpdate,
                                    useAddress,
                                )

                            if (shouldUseCache) {
                                // 使用缓存值
                                resultFromDatabase(cachedResult, attr)
                            } else {
                                // 从区块链获取最新结果
                                val walletAddress = attrIf.getWalletAddr()
                                fetchResultFromEthereum(token, useAddress, attr, useTokenId, td, attrIf)
                                    .let { transactionResult ->
                                        addParseResultIfValid(token, useTokenId, attr, transactionResult)
                                    }.let { result ->
                                        restoreFromDBIfRequired(result, cachedResult)
                                    }.let { txResult ->
                                        attrIf.storeAuxData(walletAddress, txResult)
                                    }.let { result ->
                                        parseFunctionResult(result, attr)
                                    }
                            }
                        }
                    }
                }
                else -> {
                    val addresses = event.contract?.addresses.orEmpty()

                    val chain: Long = addresses.keys.firstOrNull() ?: error("Missing chain")
                    val address = addresses.values.firstOrNull()?.firstOrNull() ?: error("Missing address")

                    val useAddress = ContractAddress(chain, address)

                    val cachedResult = attrIf.getFunctionResult(useAddress, attr, useTokenId)

                    if (TextUtils.isEmpty(cachedResult.result)) {
                        // 尝试获取最新事件结果
                        getEventResult(cachedResult, event, attr, useTokenId, attrIf)
                    } else {
                        resultFromDatabase(cachedResult, attr)
                    }
                }
            }
        }

    /**
     * 检查是否需要更新
     *
     * @param attrIf 属性接口
     * @param attr 属性对象
     * @param cachedResult 缓存结果
     * @param update 更新类型
     * @param isItemView 是否为项目视图
     * @param lastTxUpdate 最后交易更新时间
     * @param useAddress 使用的地址
     * @return 是否需要更新
     */
    private fun checkUpdateRequired(
        attrIf: AttributeInterface,
        attr: Attribute,
        cachedResult: TransactionResult,
        update: UpdateType?,
        isItemView: Boolean,
        lastTxUpdate: Long,
        useAddress: ContractAddress,
    ): Boolean =
        when (update) {
            UpdateType.USE_CACHE -> {
                isItemView || cachedResult.resultTime != 0L
            }
            UpdateType.UPDATE_IF_REQUIRED -> {
                isItemView || (
                    !attr.isVolatile() &&
                        (
                            attrIf.resolveOptimisedAttr(useAddress, attr, cachedResult) ||
                                !cachedResult.needsUpdating(lastTxUpdate)
                        )
                )
            }
            UpdateType.ALWAYS_UPDATE -> {
                isItemView
            }

            null -> TODO()
        }

    /**
     * 获取事件结果
     *
     * @param txResult 交易结果
     * @param event 事件定义
     * @param attr 属性对象
     * @param tokenId Token ID
     * @param attrIf 属性接口
     * @return 事件结果
     */

    private suspend fun getEventResult(
        txResult: TransactionResult,
        event: EventDefinition,
        attr: Attribute,
        tokenId: BigInteger?,
        attrIf: AttributeInterface,
    ): TokenScriptResult.Attribute =
        withContext(Dispatchers.IO) {
            val walletAddress = attrIf.getWalletAddr()
            val web3j: Web3j? = event.eventChainId.let { TokenRepository.getWeb3jService(it) }
            if (web3j != null) {
                val tokenIds = tokenId?.let { listOf(it) } ?: emptyList()
                val filter = EventUtils.generateLogFilter(event, tokenIds, attrIf)
                    ?: return@withContext parseFunctionResult(txResult, attr)
                val ethLogs = web3j.ethGetLogs(filter).send()

                // 使用最后接收的日志
                if (ethLogs.logs.isNotEmpty()) {
                    val ethLog = ethLogs.logs.last()
                    val selectVal = EventUtils.getSelectVal(event, ethLog)
                    txResult.result = attr.getSyntaxVal(selectVal)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        txResult.resultTime = (ethLog.get() as Log).blockNumber.longValueExact()
                    }
                    attrIf.storeAuxData(walletAddress, txResult)
                }
            }

            parseFunctionResult(txResult, attr)
        }

    /**
     * 静态属性处理
     *
     * @param attr 属性对象
     * @param tokenId Token ID
     * @return 静态属性结果
     */
    private suspend fun staticAttribute(
        attr: Attribute,
        tokenId: BigInteger?,
    ): TokenScriptResult.Attribute =
        withContext(Dispatchers.IO) {
            // 处理静态属性的逻辑
            TokenScriptResult.Attribute(
                attr.name,
                attr.name,
                tokenId,
                attr.getSyntaxVal(tokenId.toString()).toString(),
            )
        }

    /**
     * 从数据库获取结果
     *
     * @param transactionResult 交易结果
     * @param attr 属性对象
     * @return 数据库结果
     */
    private suspend fun resultFromDatabase(
        transactionResult: TransactionResult,
        attr: Attribute,
    ): TokenScriptResult.Attribute =
        withContext(Dispatchers.IO) {
            parseFunctionResult(transactionResult, attr)
        }

    /**
     * 处理交易结果
     *
     * @param result 交易结果
     * @param function 函数对象
     * @param responseValue 响应值
     * @param attr 属性对象
     * @param lastTransactionTime 最后交易时间
     * @return 处理后的结果
     */
    private fun handleTransactionResult(
        result: TransactionResult,
        function: Function,
        responseValue: String?,
        attr: Attribute,
        lastTransactionTime: Long,
    ): String {
        if (responseValue.isNullOrEmpty()) {
            return ""
        }

        return try {
            val decodedValues = FunctionReturnDecoder.decode(responseValue, function.outputParameters)
            if (decodedValues.isNotEmpty()) {
                val decodedValue = decodedValues[0]
                when (decodedValue) {
                    is Address -> decodedValue.value
                    is Utf8String -> decodedValue.value
                    is Uint -> decodedValue.value.toString()
                    is Int -> decodedValue.value.toString()
                    is DynamicBytes -> Numeric.toHexString(decodedValue.value)
                    else -> decodedValue.value.toString()
                }
            } else {
                ""
            }
        } catch (e: Exception) {
            Timber.e(e, "解析交易结果时发生错误")
            TOKENSCRIPT_CONVERSION_ERROR
        }
    }

    /**
     * 解析函数结果
     *
     * @param transactionResult 交易结果
     * @param attr 属性对象
     * @return 解析后的属性结果
     */
    fun parseFunctionResult(
        transactionResult: TransactionResult,
        attr: Attribute,
    ): TokenScriptResult.Attribute {
        val result = transactionResult.result ?: ""
        val syntaxVal = attr.getSyntaxVal(result)

        return TokenScriptResult.Attribute(
            attr.name,
            attr.name,
            transactionResult.tokenId,
            syntaxVal.toString(),
        )
    }

    /**
     * 转换输入值
     *
     * 根据属性的 As 类型转换输入值
     *
     * @param attr 属性对象
     * @param valueFromInput 输入值
     * @return 转换后的值
     */
    fun convertInputValue(
        attr: Attribute?,
        valueFromInput: String?,
    ): String {
        if (attr == null || valueFromInput.isNullOrEmpty()) {
            return ""
        }

        return try {
            when (attr.getAs()) {
                As.Unsigned, As.Signed, As.UnsignedInput -> {
                    // 转换清理后的用户输入
                    val inputBytes = convertArgToBytes(Utils.isolateNumeric(valueFromInput))
                    val unsignedValue = BigInteger(inputBytes)
                    unsignedValue.toString()
                }
                As.UTF8 -> {
                    valueFromInput
                }
                As.Bytes -> {
                    // 对用户输入应用位掩码并移位，因为字节是反向的
                    val inputBytes = convertArgToBytes(valueFromInput)
                    if (inputBytes.size <= 32) {
                        val value = BigInteger(1, inputBytes).and(attr.bitmask).shiftRight(attr.bitshift)
                        value.toString(16)
                    } else {
                        Numeric.toHexString(inputBytes)
                    }
                }
                As.e18 -> {
                    BalanceUtils.ethToWei(valueFromInput)
                }
                As.e8 -> {
                    BalanceUtils.unitToEMultiplier(valueFromInput, java.math.BigDecimal("100000000"))

                }
                As.e6 -> {
                    BalanceUtils.unitToEMultiplier(valueFromInput, java.math.BigDecimal("1000000"))
                }
                As.e4 -> {
                    BalanceUtils.unitToEMultiplier(valueFromInput, java.math.BigDecimal("10000"))
                }
                As.e3 -> {
                    BalanceUtils.unitToEMultiplier(valueFromInput, java.math.BigDecimal("1000"))
                }
                As.e2 -> {
                    BalanceUtils.unitToEMultiplier(valueFromInput, java.math.BigDecimal("100"))
                }
                As.Mapping -> {
                    // 作为输入没有意义
                    "$TOKENSCRIPT_CONVERSION_ERROR Mapping in user input params: ${attr.name}"
                }
                As.Address -> {
                    valueFromInput
                }
                As.Boolean -> {
                    // 尝试解码
                    when (valueFromInput.lowercase()) {
                        "true", "1" -> "TRUE"
                        else -> "FALSE"
                    }
                }
                As.TokenId -> {
                    // 不应该到这里 - tokenId 应该在此之前处理
                    "$TOKENSCRIPT_CONVERSION_ERROR Token ID in user input params: ${attr.name}"
                }
                As.Unknown -> {
                    valueFromInput
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "转换输入值时发生错误")
            "$TOKENSCRIPT_CONVERSION_ERROR ${e.message}"
        }
    }

    /**
     * 构建属性映射
     *
     * @param attrs 属性列表
     */
    fun buildAttrMap(attrs: List<Attribute>) {
        localAttrs.clear()
        attrs.forEach { attr ->
            attr.name?.let {
                localAttrs[it] = attr
            }
        }
    }

    /**
     * 添加解析结果（如果有效）
     *
     * @param token Token 对象
     * @param tokenId Token ID
     * @param attrResult 属性结果
     * @return 处理后的属性结果
     */
    fun addParseResultIfValid(
        token: Token,
        tokenId: BigInteger,
        attrResult: TokenScriptResult.Attribute,
    ): TokenScriptResult.Attribute {
        token.setAttributeResult(tokenId, attrResult)
        return attrResult
    }

    /**
     * 添加解析结果（如果有效）
     *
     * @param token Token 对象
     * @param tokenId Token ID
     * @param attr 属性对象
     * @param result 交易结果
     * @return 处理后的交易结果
     */
    private fun addParseResultIfValid(
        token: Token,
        tokenId: BigInteger,
        attr: Attribute,
        result: TransactionResult,
    ): TransactionResult {
        token.setAttributeResult(tokenId, parseFunctionResult(result, attr))
        return result
    }

    /**
     * 添加本地引用
     *
     * @param refs 引用映射
     */
    fun addLocalRefs(refs: Map<String, String>) {
        refTags.putAll(refs)
    }

    /**
     * 清除解析映射
     */
    fun clearParseMaps() {
        localAttrs.clear()
        refTags.clear()
    }

    /**
     * 从数据库恢复（如果需要）
     *
     * @param result 当前结果
     * @param cachedResult 缓存结果
     * @return 恢复后的结果
     */
    private fun restoreFromDBIfRequired(
        result: TransactionResult,
        cachedResult: TransactionResult,
    ): TransactionResult =
        if (result.result.isNullOrEmpty() && !cachedResult.result.isNullOrEmpty()) {
            cachedResult
        } else {
            result
        }

    /**
     * 检查字节字符串
     *
     * @param responseValue 响应值
     * @return 检查后的值
     */
    private fun checkBytesString(responseValue: String): String =
        if (responseValue.startsWith("0x")) {
            responseValue
        } else {
            "0x$responseValue"
        }

    /**
     * 转换参数为字节数组
     *
     * @param inputValue 输入值
     * @return 字节数组
     */

    fun convertArgToBytes(inputValue: String): ByteArray =
        try {
            if (inputValue.startsWith("0x")) {
                Numeric.hexStringToByteArray(inputValue)
            } else {
                inputValue.toByteArray()
            }
        } catch (e: Exception) {
            Timber.e(e, "转换参数为字节数组时发生错误")
            ByteArray(0)
        }
}
