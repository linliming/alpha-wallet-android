package com.alphawallet.app.web3

import com.alphawallet.token.entity.EthereumTypedMessage


interface OnSignTypedMessageListener {
    fun onSignTypedMessage(message: EthereumTypedMessage)
}
