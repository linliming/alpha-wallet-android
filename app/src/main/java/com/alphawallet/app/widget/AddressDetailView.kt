package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.alphawallet.app.R
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.util.Utils

/**
 * AddressDetailView 是一个自定义 View，用于详细显示钱包地址、ENS 名称或请求者 URL。
 * 它可以展开和折叠以显示更多详细信息。
 *
 * 主要功能：
 * - 显示地址摘要和完整地址。
 * - 解析并显示 ENS 名称或 Token 名称。
 * - 支持点击展开/折叠功能。
 * - 可以作为请求者信息的展示组件。
 */
class AddressDetailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    // 使用 val 声明不可变视图引用
    private val textAddressSummary: TextView
    private val textFullAddress: TextView
    private val labelEnsName: TextView
    private val textEnsName: TextView
    private val textMessage: TextView
    private val recipientDetails: ImageView
    private val userAvatar: UserAvatar
    private val layoutDetails: LinearLayout
    private val layoutEnsName: LinearLayout
    private val layoutHolder: LinearLayout

    init {
        // 在 init 块中执行初始化逻辑
        inflate(context, R.layout.item_address_detail, this)
        textAddressSummary = findViewById(R.id.text_recipient)
        textFullAddress = findViewById(R.id.text_recipient_address)
        labelEnsName = findViewById(R.id.label_ens_name)
        textEnsName = findViewById(R.id.text_ens_name)
        textMessage = findViewById(R.id.message)
        recipientDetails = findViewById(R.id.image_more)
        userAvatar = findViewById(R.id.blockie)
        layoutDetails = findViewById(R.id.layout_detail)
        layoutEnsName = findViewById(R.id.layout_ens_name)
        layoutHolder = findViewById(R.id.layout_holder)
        getAttrs(context, attrs)
    }

    /**
     * 解析并应用在 XML 布局文件中定义的自定义属性。
     * @param context 上下文。
     * @param attrs 属性集。
     */
    private fun getAttrs(context: Context, attrs: AttributeSet?) {
        val typedArray = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.InputView,
            0, 0
        )

        try { // 确保 typedArray 被回收
            val titleTextView = findViewById<TextView>(R.id.text_address_title)
            titleTextView.setText(typedArray.getResourceId(R.styleable.InputView_label, R.string.recipient))
        } finally {
            typedArray.recycle()
        }
    }

    /**
     * 在地址摘要旁边添加一条消息，并可以选择性地添加一个图标。
     * @param message 要显示的消息文本。
     * @param drawableRes 消息文本左侧的图标资源 ID，如果为 0 或负数则不设置。
     */
    fun addMessage(message: String, drawableRes: Int) {
        textMessage.text = message
        textMessage.isVisible = true // 使用 KTX 扩展属性
        if (drawableRes > 0) {
            // 设置文本旁边的图标
            textAddressSummary.setCompoundDrawablesWithIntrinsicBounds(drawableRes, 0, 0, 0)
            textMessage.setCompoundDrawablesWithIntrinsicBounds(drawableRes, 0, 0, 0)
        }
    }

    /**
     * 设置并显示地址和 ENS 名称的详细信息。
     * @param address 钱包地址。
     * @param ensName 关联的 ENS 名称（可以为空）。
     * @param destToken 目标 Token，用于在没有 ENS 名称时显示 Token 信息（可以为 null）。
     */
    fun setupAddress(address: String, ensName: String?, destToken: Token?) {
        val hasEns = !ensName.isNullOrEmpty()
        // 使用字符串模板构建摘要文本
        textAddressSummary.text = if (hasEns) "$ensName | ${Utils.formatAddress(address)}" else Utils.formatAddress(address)
        // 绑定地址以查找 ENS 头像
        userAvatar.bind(Wallet(address)) { /* NOP, here to enable lookup of ENS avatar */ }
        textFullAddress.text = address

        // 使用 when 表达式简化逻辑
        when {
            ensName.isNullOrEmpty() -> {
                if (destToken != null && !destToken.isEthereum()) {
                    labelEnsName.isVisible = true
                    layoutEnsName.isVisible = true
                    labelEnsName.setText(R.string.token_text)
                    textEnsName.text = destToken.getFullName()
                } else {
                    labelEnsName.isVisible = false
                    layoutEnsName.isVisible = false
                }
            }

            else -> {
                labelEnsName.isVisible = true
                layoutEnsName.isVisible = true
                textEnsName.text = ensName
            }
        }

        // 设置点击监听器以展开/折叠详情
        layoutHolder.setOnClickListener {
            // 根据当前可见性切换状态
            val isDetailsVisible = layoutDetails.isVisible
            layoutDetails.isVisible = !isDetailsVisible
            textAddressSummary.isVisible = isDetailsVisible
            recipientDetails.setImageResource(
                if (isDetailsVisible) R.drawable.ic_expand_more else R.drawable.ic_expand_less_black
            )
        }
    }

    /**
     * 将此视图设置为显示一个请求者（通常是 DApp）的 URL。
     * @param requesterUrl 请求者的 URL。
     */
    fun setupRequester(requesterUrl: String) {
        isVisible = true
        recipientDetails.isVisible = false
        // 缩短 URL 以适应 UI
        textAddressSummary.text = abbreviateURL(requesterUrl)
    }

    /**
     * 缩短过长的 URL。如果 URL 长度超过32个字符，会尝试在第20个字符后找到第一个“/”并截断。
     * @param inputURL 原始 URL。
     * @return 缩短后的 URL。
     */
    private fun abbreviateURL(inputURL: String): String {
        return if (inputURL.length > 32) {
            val index = inputURL.indexOf('/', 20)
            if (index >= 0) inputURL.substring(0, index) else inputURL
        } else {
            inputURL
        }
    }
}
