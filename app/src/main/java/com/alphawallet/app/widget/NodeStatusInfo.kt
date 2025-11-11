package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.alphawallet.app.R
import timber.log.Timber

class NodeStatusInfo(var context: Context, attr: AttributeSet?) : LinearLayout(
    context, attr
) {
    var icon: ImageView
    var message: TextView

    init {
        inflate(context, R.layout.item_node_status_info, this)

        icon = findViewById<ImageView>(R.id.image)
        message = findViewById<TextView>(R.id.text)

        setupAttrs(context, attr)
    }

    private fun setupAttrs(context: Context, attrs: AttributeSet?) {
        val a = context.getTheme().obtainStyledAttributes(
            attrs,
            R.styleable.NodeStatusInfo,
            0, 0
        )

        try {
            setIcon(a.getResourceId(R.styleable.NodeStatusInfo_android_icon, R.drawable.ic_help))
            setMessage(a.getString(R.styleable.NodeStatusInfo_android_text))
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    fun setMessage(msg: String?) {
        message.setText(context.getString(R.string.node_status_label, msg))
    }

    fun setIcon(@DrawableRes res: Int) {
        icon.setImageDrawable(ContextCompat.getDrawable(context, res))
    }
}
