package com.alphawallet.app.repository

import android.text.TextUtils

/**
 * Created by JB on 17/12/2020.
 */
class EventResult
    (val type: String, v: String) {
    @JvmField
    var value: String? = null
    val values: Array<String?>

    init {
        if (!TextUtils.isEmpty(v)) {
            values = v.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            value = values[0]
        } else {
            values = arrayOfNulls(0)
            value = ""
        }
    }
}
