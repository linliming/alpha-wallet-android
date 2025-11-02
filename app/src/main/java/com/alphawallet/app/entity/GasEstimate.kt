package com.alphawallet.app.entity

import android.text.TextUtils
import java.math.BigInteger

class GasEstimate {
    @JvmField
    var value: BigInteger
    @JvmField
    var error: String

    constructor(value: BigInteger) {
        this.value = value
        this.error = ""
    }

    constructor(value: BigInteger, error: String) {
        this.value = value
        this.error = error
    }

    fun hasError(): Boolean {
        return !TextUtils.isEmpty(error)
    }
}
