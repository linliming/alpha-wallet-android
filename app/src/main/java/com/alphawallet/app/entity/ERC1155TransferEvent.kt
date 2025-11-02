package com.alphawallet.app.entity

import java.math.BigInteger

/**
 * Created by JB on 14/07/2021.
 */
class ERC1155TransferEvent(
    val blockNumber: BigInteger,
    val to: String,
    val from: String,
    val tokenId: BigInteger,
    value: BigInteger,
    val isReceive: Boolean
) :
    Comparable<ERC1155TransferEvent> {
    val value: BigInteger = if (isReceive) value else value.negate()

    override fun compareTo(other: ERC1155TransferEvent): Int {
        return blockNumber.compareTo(other.blockNumber)
    }
}
