package com.alphawallet.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.RawRes
import androidx.fragment.app.FragmentActivity
import com.alphawallet.app.BuildConfig
import com.alphawallet.app.R
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanOptions
import timber.log.Timber
import java.io.IOException
import java.nio.charset.StandardCharsets

fun Context.dp2px(dp: Int): Int {
    val r: Resources = this.resources
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        r.displayMetrics
    ).toInt()
}

fun Context.loadJSONFromAsset(fileName: String): String? {
    return try {
        this.assets.open(fileName).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    } catch (ex: IOException) {
        ex.printStackTrace()
        null
    }
}

fun Context.loadRawResource(@RawRes rawRes: Int): String {
    return try {
        this.resources.openRawResource(rawRes).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    } catch (e: Exception) {
        Timber.tag("READ_JS_TAG").d(e, "Ex")
        ""
    }
}

fun Context.verifyInstallerId(): Boolean {
    return try {
        val packageManager = this.packageManager
        val installingPackageName: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val installer: InstallSourceInfo =
                packageManager.getInstallSourceInfo(this.packageName)
            installer.installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(this.packageName)
        }
        // A list with valid installers package name
        val validInstallers = listOf("com.android.vending", "com.google.android.feedback")

        // true if your app has been downloaded from Play Store
        installingPackageName != null && validInstallers.contains(installingPackageName)
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

@ColorInt
fun Context.getColorFromAttr(@AttrRes resId: Int): Int {
    val typedValue = TypedValue()
    val theme: Resources.Theme = this.theme
    theme.resolveAttribute(resId, typedValue, true)
    return typedValue.data
}

fun Context?.stillAvailable(): Boolean {
    return when (this) {
        null -> false
        is FragmentActivity -> !this.isDestroyed
        is Activity -> !this.isDestroyed
        is ContextWrapper -> this.baseContext.stillAvailable()
        else -> true // Assume available if context is not an activity
    }
}

fun Context.getQRScanOptions(): ScanOptions {
    return ScanOptions().apply {
        addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
        setBeepEnabled(true)
        setOrientationLocked(false)
        setPrompt(getString(R.string.message_scan_camera))
    }
}

fun Context.isAlphaWallet(): Boolean {
    return this.packageName == "io.stormbird.wallet"
}

fun Context.isDefaultName(name: String?): Boolean {
    val walletStrTemplate = this.getString(R.string.wallet_name_template, 1)
    val walletSplit = walletStrTemplate.split(" ").toTypedArray()
    val walletStr = walletSplit[0]
    return if (!name.isNullOrEmpty() && name.startsWith(walletStr) && walletSplit.size == 2) {
        //check last part is a number
        getWalletNum(walletSplit) > 0
    } else {
        false
    }
}

private fun getWalletNum(walletSplit: Array<String>): Int {
    if (walletSplit.size != 2) {
        return 0
    }
    return try {
        walletSplit[1].toInt()
    } catch (e: Exception) {
        0
    }
}
