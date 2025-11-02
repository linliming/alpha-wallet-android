package com.alphawallet.app.router

import android.app.Activity
import android.content.Intent
import com.alphawallet.app.C
import com.alphawallet.app.ui.ImportWalletActivity

class ImportWalletRouter {
    fun openForResult(activity: Activity, requestCode: Int, fromSplash: Boolean) {
        val intent = Intent(activity, ImportWalletActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        intent.putExtra(C.EXTRA_FROM_SPLASH, fromSplash)
        activity.startActivityForResult(intent, requestCode)
    }

    fun openWatchCreate(activity: Activity, requestCode: Int) {
        val intent = Intent(activity, ImportWalletActivity::class.java)
        intent.putExtra(C.EXTRA_STATE, "watch")
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        activity.startActivityForResult(intent, requestCode)
    }
}
