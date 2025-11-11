package com.alphawallet.app.web3

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Base64
import android.view.KeyEvent
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.alphawallet.app.BuildConfig
import com.alphawallet.app.R
import com.alphawallet.app.entity.UpdateType
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokenscript.TokenScriptRenderCallback
import com.alphawallet.app.entity.tokenscript.WebCompletionCallback
import com.alphawallet.app.repository.entity.RealmAuxData
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.web3.entity.Address
import com.alphawallet.app.web3.entity.FunctionCallback
import com.alphawallet.app.web3.entity.PageReadyCallback
import com.alphawallet.token.entity.EthereumMessage
import com.alphawallet.token.entity.Signable
import com.alphawallet.token.entity.TSTokenView
import com.alphawallet.token.entity.TicketRange
import com.alphawallet.token.entity.TokenScriptResult
import com.alphawallet.token.entity.ViewType
import com.alphawallet.token.tools.TokenDefinition
import com.alphawallet.token.tools.TokenDefinition.Companion.TOKENSCRIPT_ERROR
import io.realm.Realm
import io.realm.RealmResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigInteger
import java.nio.charset.StandardCharsets

/**
 * Web3TokenView 是一个自定义的 WebView，专门用于渲染和与 TokenScript 视图交互。
 * 它注入 Web3 provider，处理来自 TokenScript 的回调，并管理视图的生命周期。
 *
 * 此视图使用 Coroutine (viewScope) 来管理异步操作，并在 onAttachedToWindow/onDetachedFromWindow 中管理其生命周期。
 * 它还包含用于处理 Realm 数据库更改以自动刷新视图的逻辑。
 */
class Web3TokenView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    companion object {
        const val RENDERING_ERROR = "<html>$TOKENSCRIPT_ERROR\${ERR1}</html>"
        const val RENDERING_ERROR_SUPPLIMENTAL = "</br></br>Error in line \$ERR1:</br>\$ERR2"

