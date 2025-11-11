package com.alphawallet.app.widget

import android.webkit.WebBackForwardList

interface AddressBarListener {
    fun onLoad(urlText: String): Boolean

    fun onClear()

    fun loadNext(): WebBackForwardList?

    fun loadPrevious(): WebBackForwardList?

    fun onHomePagePressed(): WebBackForwardList?
}
