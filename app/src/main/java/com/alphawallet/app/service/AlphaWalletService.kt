package com.alphawallet.app.service

import android.util.Base64
import com.alphawallet.app.entity.CryptoFunctions.Companion.sigFromByteArray
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Ticket
import com.alphawallet.app.util.Utils
import com.alphawallet.token.entity.MagicLinkData
import com.alphawallet.token.entity.XMLDsigDescriptor
import com.alphawallet.token.tools.ParseMagicLink
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.web3j.crypto.Keys
import org.web3j.protocol.http.HttpService.JSON_MEDIA_TYPE
import org.web3j.utils.Numeric
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigInteger
import java.nio.charset.StandardCharsets

/**
 * AlphaWalletService - AlphaWallet核心服务类
 *
 * 这个类是AlphaWallet中负责处理核心业务逻辑的服务类，主要功能包括：
 * 1. TokenScript签名验证 - 验证TokenScript的数字签名和安全性
 * 2. Feemaster交易处理 - 处理免费交易服务，降低用户使用门槛
 * 3. 魔法链接处理 - 处理各种类型的魔法链接（Magic Link）
 * 4. 网络请求管理 - 统一的HTTP请求处理
 * 5. 数字签名处理 - 处理各种加密签名操作
 *
 * 该服务使用Kotlin协程实现异步操作，提供高效稳定的网络通信功能。
 *
 * @param httpClient OkHttp客户端，用于网络请求
 * @param gson JSON序列化工具
 *
 * @author AlphaWallet Team
 * @since 2024
 */
