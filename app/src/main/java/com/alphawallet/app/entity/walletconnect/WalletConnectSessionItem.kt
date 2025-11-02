package com.alphawallet.app.entity.walletconnect

import com.alphawallet.app.repository.entity.RealmWCSession

/**
 * Created by JB on 9/09/2020.
 */
open class WalletConnectSessionItem {

    var name: String = ""

    var url: String = ""

    var icon: String? = ""

    var sessionId: String? = null

    var localSessionId: String? = null

    var chainId: Long = 0

    var expiryTime: Long = 0

    var wcVersion: Int = 0

    constructor(s: RealmWCSession) {
        if (s.getRemotePeerData() != null) {
            name = s.getRemotePeerData()!!.name
            url = s.getRemotePeerData()!!.url
            icon =
                if (s.getRemotePeerData()!!.icons.size > 0) s.getRemotePeerData()!!.icons[0] else null
            expiryTime = convertEpochTime(s.lastUsageTime)
        }
        sessionId = s.getSession()!!.topic
        localSessionId = s.sessionId
        chainId =
            if (s.chainId == 0L) 1 else s.chainId //older sessions without chainId set must be mainnet
        wcVersion = 1
    }

    constructor()

    fun convertEpochTime(inputTime: Long): Long {
        var inputTime = inputTime
        if (inputTime < Y2K) {
            inputTime *= 1000
        }

        return inputTime
    }

    companion object {
        private const val Y2K = 946684800000L
    }
}
