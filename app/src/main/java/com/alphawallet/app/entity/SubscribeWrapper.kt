package com.alphawallet.app.entity

import org.web3j.protocol.core.methods.response.Transaction

/**
 * Created by James on 1/02/2018.
 */
interface SubscribeWrapper {
    fun scanReturn(tx: Transaction?)
}
