package com.alphawallet.app.repository

import com.alphawallet.app.entity.lifi.SwapProvider

interface SwapRepositoryType {
    fun getProviders(): List<SwapProvider>
}
