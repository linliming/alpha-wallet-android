package com.alphawallet.app.walletconnect.entity

import com.alphawallet.token.entity.EthereumMessage
import com.alphawallet.token.entity.SignMessageType
import com.alphawallet.token.entity.Signable

class SignPersonalMessageRequest(params: String) :
    BaseRequest(params, WCEthereumSignMessage.WCSignType.PERSONAL_MESSAGE) {
    override val signable: Signable
        get() = EthereumMessage(message, "", 0, SignMessageType.SIGN_PERSONAL_MESSAGE)

    override fun getSignable(callbackId: Long, origin: String?): Signable? {
        return EthereumMessage(message, origin, callbackId, SignMessageType.SIGN_PERSONAL_MESSAGE)
    }

    override val walletAddress: String
        get() = params!![1]
}
