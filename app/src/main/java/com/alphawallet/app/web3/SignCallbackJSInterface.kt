package com.alphawallet.app.web3

import android.text.TextUtils
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.alphawallet.app.entity.CryptoFunctions
import com.alphawallet.app.entity.tokenscript.TokenscriptFunction.Companion.ZERO_ADDRESS
import com.alphawallet.app.util.Hex
import com.alphawallet.app.util.Utils
import com.alphawallet.app.web3.entity.Address
import com.alphawallet.app.web3.entity.WalletAddEthereumChainObject
import com.alphawallet.app.web3.entity.Web3Call
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.token.entity.EthereumMessage
import com.alphawallet.token.entity.EthereumTypedMessage
import com.alphawallet.token.entity.SignMessageType
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.json.JSONObject
import org.web3j.protocol.core.DefaultBlockParameterName
import timber.log.Timber
import java.math.BigInteger

/**
 * 这此类作为 Web3 JavaScript 接口，暴露原生 Android 方法给 WebView 中的 DApp 调用。
 * 它处理来自 DApp 的各种签名请求和钱包操作，并将它们转发给相应的监听器进行处理。
 *
 * @property webView 当前 DApp 所在的 WebView 实例，用于在主线程上执行回调。
 * @property onSignTransactionListener 处理 `eth_signTransaction` 和 `eth_sendTransaction` 请求的监听器。
 * @property onSignMessageListener 处理 `eth_sign` 请求的监听器。
 * @property onSignPersonalMessageListener 处理 `personal_sign` 请求的监听器。
 * @property onSignTypedMessageListener 处理 `eth_signTypedData` 请求的监听器。
 * @property onEthCallListener 处理 `eth_call` 请求的监听器。
 * @property onWalletAddEthereumChainObjectListener 处理 `wallet_addEthereumChain` 请求的监听器。
 * @property onWalletActionListener 处理 `eth_requestAccounts` 和 `wallet_switchEthereumChain` 请求的监听器。
 */
