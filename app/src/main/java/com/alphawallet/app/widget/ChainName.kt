package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.alphawallet.app.R
import com.alphawallet.app.repository.EthereumNetworkBase

/**
 * Created by JB on 3/12/2020.
 */
class ChainName(context: Context, attrs: AttributeSet?) :
    LinearLayout(context, attrs) {
    private val chainName: TextView
    private var invertNameColour = false

    init {
        inflate(context, R.layout.item_chain_name, this)
        chainName = findViewById(R.id._text_chain_name)
        getAttrs(context, attrs)
    }

    fun setChainID(chainId: Long) {
        if (EthereumNetworkBase.isChainSupported(chainId)) {
            visibility = VISIBLE
            chainName.text =
                EthereumNetworkBase.getShortChainName(chainId)

            if (invertNameColour) {
                chainName.setTextColor(context.getColor(EthereumNetworkBase.getChainColour(chainId)))
                chainName.setBackgroundResource(R.drawable.background_chain_inverse)
            } else {
                chainName.setTextColor(context.getColor(R.color.white))
                chainName.background.setTint(
                    ContextCompat.getColor(
                        context,
                        EthereumNetworkBase.getChainColour(chainId)
                    )
                )
            }
        } else {
            visibility = GONE
        }
    }

    private fun getAttrs(context: Context, attrs: AttributeSet?) {
        val a = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.InputView,
            0, 0
        )

        try {
            val fontSize = a.getInteger(R.styleable.InputView_font_size, 12)
            chainName.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat())
            invertNameColour = a.getBoolean(R.styleable.InputView_invert, false)
        } finally {
            a.recycle()
        }
    }
}