        private const val JS_PROTOCOL_CANCELLED = "cancelled"
        private const val JS_PROTOCOL_ON_SUCCESSFUL = "executeCallback(%1\$s, null, \"%2\$s\")"
        private const val JS_PROTOCOL_ON_FAILURE = "executeCallback(%1\$s, \"%2\$s\", null)"
    }

    // 用于注入 JS 的客户端
    private val jsInjectorClient: JsInjectorClient = JsInjectorClient(getContext())

    // 用于处理 WebView 事件的客户端
    private val tokenScriptClient: TokenScriptClient = TokenScriptClient(this)

    // 页面加载完成的回调
    private var assetHolder: PageReadyCallback? = null

    // 标记当前是否正在显示错误
    private var showingError = false

    // 键盘回车键的回调
    private var keyPressCallback: WebCompletionCallback? = null

    // 与此视图生命周期绑定的 CoroutineScope
    private var scopeJob = SupervisorJob()
    private var viewScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + scopeJob)

    // 用于监听 TokenScript setValues 回调
    var onSetValuesListener: OnSetValuesListener? = null

    // 用于监听 TokenScript 签名个人消息的回调
    var onSignPersonalMessageListener: OnSignPersonalMessageListener? = null

    // 用于存储从 TokenScript 解析的属性结果
    private var attrResults: String = ""

    // 监听 Realm 数据库更新
    private var realmAuxUpdates: RealmResults<RealmAuxData>? = null

    // 存储未编码的 HTML 页面内容
    private var unencodedPage: String? = null

    init {
        init()
    }

    /**
     * 初始化 WebView 的设置。
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun init() {
        // 基础 WebView 设置
        settings.apply {
            @Suppress("SetJavaScriptEnabled") // TokenScript 运行需要 JS
            javaScriptEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            useWideViewPort = false
            loadWithOverviewMode = true
            userAgentString = settings.userAgentString +
                    "AlphaWallet(Platform=Android&AppVersion=" + BuildConfig.VERSION_NAME + ")"
        }

        // 启用 Web 内容调试
        setWebContentsDebuggingEnabled(true)

        // 如果支持，启用算法变暗（暗黑模式）
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
        }

        // UI 设置
        scrollBarSize = 0
        isVerticalScrollBarEnabled = false
        isScrollContainer = false
        isScrollbarFadingEnabled = true
        setInitialScale(0)
        clearCache(true)
        showingError = false

        // 添加 JavaScript 接口，用于从 TokenScript 回调到 Kotlin
        addJavascriptInterface(
            TokenScriptCallbackInterface(this, innerOnSignPersonalMessageListener, innerOnSetValuesListener),
            "alpha"
        )

        // 设置客户端
        webChromeClient = object : WebChromeClient() {
            /**
             * 处理来自 WebView 的控制台消息。
             * @param msg 控制台消息。
             * @return true 表示已处理该消息。
             */
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                Timber.w("Web3Token Message: %s", msg.message())
                return true
            }

            /**
             * 处理来自 WebView 的 JS Alert。
             * @return true 表示已处理，阻止弹窗显示。
             */
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                // 阻止 alert 弹窗
                result.cancel()
                return true
            }
        }
        webViewClient = tokenScriptClient
    }

    /**
     * 在 WebView 中显示一个错误消息。
     * @param error 要显示的 HTML 格式的错误字符串。
     */
    fun showError(error: String) {
        showingError = true
        visibility = VISIBLE
        loadData(error, "text/html", "utf-8")
    }

    /**
     * 设置当前钱包地址，以便注入到 JavaScript 环境中。
     * @param address 当前用户的钱包地址。
     */
    fun setWalletAddress(address: Address) {
        jsInjectorClient.walletAddress = address
    }

    /**
     * 设置一个回调，用于处理 JavaScript 中的 `window.close()` 事件。
     * @param callback 当 `window.close()` 被调用时执行的回调。
     */
    fun setupWindowCallback(callback: FunctionCallback) {
        webChromeClient = object : WebChromeClient() {
            override fun onCloseWindow(window: WebView) {
                callback.functionSuccess()
            }

            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                return true
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                result.cancel()
                return true
            }
        }
    }

    /**
     * 设置当前链 ID，以便注入到 JavaScript 环境中。
     * @param chainId 当前网络的链 ID。
     */
    fun setChainId(chainId: Long) {
        jsInjectorClient.setChainId(chainId)
    }

    /**
     * 当个人消息签名成功后调用此方法，将签名结果回调给 JavaScript。
     * @param message 原始的 `Signable` 消息对象。
     * @param signHex 签名后的十六进制字符串。
     */
    fun onSignPersonalMessageSuccessful(message: Signable, signHex: String) {
        callbackToJS(message.callbackId, JS_PROTOCOL_ON_SUCCESSFUL, signHex)
    }

    /**
     * 设置一个监听器，用于处理 WebView 中的回车键按压事件。
     * @param cpCallback 当回车键被按下时执行的回调。
     */
    fun setKeyboardListenerCallback(cpCallback: WebCompletionCallback) {
        keyPressCallback = cpCallback
    }

    /**
     * 重写以提供一个固定的 URL，用于标识 TokenScript 环境。
     * @return 返回 "TokenScript"。
     */
    override fun getUrl(): String {
        return "TokenScript"
    }

    /**
     * 在 WebView 中异步执行一段 JavaScript 代码。
     * @param function 要执行的 JavaScript 函数或代码字符串。
     */
    fun callToJS(function: String) {
        post { evaluateJavascript(function) { value -> Timber.tag("WEB_VIEW").d(value) } }
    }

    /**
     * TokenScript 专用的 JavaScript 调用，带有渲染回调。
     * @param fName 函数名称，用于调试。
     * @param script 要执行的 JavaScript。
     * @param cb 执行完成后的回调。
     */
    @JavascriptInterface
    fun TScallToJS(fName: String, script: String, cb: TokenScriptRenderCallback) {
        post { evaluateJavascript(script) { value -> cb.callToJSComplete(fName, value) } }
    }

    /**
     * 将结果回调给 JavaScript 中的特定回调函数。
     * @param callbackId 回调的 ID。
     * @param function 格式化的 JavaScript 回调函数字符串（例如 "executeCallback(%1$s, null, \"%2\$s\")"）。
     * @param param 要传递给回调函数的参数。
     */
    @JavascriptInterface
    fun callbackToJS(callbackId: Long, function: String, param: String) {
        val callback = String.format(function, callbackId, param)
        post { evaluateJavascript(callback) { value -> Timber.tag("WEB_VIEW").d(value) } }
    }

    /**
     * 内部监听器，用于将签名请求桥接到外部设置的 [onSignPersonalMessageListener]。
     */
    private val innerOnSignPersonalMessageListener = object : OnSignPersonalMessageListener {
        override fun onSignPersonalMessage(message: EthereumMessage?) {
            onSignPersonalMessageListener?.onSignPersonalMessage(message)
        }
    }

        /**
         * 内部监听器，用于将 setValues 请求桥接到外部设置的 [onSetValuesListener]。
         */
        private val innerOnSetValuesListener = OnSetValuesListener { updates ->
            onSetValuesListener?.setValues(updates)
        }

        /**
         * 当签名操作被用户取消时调用，将 "cancelled" 状态回调给 JavaScript。
         * @param message 原始的 `Signable` 消息对象。
         */
        fun onSignCancel(message: Signable) {
            callbackToJS(message.callbackId, JS_PROTOCOL_ON_FAILURE, JS_PROTOCOL_CANCELLED)
        }

        /**
         * 设置一个回调，在页面渲染完成时被调用。
         * @param holder 页面准备就绪的回调接口。
         */
        fun setOnReadyCallback(holder: PageReadyCallback) {
            assetHolder = holder
        }

        /**
         * 注入 Web3 初始化脚本、代币数据和 Token ID。
         * @param view 视图的 HTML 内容。
         * @param tokenContent 序列化后的代币属性。
         * @param tokenId 当前的 Token ID。
         * @return 注入脚本后的 HTML 内容。
         */
        fun injectWeb3TokenInit(view: String, tokenContent: String, tokenId: BigInteger): String {
            return jsInjectorClient.injectWeb3TokenInit(context, view, tokenContent, tokenId)
        }

        /**
         * 向 HTML 中注入一段 JavaScript 代码。
         * @param view 视图的 HTML 内容。
         * @param buildToken 要注入的 JS 代码。
         * @return 注入脚本后的 HTML 内容。
         */
        fun injectJS(view: String, buildToken: String): String {
            return jsInjectorClient.injectJS(view, buildToken)
        }

        /**
         * 注入 CSS 样式并包裹视图内容。
         * @param viewData 视图的 HTML 内容。
         * @param style 要注入的 CSS 样式。
         * @return 注入样式后的 HTML 内容。
         */
        fun injectStyleAndWrapper(viewData: String, style: String?): String {
            return jsInjectorClient.injectStyleAndWrap(viewData, style)
        }

        /**
         * 根据视图类型（例如，是否为图标化视图）设置布局参数。
         * @param token 当前的 Token 对象。
         * @param iconified 视图类型 (ITEM_VIEW 或 VIEW)。
         */
        fun setLayout(token: Token, iconified: ViewType) {
            if (iconified == ViewType.ITEM_VIEW && token.itemViewHeight > 0) {
                val params = LinearLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    token.itemViewHeight
                )
                layoutParams = params
            }
        }

        /**
         * 自定义的 WebViewClient，用于处理页面加载事件和 URL 覆盖。
         */
        private inner class TokenScriptClient(private val web3: Web3TokenView) : WebViewClient() {
            /**
             * 页面加载完成时调用。
             */
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                assetHolder?.onPageRendered(view)
            }

            /**
             * 页面内容首次可见时调用。
             */
            override fun onPageCommitVisible(view: WebView, url: String) {
                super.onPageCommitVisible(view, url)
                unencodedPage = null
                assetHolder?.onPageLoaded(view)
            }

            /**
             * 处理未处理的按键事件。
             */
            override fun onUnhandledKeyEvent(view: WebView, event: KeyEvent) {
                if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    keyPressCallback?.enterKeyPressed()
                }
                super.onUnhandledKeyEvent(view, event)
            }

            /**
             * 决定是否应覆盖 URL 加载。
             * @return true 如果宿主应用处理了该 URL，否则 false。
             */
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return assetHolder?.overridePageLoad(view, request.url.toString()) ?: false
            }

            /**
             * 处理 SSL 错误。
             */
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                // 在调试版本中忽略 SSL 错误，但在生产环境中应谨慎处理
                Timber.w("SSL Error: %s", error.toString())
                handler.proceed()
            }
        }

        /**
         * 渲染一个票券持有者视图（默认使用 ITEM_VIEW）。
         * @param token Token 对象。
         * @param range 票券范围。
         * @param assetService AssetDefinitionService 实例。
         */
        fun displayTicketHolder(token: Token, range: TicketRange, assetService: AssetDefinitionService) {
            displayTicketHolder(token, range, assetService, ViewType.ITEM_VIEW)
        }

        /**
         * 渲染一个票券持有者视图，并指定视图类型。
         * 此方法使用 Coroutine 异步获取 TokenDefinition。
         * @param token Token 对象。
         * @param range 票券范围。
         * @param assetService AssetDefinitionService 实例。
         * @param iconified 视图类型 (ITEM_VIEW 或 VIEW)。
         */
        fun displayTicketHolder(
            token: Token,
            range: TicketRange,
            assetService: AssetDefinitionService,
            iconified: ViewType
        ) {
            viewScope.launch {
                try {
                    // 异步获取 TokenDefinition
                    val definition = withContext(Dispatchers.IO) {
                        assetService.getAssetDefinitionAsync(token)
                    }
                    renderTicketHolder(token, definition, range, assetService, iconified)
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    loadingError(throwable)
                }
            }
        }

        /**
         * 加载时发生错误的日志记录。
         * @param e 发生的异常。
         */
        private fun loadingError(e: Throwable) {
            Timber.e(e)
        }

        /**
         * 根据 TokenDefinition 渲染票券持有者视图。
         * @param td TokenDefinition，如果为 null，则显示旧版视图。
         */
        private fun renderTicketHolder(
            token: Token,
            td: TokenDefinition?,
            range: TicketRange,
            assetService: AssetDefinitionService,
            iconified: ViewType
        ) {
            if (td?.holdingToken != null) {
                // 使用 TokenScript 渲染视图
                renderTokenScriptInfoView(token, range, assetService, iconified, td)
            } else {
                // 显示传统的旧版视图
                showLegacyView(token, range)
            }
        }

        /**
         * 显示一个不基于 TokenScript 的传统（旧版）视图。
         * @param token Token 对象。
         * @param range 票券范围。
         */
        private fun showLegacyView(token: Token, range: TicketRange) {
            visibility = VISIBLE
            val displayData = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    h4 { display: inline; color: green; font: 20px Helvetica, Sans-Serif; padding: 6px; font-weight: bold;}
                    h5 { display: inline; color: black; font: 18px Helvetica, Sans-Serif; font-weight: normal;}
                </style>
            </head>
            <body>
                <div><h4><span>x${range.tokenIds.size}</span></h4><h5><span>${token.getFullName()}</span></h5></div>
            </body>
            </html>
        """.trimIndent()
            loadData(displayData, "text/html", "utf-8")
        }

        /**
         * 渲染 TokenScript 的 "Info" 视图。
         * 此方法会异步解析属性，然后构建并加载 HTML。
         *
         * @param token Token 对象。
         * @param range 票券范围。
         * @param assetService AssetDefinitionService 实例。
         * @param itemView 视图类型 (ITEM_VIEW 或 VIEW)。
         * @param td TokenDefinition。
         * @return 如果视图存在并开始渲染，返回 true；否则返回 false。
         */
        fun renderTokenScriptInfoView(
            token: Token,
            range: TicketRange,
            assetService: AssetDefinitionService,
            itemView: ViewType,
            td: TokenDefinition?
        ): Boolean {
            val tokenId = range.tokenIds[0]
            val tokenView: TSTokenView = td?.getTSTokenView("Info") ?: return false

            attrResults = "" // 重置属性结果

            viewScope.launch {
                try {
                    // 1. (IO) 获取基础属性
                    val attrs = withContext(Dispatchers.IO) {
                        assetService.getTokenAttrs(token, tokenId, range.balance)
                    }

                    // 2. (IO) 异步解析属性 (从 RxJava 转换为 Flow)
                    // 假设 assetService.resolveAttrs 返回 Observable (RxJava)
                    assetService.resolveAttrs(
                        token, null, tokenId,
                        assetService.getTokenViewLocalAttributes(token), itemView, UpdateType.USE_CACHE
                    )
//                    .asFlow() // 转换为 Kotlin Flow
                        .onEach { attribute ->
                            // 3. (Main) 收集每个属性 (onAttr)
                            onAttr(attribute, attrs)
                        }
                        .onCompletion {
                            // 4. (Main) 所有属性收集完毕 (onComplete)
                            displayTokenView(token, assetService, attrs, itemView, range, td, tokenView)
                        }
                        .catch { throwable ->
                            // 5. (Main) 处理解析错误 (onError)
                            onError(token, throwable, range)
                        }
                        .collect{} // 启动 Flow 收集

                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    onError(token, e, range)
                }
            }

            return true
        }

        /**
         * 将已解析的属性添加到 [StringBuilder] 中，用于构建注入到 JS 的代币属性。
         * @param attribute 已解析的属性。
         * @param attrs 用于构建 JS 对象的 StringBuilder。
         */
        private fun onAttr(attribute: TokenScriptResult.Attribute, attrs: StringBuilder) {
            TokenScriptResult.addPair(attrs, attribute.id, attribute.text)
            attrResults += attribute.text
        }

        /**
         * (onComplete) 在所有属性都解析完毕后，构建最终的 HTML/JS/CSS 并加载到 WebView 中。
         * 同时设置 Realm 数据库监听器以实现自动刷新。
         */
        private fun displayTokenView(
            token: Token,
            assetService: AssetDefinitionService,
            attrs: StringBuilder,
            iconified: ViewType,
            range: TicketRange,
            td: TokenDefinition,
            tokenView: TSTokenView
        ) {
            visibility = VISIBLE

            var view = tokenView.tokenView
            if (TextUtils.isEmpty(view)) {
                view = buildViewError(token, range, tokenView.label)
            }
            val style = tokenView.style
            unencodedPage = injectWeb3TokenInit(view, attrs.toString(), range.tokenIds[0])
            unencodedPage = injectStyleAndWrapper(unencodedPage?:"", style) // 样式最后注入，以确保在最前面

            val base64 = Base64.encodeToString(unencodedPage!!.toByteArray(StandardCharsets.UTF_8), Base64.DEFAULT)
            val urlFragment = if (tokenView.urlFragment.isNotEmpty()) "#" + tokenView.urlFragment else ""
            loadData(base64 + urlFragment, "text/html; charset=utf-8", "base64")

            // 移除旧的监听器
            realmAuxUpdates?.removeAllChangeListeners()

            // 设置新的 Realm 监听器以刷新视图
            val realm = assetService.eventRealm
            val lastUpdateTime = getLastUpdateTime(realm, token, range.tokenIds[0])
            realmAuxUpdates = RealmAuxData.getEventListener(realm, token, range.tokenIds[0], 1, lastUpdateTime)
            realmAuxUpdates?.addChangeListener { realmAux ->
                if (realmAux.size == 0) return@addChangeListener
                // 数据发生变化，重新渲染
                renderTicketHolder(token, td, range, assetService, iconified)
            }

            invalidate()
        }

        /**
         * 获取指定 Token ID 的最后一次事件更新时间。
         * @param realm Realm 实例。
         * @param token Token 对象。
         * @param tokenId Token ID。
         * @return 最后一次更新的时间戳 + 1。
         */
        private fun getLastUpdateTime(realm: Realm, token: Token, tokenId: BigInteger): Long {
            var lastResultTime: Long = 0
            val lastEntry = RealmAuxData.getEventQuery(realm, token, tokenId, 1, 0).findAll()
            if (!lastEntry.isEmpty()) {
                val data = lastEntry.first()
                data?.let { lastResultTime = it.resultTime }
            }
            return lastResultTime + 1
        }

        /**
         * 获取属性解析的结果字符串（用于调试或测试）。
         * @return 拼接的属性文本值。
         */
        fun getAttrResults(): String {
            return attrResults
        }

        /**
         * 当视图暂停时（例如 Activity onPause），清理 Realm 监听器并清空 WebView。
         */
        override fun onPause() {
            super.onPause()
            realmAuxUpdates?.removeAllChangeListeners()
            realmAuxUpdates?.realm?.let {
                if (!it.isClosed) {
                    it.close()
                }
            }
            realmAuxUpdates = null

            loadData("", "text/html", "utf-8") // 清空视图内容
        }

        /**
         * 当视图销毁时，确保所有资源（Realm 监听器）都被释放。
         */
        override fun destroy() {
            super.destroy()
            realmAuxUpdates?.removeAllChangeListeners()
            realmAuxUpdates?.realm?.let {
                if (!it.isClosed) {
                    it.close()
                }
            }
            realmAuxUpdates = null
        }

        /**
         * 当视图附加到窗口时，重新创建 CoroutineScope。
         */
        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (scopeJob.isCancelled) {
                scopeJob = SupervisorJob()
                viewScope = CoroutineScope(Dispatchers.Main.immediate + scopeJob)
            }
        }

        /**
         * 当视图从窗口分离时，取消所有正在运行的 Coroutine。
         */
        override fun onDetachedFromWindow() {
            scopeJob.cancel()
            super.onDetachedFromWindow()
        }

        /**
         * 在 TokenScript 视图未找到时，构建一个错误/占位视图。
         * @param token Token 对象。
         * @param range 票券范围。
         * @param viewName 未找到的视图名称。
         * @return 用于显示的 HTML 字符串。
         */
        private fun buildViewError(token: Token, range: TicketRange, viewName: String): String {
            var displayData =
                "<h3><span style=\"color:Green\">x${range.tokenIds.size}</span><span style=\"color:Black\"> ${token.getFullName()}</span></h3>"
            displayData += ("<br /><body>" + context.getString(R.string.card_view_not_found_error, viewName) + "</body>")
            return displayData
        }

        /**
         * 在属性解析出错时，显示一个错误/占位视图。
         * @param token Token 对象。
         * @param throwable 发生的异常。
         * @param range 票券范围。
         */
        private fun onError(token: Token, throwable: Throwable, range: TicketRange) {
            Timber.e(throwable)
            var displayData =
                "<h3><span style=\"color:Green\">x${range.tokenIds.size}</span><span style=\"color:Black\"> ${token.getFullName()}</span></h3>"
            if (BuildConfig.DEBUG) displayData += ("<br /><body>" + throwable.localizedMessage + "</body>")
            loadData(displayData, "text/html", "utf-8")
        }
    }
