package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import com.alphawallet.app.R

class TokenInfoCategoryView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private var title: TextView? = null

    constructor(context: Context, titleText: String?) : this(context, null as AttributeSet?) {
        title!!.setText(titleText)
    }

    init {
        inflate(context, R.layout.item_token_info_category, this)
        getAttrs(context, attrs)
    }

    private fun getAttrs(context: Context, attrs: AttributeSet?) {
        val a = context.getTheme().obtainStyledAttributes(
            attrs,
            R.styleable.TokenInfoCategoryView,
            0, 0
        )

        try {
            val titleRes = a.getResourceId(R.styleable.TokenInfoCategoryView_title, R.string.empty)
            title = findViewById<TextView>(R.id.title)
            title!!.setText(titleRes)
        } finally {
            a.recycle()
        }
    }

    fun setTitle(text: String?) {
        title!!.setText(text)
    }
}
