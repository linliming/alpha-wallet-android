package com.alphawallet.app.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.DisplayMetrics
import androidx.preference.PreferenceManager
import com.alphawallet.app.repository.SharedPreferenceRepository
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 语言与地区相关工具。
 *
 * 负责读取/设置应用当前语言设置，并提供常用日期转换方法。
 */
object LocaleUtils {

    /**
     * 根据基准语言返回目标语言的本地化描述（首字母大写）。
     */
    @JvmStatic
    fun getDisplayLanguage(locale: String, base: String): String {
        val target = Locale(locale)
        val intermediate = Locale(base)
        val displayLanguage = target.getDisplayLanguage(intermediate)
        return displayLanguage.replaceFirstChar { if (it.isLowerCase()) it.titlecase(intermediate) else it.toString() }
    }

    /**
     * 设置指定上下文的语言环境。
     */
    @JvmStatic
    @Suppress("DEPRECATION")
    fun setLocale(context: Context, locale: String) {
        val target = Locale(locale)
        val resources = context.resources
        val metrics: DisplayMetrics = resources.displayMetrics
        val configuration: Configuration = resources.configuration
        configuration.locale = target
        resources.updateConfiguration(configuration, metrics)
    }

    /**
     * 将秒级时间戳转换为本地当天的结束时间。
     */
    @JvmStatic
    fun getLocalDateFromTimestamp(timeStampInSec: Long): Date {
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = timeStampInSec * DateUtils.SECOND_IN_MILLIS
            set(Calendar.MILLISECOND, 999)
            set(Calendar.SECOND, 59)
            set(Calendar.MINUTE, 59)
            set(Calendar.HOUR_OF_DAY, 23)
        }
        return calendar.time
    }

    /**
     * 记录设备默认语言环境，便于后续恢复。
     */
    @JvmStatic
    fun setDeviceLocale(ctx: Context) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .edit()
            .putString(SharedPreferenceRepository.DEVICE_LOCALE, currentLanguage)
            .putString(SharedPreferenceRepository.DEVICE_COUNTRY, currentCountry)
            .apply()
    }

    /**
     * 读取设备启动时记录的语言环境。
     */
    @JvmStatic
    fun getDeviceLocale(ctx: Context): Locale {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val language = prefs.getString(SharedPreferenceRepository.DEVICE_LOCALE, "en").orEmpty()
        val country = prefs.getString(SharedPreferenceRepository.DEVICE_COUNTRY, "US").orEmpty()
        return Locale(language, country)
    }

    /**
     * 根据用户设置更新当前语言环境，并返回激活语言代码。
     */
    @JvmStatic
    @Suppress("DEPRECATION")
    fun setActiveLocale(context: Context): String {
        val localeCode = getActiveLocaleName(context)
        val locale = Locale(localeCode)

        val resources = context.resources
        val metrics = resources.displayMetrics
        val configuration = resources.configuration
        configuration.setLocale(locale)
        resources.updateConfiguration(configuration, metrics)

        val appResources = context.applicationContext.resources
        appResources.updateConfiguration(configuration, appResources.displayMetrics)

        return localeCode
    }

    /**
     * 创建带有当前激活语言环境的新 Context。
     */
    @JvmStatic
    fun getActiveLocaleContext(context: Context): Context {
        val localeCode = getActiveLocaleName(context)
        val locale = Locale(localeCode)
        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    /**
     * 获取设备当前设置的语言代码。
     */
    @JvmStatic
    fun getDeviceSettingsLocale(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales.get(0).language
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale.language
        }

    private fun getActiveLocaleName(context: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.getString(SharedPreferenceRepository.USER_LOCALE_PREF, "").orEmpty()
        return if (TextUtils.isEmpty(saved)) {
            getDeviceSettingsLocale(context)
        } else {
            saved
        }
    }

    private val currentLanguage: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList.getDefault().get(0).language
        } else {
            Locale.getDefault().language
        }

    private val currentCountry: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList.getDefault().get(0).country
        } else {
            Locale.getDefault().country
        }
}
