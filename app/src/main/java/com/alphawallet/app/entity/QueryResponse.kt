package com.alphawallet.app.entity

/**
 * Created by JB on 5/11/2022.
 */
class QueryResponse
    (val code: Int, @JvmField val body: String) {
    val isSuccessful: Boolean
        get() = code >= 200 && code <= 299
}


