package com.alphawallet.app.repository

import android.content.Context
import com.alphawallet.app.entity.LocaleItem
import com.alphawallet.app.util.LocaleUtils
import java.util.ArrayList

class LocaleRepository(private val preferences: PreferenceRepositoryType) : LocaleRepositoryType {

    override fun setLocale(context: Context?, locale: String) {
        context?.let { LocaleUtils.setLocale(it, locale) }
    }

    override fun getUserPreferenceLocale(): String? = preferences.userPreferenceLocale

    override fun setUserPreferenceLocale(locale: String) {
        preferences.userPreferenceLocale = locale
    }

    override fun getActiveLocale(): String {
        val userLocale = preferences.userPreferenceLocale
        return if (userLocale.isNullOrEmpty()) {
            preferences.defaultLocale.orEmpty()
        } else {
            userLocale
        }
    }

    override fun getLocaleList(context: Context): ArrayList<LocaleItem> {
        val list = ArrayList<LocaleItem>()
        for (locale in LOCALES) {
            list.add(LocaleItem(LocaleUtils.getDisplayLanguage(locale, getActiveLocale()), locale))
        }
        return list
    }

    override fun isLocalePresent(locale: String): Boolean {
        return LOCALES.contains(locale)
    }

    companion object {
        private val LOCALES = arrayOf(
            "en",
            "zh",
            "es",
            "fr",
            "vi",
            "my",
            "id"
        )
    }
}
