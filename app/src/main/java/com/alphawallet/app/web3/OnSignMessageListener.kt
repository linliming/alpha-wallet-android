package com.alphawallet.app.web3

import com.alphawallet.token.entity.EthereumMessage

interface OnSignMessageListener {
    fun onSignMessage(message: EthereumMessage)
}
