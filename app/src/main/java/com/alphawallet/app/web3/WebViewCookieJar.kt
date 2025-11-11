package com.alphawallet.app.web3

import android.text.TextUtils
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class WebViewCookieJar : CookieJar {
    private var webViewCookieManager: CookieManager? = null

    init {
        try {
            webViewCookieManager = CookieManager.getInstance()
        } catch (ex: Exception) {
            /* Caused by android.content.pm.PackageManager$NameNotFoundException com.google.android.webview */
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (webViewCookieManager != null) {
            val urlString = url.toString()
            for (cookie in cookies) {
                webViewCookieManager.setCookie(urlString, cookie.toString())
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (webViewCookieManager != null) {
            val urlString = url.toString()
            val cookiesString = webViewCookieManager.getCookie(urlString)
            if (cookiesString != null && !TextUtils.isEmpty(cookiesString)) {
                val cookieHeaders =
                    cookiesString.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val cookies: MutableList<Cookie> = ArrayList()
                for (cookieHeader in cookieHeaders) {
                    cookies.add(parse.parse(url, cookieHeader))
                }
                return cookies
            }
        }
        return emptyList()
    }
}
