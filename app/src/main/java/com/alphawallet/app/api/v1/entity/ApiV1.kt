package com.alphawallet.app.api.v1.entity

import java.util.Arrays

object ApiV1 {
    val CONNECT: Method = Method(Path.CONNECT, CallType.CONNECT)
    val SIGN_PERSONAL_MESSAGE: Method =
        Method(Path.SIGN_PERSONAL_MESSAGE, CallType.SIGN_PERSONAL_MESSAGE)

    @JvmField
    val VALID_METHODS: List<Method> = Arrays.asList(
        CONNECT,
        SIGN_PERSONAL_MESSAGE
    )

    object Path {
        const val CONNECT: String = "/wallet/v1/connect"
        const val SIGN_PERSONAL_MESSAGE: String = "/wallet/v1/signpersonalmessage"
    }

    object CallType {
        const val CONNECT: String = "connect"
        const val SIGN_PERSONAL_MESSAGE: String = "signpersonalmessage"
    }

    object RequestParams {
        const val REDIRECT_URL: String = "redirecturl"
        const val METADATA: String = "metadata"
        const val MESSAGE: String = "message"
        const val ADDRESS: String = "address"
    }

    object ResponseParams {
        const val CALL: String = "call"
        const val ADDRESS: String = "address"
        const val SIGNATURE: String = "signature"
    }
}
