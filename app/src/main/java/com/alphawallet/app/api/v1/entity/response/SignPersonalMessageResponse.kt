package com.alphawallet.app.api.v1.entity.response

import android.net.Uri
import android.text.TextUtils
import com.alphawallet.app.api.v1.entity.ApiV1

class SignPersonalMessageResponse(redirectUrl: String, val signature: String) :
    ApiV1Response(redirectUrl) {
    override fun uri(): Uri? {
        val builder =
            Uri.parse(redirectUrl)
                .buildUpon()
                .appendQueryParameter(
                    ApiV1.ResponseParams.CALL,
                    ApiV1.CallType.SIGN_PERSONAL_MESSAGE
                )

        if (!TextUtils.isEmpty(signature)) {
            builder.appendQueryParameter(ApiV1.ResponseParams.SIGNATURE, signature)
        }

        return builder.build()
    }

    override val callType: String
        get() = ApiV1.CallType.SIGN_PERSONAL_MESSAGE
}
