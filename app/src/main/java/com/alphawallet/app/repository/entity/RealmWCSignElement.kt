package com.alphawallet.app.repository.entity

import io.realm.RealmObject
import java.nio.charset.StandardCharsets

open class RealmWCSignElement : RealmObject() {

    var sessionId: String? = null
    var signMessage: ByteArray? = null
    var signTime: Long = 0
    var signType: String? = null

    fun getSignMessage(): CharSequence {
        val data = signMessage
        return if (data != null) {
            String(data, StandardCharsets.UTF_8)
        } else {
            ""
        }
    }

    fun setSignMessage(msg: CharSequence) {
        signMessage = msg.toString().toByteArray(StandardCharsets.UTF_8)
    }

    fun setSessionId(sessionId: String?) {
        this.sessionId = sessionId
    }

    fun setSignType(signType: String?) {
        this.signType = signType
    }

    fun setSignTime(signTime: Long) {
        this.signTime = signTime
    }
}
