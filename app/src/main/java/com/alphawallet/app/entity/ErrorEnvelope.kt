package com.alphawallet.app.entity

import com.alphawallet.app.C

class ErrorEnvelope
    @JvmOverloads
    constructor(
         val code: Int,
         val message: String?,
        private val throwable: Throwable? = null,
    ) {
        constructor(message: String?) : this(C.ErrorCode.UNKNOWN, message)
    }
