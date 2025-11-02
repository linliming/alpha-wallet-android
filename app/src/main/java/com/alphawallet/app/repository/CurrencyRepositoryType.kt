package com.alphawallet.app.repository

import com.alphawallet.app.entity.CurrencyItem
import java.util.ArrayList

interface CurrencyRepositoryType {
    fun getDefaultCurrency(): String

    fun setDefaultCurrency(currency: String)

    fun getCurrencyList(): ArrayList<CurrencyItem>
}
