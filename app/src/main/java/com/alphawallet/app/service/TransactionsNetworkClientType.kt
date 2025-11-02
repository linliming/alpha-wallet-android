package com.alphawallet.app.service

import com.alphawallet.app.entity.NetworkInfo
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.TransactionMeta
import com.alphawallet.app.entity.transactionAPI.TransferFetchType
import com.alphawallet.app.entity.transactions.TransferEvent
interface TransactionsNetworkClientType {
    suspend fun storeNewTransactions(
        svs: TokensService,
        networkInfo: NetworkInfo,
        tokenAddress: String,
        lastBlock: Long,
    ): Array<Transaction?>

    suspend fun fetchMoreTransactions(
        svs: TokensService,
        network: NetworkInfo,
        lastTxTime: Long,
    ): Array<TransactionMeta?>

    suspend fun readTransfers(
        currentAddress: String,
        networkByChain: NetworkInfo,
        tokensService: TokensService,
        tfType: TransferFetchType,
    ): Map<String, List<TransferEvent>>

    fun checkRequiresAuxReset(walletAddr: String?)
}
