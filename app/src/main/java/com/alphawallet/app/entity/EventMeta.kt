package com.alphawallet.app.entity

import java.util.UUID

/**
 * Created by JB on 7/07/2020.
 */
class EventMeta(
    txHash: String,
    val eventName: String,
    val activityCardName: String,
    timeStamp: Long,
    val chainId: Long
) :
    ActivityMeta(timeStamp, txHash) {
    val uID: Long
        get() = UUID.nameUUIDFromBytes((hash + eventName).toByteArray())
            .mostSignificantBits
}
