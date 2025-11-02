package com.alphawallet.app.entity

import java.util.UUID

/**
 * Created by James on 3/12/2018.
 * Stormbird in Singapore
 */

/**
 * Cut down version of transaction which is used to populate the Transaction view adapter data.
 * The actual transaction data is retrieved just-in-time from the database when the user looks at the transaction
 * This saves a lot of memory - especially for a contract with a huge amount of transactions.
 */
class
TransactionMeta(
    hash: String,
    timeStamp: Long,
    @JvmField val contractAddress: String,
    @JvmField val chainId: Long,
    blockNumber: String
) :
    ActivityMeta(timeStamp, hash) {
    @JvmField
    val isPending: Boolean = blockNumber == "0" || blockNumber == "-2"

    val uID: Long
        get() = UUID.nameUUIDFromBytes((this.hash + "t").toByteArray())
            .mostSignificantBits
}
