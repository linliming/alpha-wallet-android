package com.alphawallet.app.repository

import com.alphawallet.app.R
import com.alphawallet.app.entity.CurrencyItem
import java.util.Arrays

class CurrencyRepository(private val preferences: PreferenceRepositoryType) :
    CurrencyRepositoryType {
    override fun setDefaultCurrency(currencyCode: String) {
        val currencyItem = getCurrencyByISO(currencyCode)
        preferences.setDefaultCurrency(currencyItem)
    }

    override fun getDefaultCurrency(): String {
        return preferences.defaultCurrency!!
    }

    override fun getCurrencyList(): ArrayList<CurrencyItem> {
        return ArrayList(Arrays.asList(*CURRENCIES))
    }

    companion object {
        /**
         * Find currency symbol here: https://coinyep.com/en/currencies
         * Find drawables here - https://github.com/Shusshu/android-flags/tree/master/flags/src/main/res/drawable
         */
        val CURRENCIES: Array<CurrencyItem> = arrayOf(
            CurrencyItem("USD", "American Dollar", "$", R.drawable.ic_flags_usa),
            CurrencyItem("EUR", "Euro", "€", R.drawable.ic_flags_euro),
            CurrencyItem("GBP", "British Pound", "£", R.drawable.ic_flags_uk),
            CurrencyItem("AUD", "Australian Dollar", "$", R.drawable.ic_flags_australia),
            CurrencyItem("CNY", "China Yuan Renminbi", "¥", R.drawable.ic_flags_china),
            CurrencyItem("INR", "Indian Rupee", "₹", R.drawable.ic_flags_india),
            CurrencyItem("SGD", "Singapore Dollar", "$", R.drawable.ic_flag_sgd),
            CurrencyItem("JPY", "Japanese Yen", "¥", R.drawable.ic_flags_japan),
            CurrencyItem("KRW", "Korean Won", "₩", R.drawable.ic_flags_korea),
            CurrencyItem("RUB", "Russian Ruble", "₽", R.drawable.ic_flags_russia),
            CurrencyItem("VND", "Vietnamese đồng", "₫", R.drawable.ic_flags_vietnam),
            CurrencyItem("PKR", "Pakistani Rupee", "Rs", R.drawable.ic_flags_pakistan),
            CurrencyItem("MMK", "Myanmar Kyat", "Ks", R.drawable.ic_flags_myanmar),
            CurrencyItem("IDR", "Indonesian Rupiah", "Rp", R.drawable.ic_flags_indonesia),
            CurrencyItem("BDT", "Bangladeshi Taka", "৳", R.drawable.ic_flags_bangladesh)
        )

        @JvmStatic
        fun getCurrencyByISO(currencyIsoCode: String): CurrencyItem? {
            for (c in CURRENCIES) {
                if (currencyIsoCode == c.code) {
                    return c
                }
            }
            return null
        }

        fun getCurrencyByName(currencyName: String): CurrencyItem? {
            for (c in CURRENCIES) {
                if (currencyName == c.name) {
                    return c
                }
            }
            return null
        }

        @JvmStatic
        fun getFlagByISO(currencyIsoCode: String): Int {
            for (c in CURRENCIES) {
                if (currencyIsoCode == c.code) {
                    return c.getFlag()
                }
            }
            return 0
        }
    }
}
