package com.alphawallet.app.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Base64
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.alphawallet.app.R
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.util.Utils
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.target.Target
import java.nio.charset.StandardCharsets

/**
 * AddressIcon 是一个自定义 View，用于显示代币或地址的图标。
 *
 * 它遵循一个加载策略：
 * 1. 优先尝试从 AlphaWallet 的图标库加载一个预定义的图标 (通常是 PNG)。
 * 2. 如果加载失败，则尝试从 `primaryURI` (通常是一个 SVG 图像) 加载。
 * 3. 如果以上都不可用，它会显示一个基于代币符号的文本图标。
 */
class AddressIcon @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    // 使用 val 和 by lazy 进行视图的延迟初始化
    private val icon: ImageView by lazy { findViewById(R.id.icon) }
    private val textIcon: TextView by lazy { findViewById(R.id.text_icon) }
    private val svgIcon: WebView by lazy { findViewById(R.id.web_view_for_svg) }
    private val svgMask: ImageView by lazy { findViewById(R.id.web_mask_circle) }

    private var symbol: String = ""
    private var primaryURI: String = ""
    private var address: String = ""
    private var chainId: Long = 0
    private var currentRq: Request? = null

    // Handler 用于在主线程执行操作
    private val handler = Handler(Looper.getMainLooper())

    init {
        // 在 init 块中执行初始化逻辑
        inflate(context, R.layout.item_address_icon, this)
        findViewById<View>(R.id.circle).isVisible = false
    }

    /**
     * 清除任何正在进行的 Glide 加载请求和 Handler 中的回调，
     * 以防止在 View 被复用或销毁时出现不必要的操作或内存泄漏。
     */
    fun clearLoad() {
        handler.removeCallbacksAndMessages(null)
        // 只有当请求仍在运行时才清除它
        if (currentRq?.isRunning == true) {
            currentRq?.clear()
        }
        currentRq = null
    }

    /**
     * 绑定数据并开始加载图标的过程。
     *
     * @param primaryURI 主要的图标 URI，通常用于加载 SVG。
     * @param chainId 代币所在的链 ID。
     * @param tokenAddress 代币的合约地址。
     * @param symbol 代币的符号，用于生成文本图标。
     */
    fun bindData(primaryURI: String, chainId: Long, tokenAddress: String, symbol: String) {
        // 在重新绑定数据前清除旧的加载任务
        clearLoad()

        this.symbol = symbol
        this.chainId = chainId
        this.primaryURI = primaryURI
        this.address = tokenAddress

        // 初始化视图状态
        setupTextIcon()
        svgIcon.isVisible = false
        svgMask.isVisible = false

        if (tokenAddress.isEmpty()) {
            loadFromPrimaryURI()
        } else {
            displayTokenIcon()
        }
    }

    /**
     * 优先尝试使用 Glide 从 AlphaWallet 的图标库加载代币图标。
     * 如果加载失败，RequestListener 会触发回退逻辑。
     */
    private fun displayTokenIcon() {
        currentRq = Glide.with(this)
            .load(Utils.getTokenImageUrl(address))
            .placeholder(R.drawable.ic_token_eth)
            .circleCrop()
            .listener(requestListener)
            .into(DrawableImageViewTarget(icon))
            .request
    }

    /**
     * 从 `primaryURI` 加载图标，这通常是一个 SVG。
     * 如果 URI 不为空，则使用 WebView 来渲染它。
     */
    private fun loadFromPrimaryURI() {
        if (primaryURI.isNotEmpty()) {
            setWebView(Utils.parseIPFS(primaryURI))
        }
    }

    /**
     * 配置并使用 WebView 来加载和显示一个 SVG 图像。
     * SVG URL 被嵌入到一个本地 HTML 模板中，并通过 Base64 编码加载到 WebView。
     *
     * @param imageUrl SVG 图像的 URL。
     */
    private fun setWebView(imageUrl: String) {
        // 从 raw 资源加载 HTML 模板并替换 URL 占位符
        val loader = Utils.loadFile(context, R.raw.token_graphic).replace("[URL]", imageUrl)
        val base64 = Base64.encodeToString(loader.toByteArray(StandardCharsets.UTF_8), Base64.DEFAULT)

        // 切换视图可见性以显示 WebView
        textIcon.isVisible = false
        icon.isVisible = false
        svgIcon.isVisible = true
        svgMask.isVisible = true
        svgIcon.loadData(base64, "text/html; charset=utf-8", "base64")
    }

    /**
     * 设置基于代币符号的文本图标作为备用方案。
     * 它会根据链 ID 设置背景颜色，并显示一个缩写的符号文本。
     */
    private fun setupTextIcon() {
        textIcon.isVisible = true
        textIcon.backgroundTintList = ContextCompat.getColorStateList(context, EthereumNetworkBase.getChainColour(chainId))
        textIcon.text = Utils.getIconisedText(symbol)
    }

    /**
     * Glide 的请求监听器，用于处理加载成功或失败的事件。
     */
    private val requestListener = object : RequestListener<Drawable> {
        /**
         * 当 Glide 加载失败时调用。
         * 在主线程上触发 `loadFromPrimaryURI` 作为回退机制。
         */
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean
        ): Boolean {
            // 使用 handler.post 确保回退逻辑在主线程上执行
            handler.post { loadFromPrimaryURI() }
            return false // 返回 false 让 Glide 继续处理占位符等
        }

        /**
         * 当 Glide 成功加载资源时调用。
         * 更新视图可见性以显示加载的图标。
         */
        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>,
            dataSource: DataSource,
            isFirstResource: Boolean
        ): Boolean {
            textIcon.isVisible = false
            icon.isVisible = true
            icon.setImageDrawable(resource)
            findViewById<View>(R.id.circle).isVisible = true
            return false // 返回 false 让 Glide 将资源设置到 target
        }
    }

    /**
     * 清空图标并取消任何正在进行的加载，使视图恢复到空白状态。
     */
    fun blankIcon() {
        clearLoad()
        icon.setImageDrawable(null)
    }
}