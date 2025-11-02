package com.alphawallet.app.api.v1.entity.response

import android.net.Uri
import android.text.TextUtils
import com.alphawallet.app.api.v1.entity.ApiV1

class ConnectResponse(redirectUrl: String, val address: String) : ApiV1Response(redirectUrl) {
    override fun uri(): Uri? {
        val builder =
            Uri.parse(redirectUrl)
                .buildUpon()
                .appendQueryParameter(ApiV1.ResponseParams.CALL, ApiV1.CallType.CONNECT)

        if (!TextUtils.isEmpty(address)) {
            builder.appendQueryParameter(ApiV1.ResponseParams.ADDRESS, address)
        }

        return builder.build()
    }

    override val callType: String
        get() = ApiV1.CallType.CONNECT
}
