package com.alphawallet.app.web3

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.alphawallet.app.R
import com.alphawallet.app.widget.AWalletAlertDialog

/**
 * Web3ViewClient 继承自 WebViewClient，为 Web3View 提供核心的页面加载、
 * URL 拦截和 SSL 错误处理逻辑。
 *
 * @param context 安卓上下文，用于访问资源和启动 Activity。
 */
class Web3ViewClient(private val context: Context) : WebViewClient() {

    /**
     * JsInjectorClient 负责向 WebView 中注入 JavaScript provider 和初始化脚本。
     */
    val jsInjectorClient: JsInjectorClient = JsInjectorClient(context)

    /**
     * 拦截并处理 URL 加载请求。如果 URL 匹配受信任的应用列表，则尝试打开该应用。
     *
     * @param view 当前的 WebView 实例。
     * @param url 要加载的 URL 字符串。
     * @return 如果 URL 已被处理则返回 true，否则返回 false 以便 WebView 继续加载。
     */
    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return handleTrustedApps(url)
    }

    /**
     * 拦截并处理 URL 加载请求 (适用于 API 21+)。
     *
     * @param view 当前的 WebView 实例。
     * @param request 包含 URL 和其他信息的 WebResourceRequest 对象。
     * @return 如果 URL 已被处理则返回 true，否则返回 false。
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        return handleTrustedApps(url)
    }

    /**
     * 拦截网络请求。当前实现直接返回父类的处理结果。
     *
     * @param view 当前的 WebView 实例。
     * @param request 网络请求对象。
     * @return 返回一个 WebResourceResponse 或 null。
     */
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        // request is already checked for nullability in the framework
        return super.shouldInterceptRequest(view, request)
    }

    /**
     * 获取 Web3 provider 的初始化 JavaScript 脚本字符串。
     *
     * @param view 当前的 WebView 实例。
     * @return 初始化脚本字符串。
     */
    fun getInitString(view: WebView): String {
        return jsInjectorClient.initJs(view.context)
    }

    /**
     * 获取 Web3 provider 的核心 JavaScript 脚本字符串。
     *
     * @param view 当前的 WebView 实例。
     * @return provider 脚本字符串。
     */
    fun getProviderString(view: WebView): String {
        return jsInjectorClient.providerJs(view.context)
    }

    /**
     * 处理 SSL 证书错误。弹出一个对话框让用户选择是继续还是取消。
     *
     * @param view 当前的 WebView 实例。
     * @param handler SSL 错误处理器，用于继续或取消加载。
     * @param error SSL 错误信息。
     */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        AWalletAlertDialog(context).apply {
            setTitle(R.string.title_dialog_error)
            setIcon(AWalletAlertDialog.ERROR)
            setMessage(R.string.ssl_cert_invalid)
            setButtonText(R.string.dialog_approve)
            setButtonListener {
                handler.proceed()
                dismiss()
            }
            setSecondaryButtonText(R.string.action_cancel)
            // 修复：需要为取消按钮设置独立的监听器
            setSecondaryButtonListener {
                handler.cancel()
                dismiss()
            }
            show()
        }
    }

    /**
     * 检查 URL 是否匹配受信任的应用列表，如果匹配则尝试通过 Intent 打开。
     *
     * @param url 要检查的 URL。
     * @return 如果 URL 已被处理则返回 true，否则返回 false。
     */
    private fun handleTrustedApps(url: String): Boolean {
        val strArray = context.resources.getStringArray(R.array.TrustedApps)
        for (item in strArray) {
            // 使用 destructuring declaration 使代码更清晰
            val (appId, trustedUrl) = item.split(",", limit = 2)
            if (url.startsWith(trustedUrl)) {
                intentTryApp(appId, url)
                return true
            }
        }
        return false
    }

    /**
     * 尝试通过 Intent 启动一个外部应用。如果应用未安装，则显示一个 Toast 提示。
     *
     * @param appId 要启动的应用的包名。
     * @param msg 要传递给应用的数据（通常是 URL）。
     */
    private fun intentTryApp(appId: String, msg: String) {
        if (isAppAvailable(appId)) {
            val myIntent = Intent(Intent.ACTION_VIEW).apply {
                `package` = appId
                data = Uri.parse(msg)
                putExtra(Intent.EXTRA_TEXT, msg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(myIntent)
        } else {
            Toast.makeText(context, "Required App not Installed", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 检查指定的应用是否已安装在设备上。
     *
     * @param appName 要检查的应用的包名。
     * @return 如果应用已安装则返回 true，否则返回 false。
     */
    private fun isAppAvailable(appName: String): Boolean {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo(appName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 重置注入状态。当前为空实现，可用于未来的扩展。
     */
    fun resetInject() {
        // 可在此处添加重置注入状态的逻辑
    }
}
