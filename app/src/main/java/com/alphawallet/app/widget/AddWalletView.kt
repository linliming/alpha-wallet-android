package com.alphawallet.app.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.core.view.isVisible
import com.alphawallet.app.R

/**
 * AddWalletView 是一个自定义 View，提供添加新钱包的多种选项，
 * 例如创建新钱包、导入现有钱包、观察钱包或使用硬件卡。
 * 它通过一系列回调接口与外部组件通信。
 */
class AddWalletView @JvmOverloads constructor(
    context: Context,
    @LayoutRes layoutId: Int = R.layout.layout_dialog_add_account
) : FrameLayout(context), View.OnClickListener {

    // 使用 lateinit var 声明回调监听器，因为它们将在之后被设置
    lateinit var onNewWalletClickListener: OnNewWalletClickListener
    lateinit var onImportWalletClickListener: OnImportWalletClickListener
    lateinit var onWatchWalletClickListener: OnWatchWalletClickListener
    lateinit var onCloseActionListener: OnCloseActionListener
    lateinit var onHardwareCardClickListener: OnHardwareCardActionListener

    init {
        // 在 init 块中执行视图初始化
        LayoutInflater.from(context).inflate(layoutId, this, true)
        findViewById<View>(R.id.new_account_action).setOnClickListener(this)
        findViewById<View>(R.id.import_account_action).setOnClickListener(this)
        findViewById<View>(R.id.watch_account_action).setOnClickListener(this)
        findViewById<View>(R.id.hardware_card).setOnClickListener(this)
    }

    /**
     * 处理此视图中所有子视图的点击事件。
     * @param view 被点击的视图。
     */
    override fun onClick(view: View) {
        // 使用 when 表达式替代 if-else if 链，代码更简洁、可读性更高
        when (view.id) {
            R.id.close_action -> if (::onCloseActionListener.isInitialized) {
                onCloseActionListener.onClose(view)
            }

            R.id.new_account_action -> if (::onNewWalletClickListener.isInitialized) {
                onNewWalletClickListener.onNewWallet(view)
            }

            R.id.import_account_action -> if (::onImportWalletClickListener.isInitialized) {
                onImportWalletClickListener.onImportWallet(view)
            }

            R.id.watch_account_action -> if (::onWatchWalletClickListener.isInitialized) {
                onWatchWalletClickListener.onWatchWallet(view)
            }

            R.id.hardware_card -> if (::onHardwareCardClickListener.isInitialized) {
                onHardwareCardClickListener.detectCard(view)
            }
        }
    }

    /**
     * 根据是否为存根版本（Stub）来设置硬件卡选项的可见性。
     * @param isStub 如果为 true，则隐藏硬件卡选项；否则显示。
     */
    fun setHardwareActive(isStub: Boolean) {
        // 使用 Android KTX 的 isVisible 扩展属性，代码更简洁
        findViewById<View>(R.id.hardware_card).isVisible = !isStub
    }

    // 使用 fun interface 使接口声明更简洁（SAM 转换）
    fun interface OnNewWalletClickListener {
        fun onNewWallet(view: View)
    }

    fun interface OnImportWalletClickListener {
        fun onImportWallet(view: View)
    }

    fun interface OnWatchWalletClickListener {
        fun onWatchWallet(view: View)
    }

    fun interface OnCloseActionListener {
        fun onClose(view: View)
    }

    fun interface OnHardwareCardActionListener {
        fun detectCard(view: View)
    }
}
