package com.alphawallet.app

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.alphawallet.app.util.TimberInit.configTimber
import com.alphawallet.app.walletconnect.AWWalletConnectClient
import dagger.hilt.android.HiltAndroidApp
import io.reactivex.plugins.RxJavaPlugins
import io.realm.Realm
import timber.log.Timber
import java.util.EmptyStackException
import java.util.Stack
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {
    @Inject
    var awWalletConnectClient: AWWalletConnectClient? = null

    private val activityStack = Stack<Activity>()

    val topActivity: Activity?
        get() {
            return try {
                activityStack.peek()
            } catch (e: EmptyStackException) {
                //
                null
            }
        }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Realm.init(this)
        configTimber()

        val defaultTheme = PreferenceManager.getDefaultSharedPreferences(this)
            .getInt("theme", C.THEME_AUTO)

        if (defaultTheme == C.THEME_LIGHT) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        } else if (defaultTheme == C.THEME_DARK) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager
            val mode = uiModeManager.nightMode
            if (mode == UiModeManager.MODE_NIGHT_YES) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else if (mode == UiModeManager.MODE_NIGHT_NO) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        RxJavaPlugins.setErrorHandler { t: Throwable? -> Timber.e(t) }

        try {
            awWalletConnectClient!!.init(this)
        } catch (e: Exception) {
            Timber.tag("WalletConnect").e(e)
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            }

            override fun onActivityDestroyed(activity: Activity) {
            }

            override fun onActivityStarted(activity: Activity) {
            }

            override fun onActivityResumed(activity: Activity) {
                activityStack.push(activity)
            }

            override fun onActivityPaused(activity: Activity) {
                pop()
            }

            override fun onActivityStopped(activity: Activity) {
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }
        })
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (awWalletConnectClient != null) {
            awWalletConnectClient!!.shutdown()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        activityStack.clear()
        if (awWalletConnectClient != null) {
            awWalletConnectClient!!.shutdown()
        }
    }

    private fun pop() {
        activityStack.pop()
    }

    companion object {
        var instance: App? = null
            private set
    }
}
