package com.alphawallet.app.web3

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Created by JB on 1/05/2020.
 */
class ValueCallbackJSInterface
    (
    private val webView: WebView,
    private val onSetValuesListener: OnSetValuesListener
) {
    @JavascriptInterface
    fun setValues(jsonValuesFromTokenView: String?) {
        var updates = try {
            Gson().fromJson<Map<String?, String?>>(
                jsonValuesFromTokenView,
                object :
                    TypeToken<HashMap<String?, String?>?>() {}.type
            )
        } catch (e: Exception) {
            HashMap()
        }

        onSetValuesListener.setValues(updates)
    }
}
