package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.alphawallet.app.R
import com.bumptech.glide.Glide

class BottomSheetToolbarView(ctx: Context, attrs: AttributeSet?) :
    RelativeLayout(ctx, attrs) {
    private val title: TextView
    private val logo: ImageView
    private val closeBtn: ImageView

    init {
        inflate(ctx, R.layout.layout_bottom_sheet_toolbar, this)
        title = findViewById(R.id.title)
        logo = findViewById(R.id.logo)
        closeBtn = findViewById(R.id.image_close)

        getAttrs(ctx, attrs)
    }

    private fun getAttrs(context: Context, attrs: AttributeSet?) {
        val a = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.BottomSheetToolbarView,
            0, 0
        )

        try {
            val titleRes = a.getResourceId(R.styleable.BottomSheetToolbarView_title, R.string.empty)
            title.setText(titleRes)
        } finally {
            a.recycle()
        }
    }

    fun setTitle(titleRes: Int) {
        title.setText(titleRes)
    }

    fun setTitle(titleText: CharSequence?) {
        title.text = titleText
    }

    fun setLogo(context: Context, imageUrl: String?) {
        Glide.with(context)
            .load(imageUrl)
            .circleCrop()
            .into(logo)
    }

    fun setCloseListener(listener: OnClickListener?) {
        closeBtn.setOnClickListener(listener)
    }
}
