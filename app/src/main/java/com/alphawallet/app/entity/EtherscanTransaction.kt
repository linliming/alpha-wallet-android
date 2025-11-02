package com.alphawallet.app.entity

/**
 * Represents an Etherscan transaction record and provides helpers to map it into AlphaWallet's [Transaction] model.
 */
class EtherscanTransaction {
    var blockNumber: String? = null
    var timeStamp: Long = 0
    var hash: String? = null
    var nonce: Int = 0
    var blockHash: String? = null
    var transactionIndex: Int = 0
    var from: String? = null
    var to: String? = null
    var value: String? = null
    var gas: String? = null
    var gasPrice: String? = null
    var isError: String? = null
    var txreceipt_status: String? = null
    var input: String? = null
    var contractAddress: String? = null
    var cumulativeGasUsed: String? = null
    var gasUsed: String? = null
    var confirmations: Int = 0
    var functionName: String? = null

    /**
     * Converts this raw record into a wallet [Transaction], filtering out entries that don't involve the given wallet.
     */
    fun createTransaction(walletAddress: String?, chainId: Long): Transaction? {
        val transaction = Transaction(
            hash.orEmpty(),
            isError.orEmpty(),
            blockNumber.orEmpty(),
            timeStamp,
            nonce,
            from.orEmpty(),
            to.orEmpty(),
            value.orEmpty(),
            gas.orEmpty(),
            gasPrice.orEmpty(),
            input.orEmpty(),
            gasUsed.orEmpty(),
            chainId,
            contractAddress,
            functionName.orEmpty(),
        )

        if (walletAddress != null && !transaction.getWalletInvolvedInTransaction(walletAddress)) {
            return null
        }

        return transaction
    }

    /** Convenience accessor for the transaction hash. */
    fun getHash(): String? = hash

    constructor()

    /**
     * Builds an Etherscan-compatible record from a Covalent/Transaction pair when the original source is Covalent.
     */
    constructor(transaction: CovalentTransaction, tx: Transaction) {
        blockNumber = tx.blockNumber
        timeStamp = tx.timeStamp
        hash = transaction.tx_hash
        nonce = tx.nonce
        blockHash = ""
        transactionIndex = transaction.tx_offset
        from = transaction.from_address
        to = transaction.to_address
        value = transaction.value
        gas = transaction.gas_spent
        gasPrice = transaction.gas_price
        isError = if (transaction.successful) "0" else "1"
        txreceipt_status = ""
        input = tx.input
        contractAddress = ""
        cumulativeGasUsed = ""
        gasUsed = transaction.gas_spent
        confirmations = 0

        if (tx.isConstructor) {
            to = tx.to
            contractAddress = tx.to
        }
    }
}
