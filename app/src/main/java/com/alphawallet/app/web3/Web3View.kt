package com.alphawallet.app.web3

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.alphawallet.app.BuildConfig
import com.alphawallet.app.entity.TransactionReturn
import com.alphawallet.app.entity.URLLoadInterface
import com.alphawallet.app.web3.entity.Address
import com.alphawallet.app.web3.entity.WalletAddEthereumChainObject
import com.alphawallet.app.web3.entity.Web3Call
import com.alphawallet.token.entity.EthereumMessage
import com.alphawallet.token.entity.EthereumTypedMessage
import com.alphawallet.token.entity.Signable
import timber.log.Timber

/**
 * Web3View 是一个定制的 WebView，用于与 DApp 交互。
 * 它注入了 AlphaWallet 的 Web3 provider，并处理来自 DApp 的各种区块链相关请求，
 * 如交易签名、消息签名和链切换等。
 */
class Web3View @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    companion object {
        private const val JS_PROTOCOL_CANCELLED = "cancelled"
        private const val JS_PROTOCOL_ON_SUCCESSFUL = "AlphaWallet.executeCallback(%1\$s, null, \"%2\$s\")"
        private const val JS_PROTOCOL_EXPR_ON_SUCCESSFUL = "AlphaWallet.executeCallback(%1\$s, null, %2\$s)"
        private const val JS_PROTOCOL_ON_FAILURE = "AlphaWallet.executeCallback(%1\$s, \"%2\$s\", null)"
    }

    private val web3ViewClient: Web3ViewClient = Web3ViewClient(getContext())

    /**
     * DApp 页面加载事件的回调接口。
     */
    var urlLoadInterface: URLLoadInterface? = null

    // --- Listeners for DApp interactions ---

    var onSignTransactionListener: OnSignTransactionListener? = null
    var onSignMessageListener: OnSignMessageListener? = null
    var onSignPersonalMessageListener: OnSignPersonalMessageListener? = null
    var onSignTypedMessageListener: OnSignTypedMessageListener? = null
    var onEthCallListener: OnEthCallListener? = null
    var onWalletAddEthereumChainObjectListener: OnWalletAddEthereumChainObjectListener? = null
    var onWalletActionListener: OnWalletActionListener? = null

    // --- Inner listeners to forward calls to public listeners ---

    private val innerOnSignTransactionListener = OnSignTransactionListener { transaction, url ->
        onSignTransactionListener?.onSignTransaction(transaction, url)
    }

    private val innerOnSignMessageListener = object : OnSignMessageListener {
        override fun onSignMessage(message: EthereumMessage) {
            onSignMessageListener?.onSignMessage(message)
        }
    }

    private val innerOnSignPersonalMessageListener = object : OnSignPersonalMessageListener {
        override fun onSignPersonalMessage(message: EthereumMessage) {
            onSignPersonalMessageListener?.onSignPersonalMessage(message)
        }

    }

    private val innerOnSignTypedMessageListener = object : OnSignTypedMessageListener {
        override fun onSignTypedMessage(message: EthereumTypedMessage) {
            onSignTypedMessageListener?.onSignTypedMessage(message)
        }

    }

    private val innerOnEthCallListener = object : OnEthCallListener {
        override fun onEthCall(txdata: Web3Call?) {
            onEthCallListener?.onEthCall(txdata)
        }

    }

    private val innerAddChainListener = object : OnWalletAddEthereumChainObjectListener {
        override fun onWalletAddEthereumChainObject(callbackId: Long, chainObject: WalletAddEthereumChainObject) {
            onWalletAddEthereumChainObjectListener?.onWalletAddEthereumChainObject(callbackId, chainObject)
        }

    }

    private val innerOnWalletActionListener = object : OnWalletActionListener {
        override fun onRequestAccounts(callbackId: Long) {
            onWalletActionListener?.onRequestAccounts(callbackId)
        }

        override fun onWalletSwitchEthereumChain(callbackId: Long, chainObj: WalletAddEthereumChainObject) {
            onWalletActionListener?.onWalletSwitchEthereumChain(callbackId, chainObj)
        }
    }

    init {
        initWebView()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        settings.apply {
            @Suppress("SetJavaScriptEnabled") // JavaScript is required for DApp functionality
            javaScriptEnabled = true
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            userAgentString = settings.userAgentString +
                    "AlphaWallet(Platform=Android&AppVersion=" + BuildConfig.VERSION_NAME + ")"
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        }

        setWebContentsDebuggingEnabled(true) // Allows developers to debug their scripts
        setInitialScale(0)

        addJavascriptInterface(
            SignCallbackJSInterface(
                this,
                innerOnSignTransactionListener,
                innerOnSignMessageListener,
                innerOnSignPersonalMessageListener,
                innerOnSignTypedMessageListener,
                innerOnEthCallListener,
                innerAddChainListener,
                innerOnWalletActionListener
            ), "alpha"
        )

        // Enable algorithmic darkening for dark mode if supported
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
        }
    }

    /**
     * 重写以包裹外部提供的 WebViewClient，确保内部的 Web3 功能正常运行。
     * @param client 外部提供的 WebViewClient。
     */
    override fun setWebViewClient(client: WebViewClient) {
        super.setWebViewClient(WrapWebViewClient(web3ViewClient, client))
    }

    /**
     * 使用 Web3 必需的 HTTP 头加载指定的 URL。
     * @param url 要加载的网页地址。
     */
    override fun loadUrl(url: String) {
        loadUrl(url, getWeb3Headers())
    }

    /**
     * 获取 Web3 (CORS) 请求所需的 HTTP 头。
     * @return 包含 CORS 相关头的 Map。
     */
    private fun getWeb3Headers(): Map<String, String> {
        return mapOf(
            "Connection" to "close",
            "Content-Type" to "text/plain",
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, DELETE, PUT, OPTIONS",
            "Access-Control-Max-Age" to "600",
            "Access-Control-Allow-Credentials" to "true",
            "Access-Control-Allow-Headers" to "accept, authorization, Content-Type"
        )
    }

    /**
     * 获取当前注入到 WebView 中的钱包地址。
     * @return 当前的钱包地址，如果未设置则为 null。
     */
    fun getWalletAddress(): Address? {
        return web3ViewClient.jsInjectorClient.walletAddress
    }

    /**
     * 设置并注入钱包地址到 WebView 的 JavaScript 环境中。
     * @param address 要设置的钱包地址。
     */
    fun setWalletAddress(address: Address) {
        web3ViewClient.jsInjectorClient.walletAddress = address
    }

    /**
     * 获取当前注入到 WebView 中的链 ID。
     * @return 当前的链 ID。
     */
    fun getChainId(): Long {
        return web3ViewClient.jsInjectorClient.chainId
    }

    /**
     * 设置并注入链 ID 到 WebView 的 JavaScript 环境中。
     * @param chainId 要设置的链 ID。
     */
    fun setChainId(chainId: Long) {
        web3ViewClient.jsInjectorClient.setChainId(chainId)
    }

    /**
     * 当交易签名成功后调用，将交易哈希回调给 DApp。
     * @param txData 包含交易和哈希的结果对象。
     */
    fun onSignTransactionSuccessful(txData: TransactionReturn) {
        callbackToJS(txData.tx.leafPosition, JS_PROTOCOL_ON_SUCCESSFUL, txData.hash.toString())
    }

    /**
     * 当消息签名成功后调用，将签名数据回调给 DApp。
     * @param message 原始的 `Signable` 消息对象。
     * @param signHex 签名后的十六进制字符串。
     */
    fun onSignMessageSuccessful(message: Signable, signHex: String) {
        callbackToJS(message.callbackId, JS_PROTOCOL_ON_SUCCESSFUL, signHex)
    }

    /**
     * 当 `eth_call` 等只读函数调用成功后调用，将结果回调给 DApp。
     * @param callbackId 回调 ID。
     * @param result 函数调用的结果字符串。
     */
    fun onCallFunctionSuccessful(callbackId: Long, result: String) {
        callbackToJS(callbackId, JS_PROTOCOL_ON_SUCCESSFUL, result)
    }

    /**
     * 当函数调用失败时调用，将错误信息回调给 DApp。
     * @param callbackId 回调 ID。
     * @param error 错误信息。
     */
    fun onCallFunctionError(callbackId: Long, error: String) {
        callbackToJS(callbackId, JS_PROTOCOL_ON_FAILURE, error)
    }

    /**
     * 当任何签名操作被用户取消时调用。
     * @param callbackId 回调 ID。
     */
    fun onSignCancel(callbackId: Long) {
        callbackToJS(callbackId, JS_PROTOCOL_ON_FAILURE, JS_PROTOCOL_CANCELLED)
    }

    /**
     * 当钱包操作（如 `eth_requestAccounts`）成功后调用。
     * @param callbackId 回调 ID。
     * @param expression 要在 DApp 上下文中执行的 JavaScript 表达式（通常是账户地址数组）。
     */
    fun onWalletActionSuccessful(callbackId: Long, expression: String?) {
        val callback = String.format(JS_PROTOCOL_EXPR_ON_SUCCESSFUL, callbackId, expression)
        post { evaluateJavascript(callback, Timber::d) }
    }

    /**
     * 重置注入状态，用于页面重新加载。
     */
    fun resetView() {
        web3ViewClient.resetInject()
    }

    private fun callbackToJS(callbackId: Long, function: String, param: String) {
        val callback = String.format(function, callbackId, param)
        post { evaluateJavascript(callback) { value -> Timber.tag("WEB_VIEW").d(value) } }
    }

    /**
     * 一个包裹类，用于将内部的 Web3ViewClient 和外部设置的 WebViewClient 结合起来，
     * 以确保 Web3 的功能和 DApp 浏览器本身的功能都能正常工作。
     */
    private inner class WrapWebViewClient(
        private val internalClient: Web3ViewClient,
        private val externalClient: WebViewClient
    ) : WebViewClient() {
        private var loadingError = false
        private var redirect = false

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            view.clearCache(true)
            if (!redirect) {
                // 注入 provider 和初始化脚本
                view.evaluateJavascript(internalClient.getProviderString(view), null)
                view.evaluateJavascript(internalClient.getInitString(view), null)
                internalClient.resetInject()
            }
            redirect = false
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (!redirect && !loadingError) {
                urlLoadInterface?.onWebpageLoaded(url, view.title)
            } else if (!loadingError) {
                urlLoadInterface?.onWebpageLoadComplete()
            }
            redirect = false
            loadingError = false
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            redirect = true
            // 外部 client 优先处理，如果它不处理，则内部 client 处理
            return externalClient.shouldOverrideUrlLoading(view, request) ||
                    internalClient.shouldOverrideUrlLoading(view, url)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            loadingError = true
            externalClient.onReceivedError(view, request, error)
        }
    }
}
