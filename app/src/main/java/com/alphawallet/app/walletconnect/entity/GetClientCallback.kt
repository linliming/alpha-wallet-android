package com.alphawallet.app.walletconnect.entity

import WCClient


/**
 * Created by JB on 6/10/2021.
 */
interface GetClientCallback {
    fun getClient(client: WCClient?)
}
