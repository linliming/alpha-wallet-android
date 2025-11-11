package com.alphawallet.app.entity

/**
 * Created by JB on 7/07/2020.
 */
open class ActivityMeta {
    val timeStamp: Long
    val hash: String?

    constructor(ts: Long, txHash: String?) {
        timeStamp = ts * 1000
        hash = txHash
    }

    constructor(ts: Long, txHash: String?, tokenTransfer: Boolean) {
        timeStamp = ts
        hash = txHash
    }

    val timeStampSeconds: Long
        get() = timeStamp / 1000
}
