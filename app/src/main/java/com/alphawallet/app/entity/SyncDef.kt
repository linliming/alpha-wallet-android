package com.alphawallet.app.entity

import java.math.BigInteger

/**
 * Created by JB on 1/04/2022.
 */
class SyncDef
    (
    val eventReadStartBlock: BigInteger,
    val eventReadEndBlock: BigInteger,
    val state: EventSyncState,
    val upwardSync: Boolean
)
