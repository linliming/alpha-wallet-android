package com.alphawallet.app.walletconnect.entity

import com.alphawallet.app.entity.CryptoFunctions
import com.alphawallet.token.entity.EthereumTypedMessage
import com.alphawallet.token.entity.Signable

class SignTypedDataRequest(params: String) :
    BaseRequest(params, WCEthereumSignMessage.WCSignType.TYPED_MESSAGE) {
    override val walletAddress: String
        get() = params!![0]

    override val signable: Signable
        get() = EthereumTypedMessage(message, "", 0, CryptoFunctions())

    override fun getSignable(callbackId: Long, origin: String?): Signable? {
        return EthereumTypedMessage(message, origin, callbackId, CryptoFunctions())
    }
}
