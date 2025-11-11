package com.alphawallet.app.web3

import com.alphawallet.app.web3.entity.Web3Transaction

fun interface OnSignTransactionListener {
    fun onSignTransaction(transaction: Web3Transaction?, url: String?)
}
