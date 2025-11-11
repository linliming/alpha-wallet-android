package com.alphawallet.app.walletconnect.entity

import com.alphawallet.app.entity.walletconnect.WCRequest

/**
 * Created by JB on 6/10/2021.
 */
interface WalletConnectCallback {
    fun receiveRequest(request: WCRequest?): Boolean
}
