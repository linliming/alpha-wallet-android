package com.alphawallet.app.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import com.alphawallet.app.R
import com.alphawallet.app.entity.NetworkInfo

class SwitchChainWidget(context: Context?, attributeSet: AttributeSet?) :
    LinearLayout(context, attributeSet) {
    private val oldChainLogo: TokenIcon
    private val newChainLogo: TokenIcon
    private val oldChainName: ChainName
    private val newChainName: ChainName
    private val textMessage: TextView

    init {
        inflate(context, R.layout.item_switch_chain, this)
        oldChainLogo = findViewById<TokenIcon>(R.id.logo_old)
        newChainLogo = findViewById<TokenIcon>(R.id.logo_new)
        oldChainName = findViewById<ChainName>(R.id.name_old_chain)
        newChainName = findViewById<ChainName>(R.id.name_new_chain)
        textMessage = findViewById<TextView>(R.id.text_message)
    }

    fun setupSwitchChainData(oldNetwork: NetworkInfo, newNetwork: NetworkInfo) {
        var message = getContext().getString(
            R.string.request_change_chain,
            newNetwork.name,
            newNetwork.chainId.toString()
        )
        if (newNetwork.hasRealValue() && !oldNetwork.hasRealValue()) {
            message += "\n" + getContext().getString(R.string.warning_switch_to_main)
        } else if (!newNetwork.hasRealValue() && oldNetwork.hasRealValue()) {
            message += "\n" + getContext().getString(R.string.warning_switching_to_test)
        }

        oldChainLogo.bindData(oldNetwork.chainId)
        newChainLogo.bindData(newNetwork.chainId)
        oldChainName.setChainID(oldNetwork.chainId)
        newChainName.setChainID(newNetwork.chainId)
        textMessage.setText(message)
    }
}
