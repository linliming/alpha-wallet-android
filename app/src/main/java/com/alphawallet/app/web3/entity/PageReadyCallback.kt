package com.alphawallet.app.web3.entity

import android.webkit.WebView

/**
 * Created by James on 3/04/2019.
 * Stormbird in Singapore
 */
interface PageReadyCallback {
    fun onPageLoaded(view: WebView?)
    fun onPageRendered(view: WebView?)
    fun overridePageLoad(view: WebView?, url: String?): Boolean {
        return true
    } //by default, don't allow TokenScript to access any URL
}
