package com.alphawallet.app.api.v1.entity.request

import com.alphawallet.app.api.v1.entity.ApiV1
import com.alphawallet.token.entity.EthereumMessage
import com.alphawallet.token.entity.SignMessageType
import com.alphawallet.token.entity.Signable

class SignPersonalMessageRequest(requestUrl: String) : ApiV1Request(requestUrl) {
    @JvmField
    val address: String? = this.requestUrl!!.queryParameter(ApiV1.RequestParams.ADDRESS)

    val message: String? = this.requestUrl!!.queryParameter(ApiV1.RequestParams.MESSAGE)

    val signable: Signable
        get() = EthereumMessage(
            message,
            metadata!!.name,
            -1,
            SignMessageType.SIGN_PERSONAL_MESSAGE
        )
}
