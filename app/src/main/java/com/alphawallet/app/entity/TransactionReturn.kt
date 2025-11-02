package com.alphawallet.app.entity

import com.alphawallet.app.web3.entity.Web3Transaction

/**
 * Created by James on 26/01/2019.
 * Stormbird in Singapore
 */
class TransactionReturn {
    @JvmField
    val hash: String?
    @JvmField
    val tx: Web3Transaction
    @JvmField
    val throwable: Throwable?

    constructor(hash: String?, tx: Web3Transaction) {
        this.hash = hash
        this.tx = tx
        this.throwable = null
    }

    constructor(throwable: Throwable?, tx: Web3Transaction) {
        this.hash = null
        this.tx = tx
        this.throwable = throwable
    }

    val displayData: String
        get() = hash?.substring(0, 66) ?: ""
}
