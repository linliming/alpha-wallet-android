package com.alphawallet.app.util

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import com.alphawallet.app.widget.PermissionRationaleDialog

object PermissionUtils {
    fun requestPostNotificationsPermission(
        activity: Activity,
        requestPermissionLauncher: ActivityResultLauncher<String?>
    ): Boolean {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
                return true
            } else if (activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                PermissionRationaleDialog.show(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS,
                    { ok: View? -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    { cancel: View? -> }
                )
                return false
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return false
            }
        } else {
            return true
        }
    }
}
