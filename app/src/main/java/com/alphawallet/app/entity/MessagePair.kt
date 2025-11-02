package com.alphawallet.app.entity

import com.alphawallet.token.entity.SignMessageType
import com.alphawallet.token.entity.Signable

/**
 * Created by James on 30/01/2018.
 */
class MessagePair(@JvmField val selection: String, @JvmField val message: String) : Signable {
    override fun getMessage(): String {
        return message
    }

    // TODO: Question to JB: actually, do we add the prefix here?
    override fun getPrehash(): ByteArray {
        return message.toByteArray()
    }

    override fun getOrigin(): String? {
        return null
    }

    // TODO: I actually don't know where to return to … -Weiwu
    override fun getCallbackId(): Long {
        return 0
    }

    override fun getUserMessage(): CharSequence {
        return ""
    }

    override fun getMessageType(): SignMessageType? {
        return null
    }
}
