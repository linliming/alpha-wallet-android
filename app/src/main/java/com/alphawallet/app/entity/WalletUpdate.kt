package com.alphawallet.app.entity

/**
 * Created by James on 8/11/2018.
 * Stormbird in Singapore
 */
class WalletUpdate {
    var lastBlock: Long = -1L
    var wallets: Map<String, Wallet>? = null
}
