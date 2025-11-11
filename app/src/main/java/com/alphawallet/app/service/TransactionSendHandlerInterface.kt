package com.alphawallet.app.service

import com.alphawallet.app.entity.TransactionReturn
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.hardware.SignatureFromKey

/**
 * Created by JB on 2/02/2023.
 */
interface TransactionSendHandlerInterface {
    fun transactionFinalised(txData: TransactionReturn)

    fun transactionError(txError: TransactionReturn)

    fun transactionSigned(sigData: SignatureFromKey?, w3Tx: Web3Transaction?) {
    } //Not always required, only WalletConnect
}
