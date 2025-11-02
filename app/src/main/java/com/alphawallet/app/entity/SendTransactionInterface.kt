package com.alphawallet.app.entity

import com.alphawallet.app.web3.entity.Web3Transaction

/**
 * Created by James on 26/01/2019.
 * Stormbird in Singapore
 */
interface SendTransactionInterface {
    fun transactionSuccess(web3Tx: Web3Transaction?, hashData: String?)
    fun transactionError(callbackId: Long, error: Throwable?)
}
