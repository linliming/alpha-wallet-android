package com.alphawallet.app.api.v1.entity.response

import android.net.Uri

abstract class ApiV1Response
    (@kotlin.jvm.JvmField protected val redirectUrl: String) {
    abstract val callType: String?

    abstract fun uri(): Uri?
}
