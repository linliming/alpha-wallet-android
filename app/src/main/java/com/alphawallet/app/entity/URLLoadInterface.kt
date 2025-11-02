package com.alphawallet.app.entity

interface URLLoadInterface {
    fun onWebpageLoaded(url: String?, title: String?)
    fun onWebpageLoadComplete()
}
