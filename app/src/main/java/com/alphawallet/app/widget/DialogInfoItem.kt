package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.alphawallet.app.R

class DialogInfoItem(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs) {
    private val label: TextView
    private val message: TextView
    private val actionText: TextView

    init {
        inflate(context, R.layout.item_dialog_info, this)
        label = findViewById(R.id.text_label)
        message = findViewById(R.id.text_message)
        actionText = findViewById(R.id.text_action)
        getAttrs(context, attrs)
    }

    private fun getAttrs(context: Context, attrs: AttributeSet?) {
        val a = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.DialogInfoItem,
            0, 0
        )

        val showAction = a.getBoolean(R.styleable.DialogInfoItem_showActionText, false)
        setLabel(a.getString(R.styleable.DialogInfoItem_title))
        setMessage(a.getString(R.styleable.DialogInfoItem_text))
        actionText.visibility =
            if (showAction) VISIBLE else INVISIBLE
    }

    fun setLabel(label: String?) {
        this.label.text = label
    }

    fun setMessage(msg: String?) {
        message.text = msg
    }

    fun setMessageTextColor(@ColorRes color: Int) {
        message.setTextColor(ContextCompat.getColor(context, color))
    }

    fun setActionText(text: String?) {
        actionText.text = text
    }

    fun setActionListener(listener: OnClickListener?) {
        actionText.setOnClickListener(listener)
    }
}
