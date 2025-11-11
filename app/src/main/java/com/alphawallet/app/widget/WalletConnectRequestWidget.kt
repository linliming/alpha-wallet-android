package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.alphawallet.app.R
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.ui.widget.entity.WalletConnectWidgetCallback
import com.alphawallet.app.walletconnect.entity.WCPeerMeta

class WalletConnectRequestWidget(context: Context?, attributeSet: AttributeSet?) :
    LinearLayout(context, attributeSet) {
    var chainIdOverride: Long = 0
        private set
    private var callback: WalletConnectWidgetCallback? = null

    private val website: DialogInfoItem
    private val network: DialogInfoItem

    init {
        inflate(context, R.layout.item_wallet_connect_request, this)
        website = findViewById<DialogInfoItem>(R.id.info_website)
        network = findViewById<DialogInfoItem>(R.id.info_network)
    }

    fun setupWidget(wcPeerMeta: WCPeerMeta, chainId: Long, callback: WalletConnectWidgetCallback) {
        this.chainIdOverride = chainId
        this.callback = callback

        website.setLabel(getContext().getString(R.string.website_text))
        website.setMessage(wcPeerMeta.url)

        network.setLabel(getContext().getString(R.string.subtitle_network))
        network.setMessage(EthereumNetworkBase.getShortChainName(chainIdOverride))
        network.setMessageTextColor(EthereumNetworkBase.getChainColour(chainIdOverride))
        network.setActionText(getContext().getString(R.string.edit))

        network.setActionListener(OnClickListener { v: View? ->
            callback.openChainSelection()
        })
    }

    fun updateChain(chainIdOverride: Long) {
        this.chainIdOverride = chainIdOverride
        network.setMessage(EthereumNetworkBase.getShortChainName(chainIdOverride))
        network.setMessageTextColor(EthereumNetworkBase.getChainColour(chainIdOverride))
    }
}
