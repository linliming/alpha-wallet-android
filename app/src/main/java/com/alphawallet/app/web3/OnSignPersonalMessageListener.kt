package com.alphawallet.app.web3

import com.alphawallet.token.entity.EthereumMessage

 interface OnSignPersonalMessageListener {
    fun onSignPersonalMessage(message: EthereumMessage)
}
