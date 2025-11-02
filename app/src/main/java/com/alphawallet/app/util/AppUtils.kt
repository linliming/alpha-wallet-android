package com.alphawallet.app.util

import android.content.Context
import android.text.TextUtils
import android.webkit.URLUtil
import com.alphawallet.app.BuildConfig
import com.alphawallet.app.C
import com.alphawallet.app.R
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.util.Date

fun formatUrl(url: String): String {
    return when {
        URLUtil.isHttpsUrl(url) || URLUtil.isHttpUrl(url) || isWalletPrefix(url) -> url
        url.isValidUrl() -> C.HTTPS_PREFIX + url
        else -> C.INTERNET_SEARCH_PREFIX + url
    }
}

fun isWalletPrefix(url: String): Boolean {
    return url.startsWith(C.DAPP_PREFIX_TELEPHONE) ||
            url.startsWith(C.DAPP_PREFIX_MAILTO) ||
            url.startsWith(C.DAPP_PREFIX_ALPHAWALLET) ||
            url.startsWith(C.DAPP_PREFIX_MAPS) ||
            url.startsWith(C.DAPP_PREFIX_WALLETCONNECT) ||
            url.startsWith(C.DAPP_PREFIX_AWALLET)
}

fun copyFile(source: String, dest: String): Boolean {
    return try {
        FileInputStream(source).channel.use { s ->
            FileOutputStream(dest).channel.use { d ->
                d.transferFrom(s, 0, s.size())
            }
        }
        true
    } catch (e: IOException) {
        Timber.e(e)
        false
    }
}

fun parseTokenId(tokenIdStr: String): BigInteger {
    return try {
        BigInteger(tokenIdStr)
    } catch (e: Exception) {
        BigInteger.ZERO
    }
}

fun randomId(): Long {
    return Date().time
}

fun isContractCall(context: Context, operationName: String?): Boolean {
    return !TextUtils.isEmpty(operationName) && context.getString(R.string.contract_call) == operationName
}


fun timeUntil(eventInMillis: Long): Long {
    return eventInMillis - System.currentTimeMillis()
}

// For detecting test runs
private val testLock = Any()
private var isTest: Boolean? = null

fun isRunningTest(): Boolean = synchronized(testLock) {
    if (isTest == null) {
        if (!BuildConfig.DEBUG) {
            isTest = false
            return false
        }
        isTest = try {
            Class.forName("androidx.test.espresso.Espresso")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
    return isTest!!
}
