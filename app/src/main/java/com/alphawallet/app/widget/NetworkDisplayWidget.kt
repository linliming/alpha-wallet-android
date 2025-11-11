package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import com.alphawallet.app.R
import com.alphawallet.app.repository.EthereumNetworkBase

class NetworkDisplayWidget(context: Context?, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private val networkName: TextView
    private val networkIcon: TokenIcon

    init {
        inflate(context, R.layout.item_network_display, this)
        networkName = findViewById<TextView>(R.id.network_name)
        networkIcon = findViewById<TokenIcon>(R.id.network_icon)
    }

    constructor(context: Context?, networkId: Int) : this(context, null) {
        setNetwork(networkId.toLong())
    }

    fun setNetwork(networkId: Long) {
        networkIcon.bindData(networkId)
        if (EthereumNetworkBase.isChainSupported(networkId)) {
            networkName.setText(EthereumNetworkBase.getShortChainName(networkId))
        } else {
            networkName.setText(
                getContext().getString(
                    R.string.unsupported_network,
                    networkId.toString()
                )
            )
        }
    }
}
