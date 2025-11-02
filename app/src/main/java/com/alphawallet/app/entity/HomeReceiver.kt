package com.alphawallet.app.entity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import androidx.core.content.ContextCompat.registerReceiver
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.alphawallet.app.C

/**
 * Handles app-wide broadcasts used by the home screen, such as backup completion and notifications.
 */
class HomeReceiver @JvmOverloads constructor(
    context: Context? = null,
    private val homeCommsInterface: HomeCommsInterface? = null,
) : BroadcastReceiver() {

    private val broadcastManager: LocalBroadcastManager? =
        context?.let { LocalBroadcastManager.getInstance(it) }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        val bundle = intent.extras
        when (action) {
            C.REQUEST_NOTIFICATION_ACCESS -> homeCommsInterface?.requestNotificationPermission()
            C.BACKUP_WALLET_SUCCESS -> {
                val keyAddress = bundle?.getString("Key", "") ?: ""
                homeCommsInterface?.backupSuccess(keyAddress)
            }
        }
    }

    /**
     * Registers the receiver to listen for home related broadcasts.
     */
    fun register(ctx: Context) {
        val filter = IntentFilter().apply {
            addAction(C.REQUEST_NOTIFICATION_ACCESS)
            addAction(C.BACKUP_WALLET_SUCCESS)
            addAction(C.WALLET_CONNECT_REQUEST)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(ctx, this, IntentFilter(C.WALLET_CONNECT_COUNT_CHANGE), RECEIVER_NOT_EXPORTED)
        } else {
            broadcastManager?.registerReceiver(this, filter)
        }
    }

    /**
     * Unregisters the receiver, mirroring the original registration path.
     */
    fun unregister(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.unregisterReceiver(this)
        } else {
            broadcastManager?.unregisterReceiver(this)
        }
    }
}
