package com.alphawallet.app.web3

import com.alphawallet.app.web3.entity.WalletAddEthereumChainObject

interface OnWalletAddEthereumChainObjectListener {
    fun onWalletAddEthereumChainObject(callbackId: Long, chainObject: WalletAddEthereumChainObject)
}