class AlphaWalletService(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
) {
    companion object {
        private const val TAG = "AlphaWalletService"
        private const val API = "api/"

        // TokenScript验证端点
        private const val XML_VERIFIER_ENDPOINT = "https://aw.app/api/v1/verifyXMLDSig"
        private const val TSML_VERIFIER_ENDPOINT_STAGING = "https://doobtvjcpb8dc.cloudfront.net/tokenscript/validate"
        private const val TSML_VERIFIER_ENDPOINT = "https://api.smarttokenlabs.com/tokenscript/validate"
        private const val XML_VERIFIER_PASS = "pass"

        // 媒体类型定义
        private val MEDIA_TYPE_TOKENSCRIPT = "text/xml; charset=UTF-8".toMediaTypeOrNull()
        private val MEDIA_TYPE_OCTET_STREAM = "application/octet-stream".toMediaTypeOrNull()
    }

    // 魔法链接解析器
    private var parser: ParseMagicLink? = null

    init {
        Timber.tag(TAG).d("AlphaWalletService initialized")
    }

    /**
     * 状态元素数据类
     * 用于表示TokenScript验证返回的状态信息
     */
    private data class StatusElement(
        val typeName: String = "",
        val status: String = "",
        val statusText: String = "",
        val signingKey: String = "",
    ) {
        /**
         * 转换为XMLDsigDescriptor对象
         *
         * @return XMLDsigDescriptor 数字签名描述符
         */
        fun getXMLDsigDescriptor(): XMLDsigDescriptor =
            XMLDsigDescriptor().apply {
                result = status
                issuer = signingKey
                certificateName = statusText
                keyType = typeName
            }
    }

    /**
     * 处理Feemaster导入操作
     * 根据不同的合约类型执行相应的免费交易处理逻辑
     *
     * @param url Feemaster服务URL
     * @param wallet 用户钱包
     * @param chainId 链ID
     * @param order 魔法链接数据
     * @return HTTP响应状态码
     */
    suspend fun handleFeemasterImport(
        url: String,
        wallet: Wallet,
        chainId: Long,
        order: MagicLinkData,
    ): Int =
        withContext(Dispatchers.IO) {
            try {
                when (order.contractType) {
                    ParseMagicLink.spawnable -> {
                        // 处理可生成代币类型
                        sendFeemasterTransaction(
                            url = url,
                            chainId = chainId,
                            address = wallet.address,
                            expiry = order.expiry,
                            indices = "", // 生成时使用空字符串
                            signature = order.signature,
                            contractAddress = order.contractAddress,
                            tokenIds = order.tokenIds,
                        )
                    }
                    ParseMagicLink.currencyLink -> {
                        // 处理货币链接类型
                        sendFeemasterCurrencyTransaction(url, chainId, wallet.address, order)
                    }
                    else -> {
                        // 处理普通代币类型
                        val ticketStr = generateTicketString(order.indices)
                        sendFeemasterTransaction(
                            url = url,
                            chainId = chainId,
                            address = wallet.address,
                            expiry = order.expiry,
                            indices = ticketStr,
                            signature = order.signature,
                            contractAddress = order.contractAddress,
                            tokenIds = order.tokenIds,
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error handling feemaster import")
                500 // 默认错误状态码
            }
        }

    /**
     * 检查TokenScript签名（通过URI）
     * 验证TokenScript的数字签名是否有效
     *
     * @param scriptUri TokenScript的URI地址
     * @param chainId 链ID
     * @param address 合约地址
     * @return XMLDsigDescriptor 签名验证结果
     */
    suspend fun checkTokenScriptSignature(
        scriptUri: String,
        chainId: Long,
        address: String,
    ): XMLDsigDescriptor =
        withContext(Dispatchers.IO) {
            val dsigDescriptor = XMLDsigDescriptor().apply { result = "fail" }

            try {
                val jsonObject =
                    JSONObject().apply {
                        put("sourceType", "scriptUri")
                        put("sourceId", "$chainId-${Keys.toChecksumAddress(address)}")
                        put("sourceUrl", scriptUri)
                    }

                val body = jsonObject.toString().toRequestBody(JSON_MEDIA_TYPE)

                val response = getTSValidationCheck(body)

                if ((response.code / 100) == 2) {
                    val result = response.body?.string() ?: return@withContext dsigDescriptor
                    val obj = gson.fromJson(result, JsonObject::class.java)

                    if (obj.has("error")) {
                        return@withContext dsigDescriptor
                    }

                    val overview = obj.getAsJsonObject("overview")
                    if (overview != null) {
                        val statuses = overview.getAsJsonArray("originStatuses")
                        if (!statuses.isEmpty) {
                            val status1 = gson.fromJson(statuses.get(0), StatusElement::class.java)
                            return@withContext status1.getXMLDsigDescriptor()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error checking TokenScript signature")
            }

            dsigDescriptor
        }

    /**
     * 检查TokenScript签名（通过输入流）
     * 验证从输入流读取的TokenScript的数字签名
     *
     * @param inputStream TokenScript的输入流
     * @param chainId 链ID
     * @param address 合约地址
     * @param sourceUrl 源URL
     * @return XMLDsigDescriptor 签名验证结果
     */
    suspend fun checkTokenScriptSignature(
        inputStream: InputStream,
        chainId: Long,
        address: String?,
        sourceUrl: String,
    ): XMLDsigDescriptor =
        withContext(Dispatchers.IO) {
            val dsigDescriptor = XMLDsigDescriptor().apply { result = "fail" }

            try {
                val jsonObject =
                    JSONObject().apply {
                        put("sourceType", "scriptUri")
                        put("sourceId", "$chainId-${Keys.toChecksumAddress(address)}")
                        put("sourceUrl", sourceUrl)
                        put("base64Xml", streamToBase64(inputStream))
                    }

//            val body = RequestBody.create(jsonObject.toString(), JSON_MEDIA_TYPE)
                val body = jsonObject.toString().toRequestBody(JSON_MEDIA_TYPE)
                val response = getTSValidationCheck(body)

                if ((response.code / 100) == 2) {
                    val result = response.body?.string() ?: return@withContext dsigDescriptor
                    val obj = gson.fromJson(result, JsonObject::class.java)

                    if (obj.has("error")) {
                        return@withContext dsigDescriptor
                    }

                    val overview = obj.getAsJsonObject("overview")
                    if (overview != null) {
                        val statuses = overview.getAsJsonArray("originStatuses")
                        if (!statuses.isEmpty) {
                            val status1 = gson.fromJson(statuses.get(0), StatusElement::class.java)
                            return@withContext status1.getXMLDsigDescriptor()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error checking TokenScript signature from stream")
            }

            dsigDescriptor
        }

    /**
     * 将输入流转换为Base64编码字符串
     *
     * @param inputStream 输入流
     * @return Base64编码的字符串
     */
    private suspend fun streamToBase64(inputStream: InputStream): String =
        withContext(Dispatchers.IO) {
            val sb = StringBuilder()

            try {
                BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
                    var c: Int
                    while (reader.read().also { c = it } != -1) {
                        sb.append(c.toChar())
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error converting stream to base64")
                throw e
            }

            val base64Encoded =
                Base64.encode(
                    sb.toString().toByteArray(StandardCharsets.UTF_8),
                    Base64.DEFAULT,
                )

            String(base64Encoded)
        }

    /**
     * 获取TokenScript验证检查响应
     * 尝试主端点，如果失败则尝试备用端点
     *
     * @param body 请求体
     * @return HTTP响应
     */
    private suspend fun getTSValidationCheck(body: RequestBody): Response =
        withContext(Dispatchers.IO) {
            // 首先尝试主端点
            var request =
                Request
                    .Builder()
                    .url(TSML_VERIFIER_ENDPOINT)
                    .post(body)
                    .build()

            var response = httpClient.newCall(request).execute()

            // 如果主端点失败，尝试备用端点
            if ((response.code / 100) != 2) {
                Timber.tag(TAG).d("Main endpoint failed, trying staging endpoint")

                request =
                    Request
                        .Builder()
                        .url(TSML_VERIFIER_ENDPOINT_STAGING)
                        .post(body)
                        .build()

                response = httpClient.newCall(request).execute()
            }

            response
        }

    /**
     * 发送Feemaster货币交易
     * 处理免费货币交易的网络请求
     *
     * @param url Feemaster服务URL
     * @param networkId 网络ID
     * @param address 接收地址
     * @param order 魔法链接订单数据
     * @return HTTP响应状态码
     */
    private suspend fun sendFeemasterCurrencyTransaction(
        url: String,
        networkId: Long,
        address: String?,
        order: MagicLinkData,
    ): Int =
        withContext(Dispatchers.IO) {
            var result = 500 // 默认失败状态码

            try {
                if (address != null) {
                    val sb = StringBuilder(url).append("claimFreeCurrency")

                    val args =
                        mutableMapOf<String, String>().apply {
                            put("prefix", Numeric.toHexString(order.prefix))
                            put("recipient", address)
                            put("amount", order.amount.toString(10))
                            put("expiry", order.expiry.toString())
                            put("nonce", order.nonce.toString(10))
                            put("networkId", networkId.toString())
                            put("contractAddress", order.contractAddress)
                        }

                    addSignature(args, order.signature)
                    result = postRequest(sb, args)
                }
            } catch (e: Exception) {
                Timber.e(e)
            }

            result
        }

    /**
     * 生成票据数组
     * 将字符串索引转换为整数数组
     *
     * @param indices 索引字符串
     * @param ticket 票据对象
     * @return 整数数组
     */
    private suspend fun generateTicketArray(
        indices: String,
        ticket: Ticket,
    ): IntArray =
        withContext(Dispatchers.Default) {
            try {
                val ticketIndices = Utils.stringIntsToIntegerList(indices)
                ticketIndices.toIntArray()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error generating ticket array")
                intArrayOf()
            }
        }

    /**
     * 生成票据字符串
     * 将整数数组转换为逗号分隔的字符串
     *
     * @param tickets 票据数组
     * @return 逗号分隔的字符串
     */
    private suspend fun generateTicketString(tickets: IntArray): String =
        withContext(Dispatchers.Default) {
            try {
                tickets.joinToString(",")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error generating ticket string")
                ""
            }
        }

    /**
     * 添加数字签名到参数映射
     * 将签名数据分解为r、s、v三个组件
     *
     * @param args 参数映射
     * @param sig 签名字节数组
     */
    private fun addSignature(
        args: MutableMap<String, String>,
        sig: ByteArray,
    ) {
        try {
            val sigData = sigFromByteArray(sig)
            if (sigData != null) {
                args["r"] = Numeric.toHexString(sigData.r)
                args["s"] = Numeric.toHexString(sigData.s)
                args["v"] = Numeric.toHexString(sigData.v)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error adding signature to args")
        }
    }

    /**
     * 发送Feemaster交易
     * 处理各种类型的免费交易请求
     *
     * @param url 服务URL
     * @param chainId 链ID
     * @param address 目标地址
     * @param expiry 过期时间
     * @param indices 索引字符串
     * @param signature 交易签名
     * @param contractAddress 合约地址
     * @param tokenIds 代币ID列表
     * @return HTTP响应状态码
     */
    private suspend fun sendFeemasterTransaction(
        url: String,
        chainId: Long,
        address: String?,
        expiry: Long,
        indices: String,
        signature: ByteArray,
        contractAddress: String,
        tokenIds: List<BigInteger>,
    ): Int =
        withContext(Dispatchers.IO) {
            var result = 500 // 默认失败状态码

            try {
                val sb = StringBuilder(url)
                val args = mutableMapOf<String, String>()

                // 根据indices判断交易类型
                if (indices.isEmpty()) {
                    // 可生成代币类型
                    sb.append("/claimSpawnableToken/")
                    args["tokenIds"] = parseTokenIds(tokenIds)
                    Timber.tag(TAG).d("Claiming spawnable token")
                } else {
                    // 普通代币类型
                    sb.append("/claimToken/")
                    args["indices"] = indices
                    Timber.tag(TAG).d("Claiming regular token")
                }

                // 添加通用参数
                if (address != null) {
                    args.apply {
                        put("contractAddress", contractAddress)
                        put("address", address)
                        put("expiry", expiry.toString())
                        put("networkId", chainId.toString())
                    }
                }

                addSignature(args, signature)
                result = postRequest(sb, args)

                Timber.tag(TAG).d("Feemaster transaction result: $result")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error sending feemaster transaction")
            }

            result
        }

    /**
     * 解析代币ID列表
     * 将BigInteger列表转换为十六进制字符串
     *
     * @param tokens 代币ID列表
     * @return 逗号分隔的十六进制字符串
     */
    private fun parseTokenIds(tokens: List<BigInteger>): String =
        try {
            tokens.joinToString(",") { token ->
                Numeric.toHexStringNoPrefix(token)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error parsing token IDs")
            ""
        }

    /**
     * 执行POST请求
     * 统一的HTTP POST请求处理方法
     *
     * @param sb URL构建器
     * @param args 请求参数
     * @return HTTP响应状态码
     */
    private suspend fun postRequest(
        sb: StringBuilder,
        args: Map<String, String>,
    ): Int =
        withContext(Dispatchers.IO) {
            try {
                val urlWithParams = sb.append(formPrologData(args)).toString()

                val request =
                    Request
                        .Builder()
                        .url(urlWithParams)
                        .post(RequestBody.create(MEDIA_TYPE_OCTET_STREAM, ""))
                        .build()

                val response = httpClient.newCall(request).execute()
                val statusCode = response.code

                Timber.tag(TAG).d("POST request to $urlWithParams returned $statusCode")

                statusCode
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error executing POST request")
                500
            }
        }

    /**
     * 构建查询参数字符串
     * 将参数映射转换为URL查询字符串格式
     *
     * @param data 参数数据映射
     * @return 查询参数字符串
     */
    private fun formPrologData(data: Map<String, String>): String {
        val sb = StringBuilder()

        for ((key, value) in data) {
            if (sb.isNotEmpty()) {
                sb.append("&")
            } else {
                sb.append("?")
            }

            sb.append(key).append("=").append(value)
        }

        return sb.toString()
    }

    /**
     * 检查Feemaster服务可用性
     * 验证指定合约是否支持免费交易服务
     *
     * @param url 服务URL
     * @param chainId 链ID
     * @param address 合约地址
     * @return 服务是否可用
     */
    suspend fun checkFeemasterService(
        url: String,
        chainId: Long,
        address: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            var result = false

            try {
                val apiIndex = url.indexOf(API)
                if (apiIndex > 0) {
                    val pureServerURL = url.substring(0, apiIndex + API.length)
                    val sb =
                        StringBuilder(pureServerURL)
                            .append("checkContractIsSupportedForFreeTransfers")

                    val args = mapOf("contractAddress" to address)
                    val urlWithParams = sb.append(formPrologData(args)).toString()

                    val request =
                        Request
                            .Builder()
                            .url(urlWithParams)
                            .get()
                            .build()

                    val response = httpClient.newCall(request).execute()
                    val resultCode = response.code

                    if ((resultCode / 100) == 2) {
                        result = true
                    }

                    val responseBody = response.body?.string() ?: ""
                    Timber.tag("RESP").d(responseBody)
                    Timber.tag(TAG).d("Feemaster service check for $address: $result (code: $resultCode)")
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error checking feemaster service")
            }

            result
        }

    /**
     * 设置魔法链接解析器
     *
     * @param magicLinkParser 魔法链接解析器实例
     */
    fun setMagicLinkParser(magicLinkParser: ParseMagicLink) {
        this.parser = magicLinkParser
        Timber.tag(TAG).d("Magic link parser set")
    }

    /**
     * 获取魔法链接解析器
     *
     * @return 魔法链接解析器实例，可能为null
     */
    fun getMagicLinkParser(): ParseMagicLink? = parser

    /**
     * 验证URL格式
     * 检查URL是否包含有效的API路径
     *
     * @param url 要验证的URL
     * @return URL是否有效
     */
    fun isValidServiceUrl(url: String): Boolean =
        try {
            url.contains(API) && url.startsWith("http")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error validating service URL")
            false
        }

    /**
     * 获取服务端点信息
     * 返回当前配置的服务端点信息
     *
     * @return 服务端点信息映射
     */
    fun getServiceEndpoints(): Map<String, String> =
        mapOf(
            "xml_verifier" to XML_VERIFIER_ENDPOINT,
            "tsml_verifier" to TSML_VERIFIER_ENDPOINT,
            "tsml_verifier_staging" to TSML_VERIFIER_ENDPOINT_STAGING,
        )

    /**
     * 检查网络连接状态
     * 验证服务是否可以正常访问网络
     *
     * @return 网络是否可用
     */
    suspend fun checkNetworkConnection(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request
                        .Builder()
                        .url(TSML_VERIFIER_ENDPOINT)
                        .head() // 只发送HEAD请求检查连接
                        .build()

                val response = httpClient.newCall(request).execute()
                val isConnected = response.isSuccessful || response.code in 400..499 // 4xx也表示能连接到服务器

                Timber.tag(TAG).d("Network connection check: $isConnected")
                isConnected
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Network connection check failed")
                false
            }
        }
}
