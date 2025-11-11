package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.alphawallet.app.R

class LargeTitleView(context: Context?, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    @JvmField
    val title: TextView?
    @JvmField
    val subtitle: TextView?

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_large_title_view, this, true)

        title = findViewById<TextView?>(R.id.title)
        subtitle = findViewById<TextView?>(R.id.subtitle)
    }
}
