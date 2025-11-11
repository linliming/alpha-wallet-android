package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import com.alphawallet.app.R

class SimpleSheetWidget(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private val label: TextView
    private val value: TextView
    private val caption: TextView

    init {
        inflate(context, R.layout.item_simple_widget, this)
        label = findViewById<TextView>(R.id.label)
        value = findViewById<TextView>(R.id.value)
        caption = findViewById<TextView>(R.id.caption)

        setupAttrs(context, attrs)
    }

    constructor(context: Context, labelRes: Int) : this(context, null as AttributeSet?) {
        label.setText(labelRes)
    }

    constructor(context: Context, labelRes: String?) : this(context, null as AttributeSet?) {
        label.setText(labelRes)
    }

    private fun setupAttrs(context: Context, attrs: AttributeSet?) {
        val a = context.getTheme().obtainStyledAttributes(
            attrs,
            R.styleable.SimpleSheetWidget,
            0, 0
        )

        try {
            val labelRes = a.getResourceId(R.styleable.SimpleSheetWidget_swLabelRes, R.string.empty)
            val valueRes = a.getResourceId(R.styleable.SimpleSheetWidget_swValueRes, R.string.empty)
            val captionRes =
                a.getResourceId(R.styleable.SimpleSheetWidget_swCaptionRes, R.string.empty)

            label.setText(labelRes)
            value.setText(valueRes)
            if (captionRes == R.string.empty) {
                caption.setVisibility(GONE)
            } else {
                caption.setText(labelRes)
            }
        } finally {
            a.recycle()
        }
    }

    fun setLabel(labelText: String?) {
        label.setText(labelText)
    }

    fun setValue(valueText: String?) {
        value.setText(valueText)
    }

    fun setCaption(captionText: String?) {
        caption.setVisibility(VISIBLE)
        caption.setText(captionText)
    }
}
