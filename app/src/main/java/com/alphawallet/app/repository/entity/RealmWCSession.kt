package com.alphawallet.app.repository.entity

import com.alphawallet.app.walletconnect.WCSession
import com.alphawallet.app.walletconnect.entity.WCPeerMeta
import com.google.gson.Gson
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmWCSession : RealmObject() {

    @PrimaryKey
    var sessionId: String? = null
    var peerId: String? = null
    var sessionData: String? = null

    @get:JvmName("getRemotePeerDataRaw")
    @set:JvmName("setRemotePeerDataRaw")
    var remotePeerData: String? = null

    var remotePeerId: String? = null
    var usageCount: Int = 0
    var lastUsageTime: Long = 0L
    var walletAccount: String? = null
    var chainId: Long = 0L

    fun getSession(): WCSession? = Gson().fromJson(sessionData, WCSession::class.java)

    fun getRemotePeerData(): WCPeerMeta? = Gson().fromJson(remotePeerData, WCPeerMeta::class.java)

    fun setRemotePeerData(peerData: String?) {
        remotePeerData = peerData
    }
}
