package com.alphawallet.app.repository

object KeyProviderFactory {
    fun get(): KeyProvider = KeyProviderJNIImpl()
}
