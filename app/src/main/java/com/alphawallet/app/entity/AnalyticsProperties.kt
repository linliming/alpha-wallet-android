package com.alphawallet.app.entity

import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

class AnalyticsProperties {
    private val props = JSONObject()

    fun put(
        key: String,
        value: Any?,
    ) {
        if (value == null) return

        try {
            props.put(key, value)
        } catch (e: JSONException) {
            Timber.e(e)
        }
    }

    fun get(): JSONObject = props
}
