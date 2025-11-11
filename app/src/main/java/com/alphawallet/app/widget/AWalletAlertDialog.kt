package com.alphawallet.app.widget

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import com.alphawallet.app.R

class AWalletAlertDialog @JvmOverloads constructor(
    context: Context,
    iconRes: Int = NONE
) : Dialog(context) {

    enum class TextStyle {
        CENTERED,
        LEFT
    }

    private val iconView: ImageView
    private val titleText: TextView
    private val messageText: TextView
    private val primaryButton: Button
    private val secondaryButton: Button
    private val progressBar: ProgressBar
    private val viewContainer: RelativeLayout
    private val dialogLayout: RelativeLayout

    init {
        setContentView(R.layout.dialog_awallet_alert)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setCanceledOnTouchOutside(true)

        iconView = findViewById(R.id.dialog_icon)
        titleText = findViewById(R.id.dialog_main_text)
        messageText = findViewById(R.id.dialog_sub_text)
        primaryButton = findViewById(R.id.dialog_button1)
        secondaryButton = findViewById(R.id.dialog_button2)
        progressBar = findViewById(R.id.dialog_progress)
        viewContainer = findViewById(R.id.dialog_view)
        dialogLayout = findViewById(R.id.layout_dialog_container)

        primaryButton.setOnClickListener { dismiss() }
        secondaryButton.setOnClickListener { dismiss() }

        setIcon(iconRes)
    }

    companion object {
        const val NONE = 0
         val SUCCESS = R.drawable.ic_redeemed
         val ERROR = R.drawable.ic_error
         val NO_SCREENSHOT = R.drawable.ic_no_screenshot
         val WARNING = R.drawable.ic_warning
    }

    /**
     * 调整对话框的边距与内边距，使其横向更宽。
     */
    fun makeWide() {
        val scale = context.resources.displayMetrics.density
        val padding = (15 * scale + 0.5f).toInt()
        val margin = (10 * scale + 0.5f).toInt()
        dialogLayout.setPadding(padding, padding, padding, padding)
        (dialogLayout.layoutParams as? ViewGroup.MarginLayoutParams)?.let { layoutParams ->
            layoutParams.setMargins(margin, margin, margin, margin)
            dialogLayout.layoutParams = layoutParams
        }
        dialogLayout.requestLayout()
    }

    /**
     * 切换进度模式，隐藏按钮与说明文字。
     */
    fun setProgressMode() {
        iconView.visibility = View.GONE
        messageText.visibility = View.GONE
        primaryButton.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
    }

    /**
     * 设置主标题（资源 ID）。
     */
    override fun setTitle(@StringRes resId: Int) {
        titleText.visibility = View.VISIBLE
        titleText.text = context.getString(resId)
    }

    /**
     * 设置主标题（文本）。
     */
    @MainThread
    override fun setTitle(message: CharSequence?) {
        titleText.visibility = View.VISIBLE
        titleText.text = message
    }

    /**
     * 配置主按钮文案与点击回调。
     */
    fun setButton(@StringRes resId: Int, listener: View.OnClickListener?) {
        setButtonText(resId)
        setButtonListener(listener)
    }

    /**
     * 设置主按钮文案。
     */
    fun setButtonText(@StringRes resId: Int) {
        primaryButton.visibility = View.VISIBLE
        primaryButton.text = context.getString(resId)
    }

    /**
     * 设置主按钮点击监听。
     */
    fun setButtonListener(listener: View.OnClickListener?) {
        primaryButton.setOnClickListener(listener ?: View.OnClickListener { dismiss() })
    }

    /**
     * 配置副按钮文案与点击回调。
     */
    fun setSecondaryButton(@StringRes resId: Int, listener: View.OnClickListener?) {
        setSecondaryButtonText(resId)
        setSecondaryButtonListener(listener)
    }

    /**
     * 设置副按钮文案。
     */
    fun setSecondaryButtonText(@StringRes resId: Int) {
        secondaryButton.visibility = View.VISIBLE
        secondaryButton.text = context.getString(resId)
    }

    /**
     * 设置副按钮点击监听。
     */
    fun setSecondaryButtonListener(listener: View.OnClickListener?) {
        secondaryButton.setOnClickListener(listener ?: View.OnClickListener { dismiss() })
    }

    /**
     * 设置提示信息（资源 ID）。
     */
    fun setMessage(@StringRes resId: Int) {
        messageText.visibility = View.VISIBLE
        messageText.text = context.getString(resId)
    }

    /**
     * 设置提示信息（字符序列）。
     */
    fun setMessage(message: CharSequence?) {
        messageText.visibility = View.VISIBLE
        messageText.text = message
    }

    /**
     * 配置图标资源，若传入 NONE 则隐藏。
     */
    fun setIcon(resId: Int) {
        if (resId == NONE) {
            iconView.visibility = View.GONE
        } else {
            iconView.visibility = View.VISIBLE
            iconView.setImageResource(resId)
        }
    }

    /**
     * 向自定义容器中添加额外视图。
     */
    fun setView(view: View) {
        viewContainer.addView(view)
    }

    /**
     * 调整提示文字的对齐方式。
     */
    fun setTextStyle(style: TextStyle) {
        messageText.gravity = when (style) {
            TextStyle.CENTERED -> Gravity.CENTER_HORIZONTAL
            TextStyle.LEFT -> Gravity.START
        }
    }
}
