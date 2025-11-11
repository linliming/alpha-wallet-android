package com.alphawallet.app.web3

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.alphawallet.token.entity.EthereumMessage
import com.alphawallet.token.entity.SignMessageType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Created by JB on 13/05/2020.
 */
class TokenScriptCallbackInterface(
    private val webView: WebView?,
    private val onSignPersonalMessageListener: OnSignPersonalMessageListener,
    private val onSetValuesListener: OnSetValuesListener
) {
    @JavascriptInterface
    fun signPersonalMessage(callbackId: Int, data: String?) {
        webView!!.post {
            onSignPersonalMessageListener.onSignPersonalMessage(
                EthereumMessage(
                    data,
                    url, callbackId.toLong(), SignMessageType.SIGN_PERSONAL_MESSAGE
                )
            )
        }
    }

    private val url: String?
        get() = if (webView == null) "" else webView.url

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
