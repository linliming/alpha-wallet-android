package com.alphawallet.app.web3

import com.alphawallet.app.web3.entity.WalletAddEthereumChainObject

/**
 * Created by JB on 15/01/2022.
 */
interface OnWalletActionListener {
    fun onRequestAccounts(callbackId: Long)
    fun onWalletSwitchEthereumChain(callbackId: Long, chainObj: WalletAddEthereumChainObject)
}
