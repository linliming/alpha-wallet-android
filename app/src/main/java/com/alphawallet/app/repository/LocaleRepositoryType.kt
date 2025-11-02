package com.alphawallet.app.repository

import android.content.Context
import com.alphawallet.app.entity.LocaleItem

interface LocaleRepositoryType {
    fun getUserPreferenceLocale(): String?

    fun setUserPreferenceLocale(locale: String)

    fun setLocale(context: Context?, locale: String)

    fun getLocaleList(context: Context): ArrayList<LocaleItem>

    fun getActiveLocale(): String

    fun isLocalePresent(locale: String): Boolean
}
