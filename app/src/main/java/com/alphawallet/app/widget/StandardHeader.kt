package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.alphawallet.app.R
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Created by JB on 26/08/2021.
 */
class StandardHeader(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private var headerText: TextView? = null
    private var textControl: TextView? = null
    private var imageControl: ImageView? = null
    private var chainName: ChainName? = null
    private var switchMaterial: SwitchMaterial? = null
    private var separator: View? = null

    init {
        inflate(context, R.layout.item_standard_header, this)
        getAttrs(context, attrs)
    }

    private fun getAttrs(context: Context, attrs: AttributeSet?) {
        val a = context.getTheme().obtainStyledAttributes(
            attrs,
            R.styleable.StandardHeader,
            0, 0
        )

        try {
            val headerId = a.getResourceId(R.styleable.StandardHeader_headerText, R.string.empty)
            val showSwitch = a.getBoolean(R.styleable.StandardHeader_showSwitch, false)
            val showChainName = a.getBoolean(R.styleable.StandardHeader_showChain, false)
            val showTextControl = a.getBoolean(R.styleable.StandardHeader_showTextControl, false)
            val showImageControl = a.getBoolean(R.styleable.StandardHeader_showImageControl, false)
            val controlText = a.getResourceId(R.styleable.StandardHeader_controlText, -1)
            val controlImageRes = a.getResourceId(R.styleable.StandardHeader_controlImageRes, -1)

            headerText = findViewById<TextView>(R.id.text_header)
            chainName = findViewById<ChainName>(R.id.chain_name)
            switchMaterial = findViewById<SwitchMaterial>(R.id.switch_material)
            separator = findViewById<View>(R.id.separator)
            textControl = findViewById<TextView>(R.id.text_control)
            imageControl = findViewById<ImageView>(R.id.image_control)

            headerText!!.setText(headerId)

            switchMaterial!!.setVisibility(if (showSwitch) VISIBLE else GONE)
            chainName!!.setVisibility(if (showChainName) VISIBLE else GONE)

            if (showTextControl) {
                textControl!!.setVisibility(VISIBLE)
                textControl!!.setText(controlText)
            } else {
                textControl!!.setVisibility(GONE)
            }

            if (showImageControl) {
                imageControl!!.setVisibility(VISIBLE)
                imageControl!!.setImageResource(controlImageRes)
            } else {
                imageControl!!.setVisibility(GONE)
            }
        } finally {
            a.recycle()
        }
    }

    fun setText(text: String?) {
        headerText!!.setText(text)
    }

    fun setText(resId: Int) {
        headerText!!.setText(resId)
    }

    fun getChainName(): ChainName {
        return chainName!!
    }

    val switch: SwitchMaterial
        get() = switchMaterial!!

    fun getTextControl(): TextView {
        return textControl!!
    }

    fun getImageControl(): ImageView {
        return imageControl!!
    }

    fun hideSeparator() {
        separator!!.setVisibility(GONE)
    }
}