class SignCallbackJSInterface(
    private val webView: WebView,
    private val onSignTransactionListener: OnSignTransactionListener,
    private val onSignMessageListener: OnSignMessageListener,
    private val onSignPersonalMessageListener: OnSignPersonalMessageListener,
    private val onSignTypedMessageListener: OnSignTypedMessageListener,
    private val onEthCallListener: OnEthCallListener,
    private val onWalletAddEthereumChainObjectListener: OnWalletAddEthereumChainObjectListener,
    private val onWalletActionListener: OnWalletActionListener
) {
    /**
     * 处理来自 DApp 的交易签名请求 (`eth_signTransaction` 或 `eth_sendTransaction`)。
     *
     * @param callbackId 用于识别此请求的回调 ID。
     * @param recipient 交易接收方地址。
     * @param value 交易发送的以太币数量（以 Wei 为单位），十六进制字符串。
     * @param nonce 交易的 nonce 值，十六进制字符串。
     * @param gasLimit 交易的 Gas 上限，十六进制字符串。
     * @param gasPrice 交易的 Gas 价格，十六进制字符串。
     * @param payload 交易的数据负载（例如，调用智能合约方法的编码数据）。
     */
    @JavascriptInterface
    fun signTransaction(
        callbackId: Int,
        recipient: String,
        value: String?,
        nonce: String,
        gasLimit: String,
        gasPrice: String?,
        payload: String
    ) {
        val finalValue = if (value == "undefined") "0" else value
        val transaction = Web3Transaction(
            recipient = if (TextUtils.isEmpty(recipient)) Address.EMPTY else Address(recipient),
            contract = null,
            value = finalValue?.let { Hex.hexToBigInteger(it, BigInteger.ZERO) }?: BigInteger.ZERO,
            gasPrice = gasPrice?.let { Hex.hexToBigInteger(it, BigInteger.ZERO) }?: BigInteger.ZERO,
            gasLimit = Hex.hexToBigInteger(gasLimit, BigInteger.ZERO),
            nonce = Hex.hexToLong(nonce, -1),
            payload = payload,
            leafPosition = callbackId.toLong()  // 使用 callbackId作为leafPosition并修复了参数名
        )
        // 回调必须在 UI 线程上执行
        webView.post { onSignTransactionListener.onSignTransaction(transaction, getUrl()) }
    }

    /**
     * 处理来自 DApp 的 `eth_sign` 消息签名请求。
     *
     * @param callbackId 回调 ID。
     * @param data 要签名的消息数据。
     */
    @JavascriptInterface
    fun signMessage(callbackId: Int, data: String) {
        val message = EthereumMessage(data, getUrl(), callbackId.toLong(), SignMessageType.SIGN_MESSAGE)
        webView.post { onSignMessageListener.onSignMessage(message) }
    }

    /**
     * 处理来自 DApp 的 `personal_sign` 消息签名请求。
     *
     * @param callbackId 回调 ID。
     * @param data 要签名的消息数据。
     */
    @JavascriptInterface
    fun signPersonalMessage(callbackId: Int, data: String) {
        val message = EthereumMessage(data, getUrl(), callbackId.toLong(), SignMessageType.SIGN_PERSONAL_MESSAGE)
        webView.post { onSignPersonalMessageListener.onSignPersonalMessage(message) }
    }

    /**
     * 处理来自 DApp 的 `eth_requestAccounts` 请求，用于获取用户钱包地址。
     *
     * @param callbackId 回调 ID。
     */
    @JavascriptInterface
    fun requestAccounts(callbackId: Long) {
        webView.post { onWalletActionListener.onRequestAccounts(callbackId) }
    }

    /**
     * 处理来自 DApp 的 `eth_signTypedData` (EIP-712) 签名请求。
     *
     * @param callbackId 回调 ID。
     * @param data 包含 `from` 地址和 EIP-712 消息数据的 JSON 字符串。
     */
    @JavascriptInterface
    fun signTypedMessage(callbackId: Int, data: String) {
        webView.post {
            try {
                val obj = JSONObject(data)
                val messageData = obj.getString("data")
                val cryptoFunctions = CryptoFunctions()
                val message = EthereumTypedMessage(messageData, getDomainName(), callbackId.toLong(), cryptoFunctions)
                onSignTypedMessageListener.onSignTypedMessage(message)
            } catch (e: Exception) {
                // 如果解析失败，创建一个带有错误标记的消息
                val errorMessage = EthereumTypedMessage(null, "", getDomainName(), callbackId.toLong())
                onSignTypedMessageListener.onSignTypedMessage(errorMessage)
                Timber.e(e)
            }
        }
    }

    /**
     * 处理来自 DApp 的 `eth_call` 请求，用于执行一个只读的智能合约调用。
     *
     * @param callbackId 回调 ID。
     * @param recipient 包含 `to`, `data`, `value`, `gas` 等参数的 JSON 对象字符串。
     */
    @JavascriptInterface
    fun ethCall(callbackId: Int, recipient: String) {
        try {
            val json = JSONObject(recipient)
            val to = if (json.has("to")) json.getString("to") else ZERO_ADDRESS
            val payload = if (json.has("data")) json.getString("data") else "0x"
            val value = if (json.has("value")) json.getString("value") else null
            val gasLimit = if (json.has("gas")) json.getString("gas") else null
            // TODO: 从查询中获取区块参数
            val defaultBlockParameter = DefaultBlockParameterName.LATEST

            val call = Web3Call(
                Address(to),
                defaultBlockParameter,
                payload,
                value,
                gasLimit,
                callbackId.toLong()
            )
            webView.post { onEthCallListener.onEthCall(call) }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    /**
     * 处理来自 DApp 的 `wallet_addEthereumChain` 请求，用于添加一个新的网络配置。
     *
     * @param callbackId 回调 ID。
     * @param msgParams 包含链信息的 JSON 对象字符串。
     */
    @JavascriptInterface
    fun walletAddEthereumChain(callbackId: Int, msgParams: String) {
        try {
            val chainObj = Gson().fromJson(msgParams, WalletAddEthereumChainObject::class.java)
            if (!chainObj.chainId.isNullOrEmpty()) {
                webView.post { onWalletAddEthereumChainObjectListener.onWalletAddEthereumChainObject(callbackId.toLong(), chainObj) }
            }
        } catch (e: JsonSyntaxException) {
            Timber.e(e)
        }
    }

    /**
     * 处理来自 DApp 的 `wallet_switchEthereumChain` 请求，用于切换到指定的网络。
     *
     * @param callbackId 回调 ID。
     * @param msgParams 包含 `chainId` 的 JSON 对象字符串。
     */
    @JavascriptInterface
    fun walletSwitchEthereumChain(callbackId: Int, msgParams: String) {
        try {
            val chainObj = Gson().fromJson(msgParams, WalletAddEthereumChainObject::class.java)
            if (!chainObj.chainId.isNullOrEmpty()) {
                webView.post { onWalletActionListener.onWalletSwitchEthereumChain(callbackId.toLong(), chainObj) }
            }
        } catch (e: JsonSyntaxException) {
            Timber.e(e)
        }
    }

    private fun getUrl(): String {
        return webView.url ?: ""
    }

    private fun getDomainName(): String {
        return webView.url?.let { Utils.getDomainName(it) } ?: ""
    }
}
