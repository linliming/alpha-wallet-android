package com.alphawallet.app.entity

import android.app.Activity
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
 * Broadcast receiver that listens for finish signals and closes the associated `Activity`.
 */
class FinishReceiver private constructor(
    private val activity: Activity?,
    private val broadcastManager: LocalBroadcastManager?,
) : BroadcastReceiver() {

    constructor(activity: Activity) : this(
        activity = activity,
        broadcastManager = LocalBroadcastManager.getInstance(activity),
    ) {
        register(activity)
    }

    constructor() : this(null, null)

    /**
     * Registers the receiver for the relevant broadcast action based on API level.
     */
    private fun register(ctx: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(ctx, this, IntentFilter(C.WALLET_CONNECT_COUNT_CHANGE), RECEIVER_NOT_EXPORTED)
        } else {
            broadcastManager?.registerReceiver(this, IntentFilter(C.PRUNE_ACTIVITY))
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        activity?.finish()
    }

    /**
     * Unregisters the receiver, mirroring the registration path.
     */
    fun unregister() {
        if (activity == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.unregisterReceiver(this)
        } else {
            broadcastManager?.unregisterReceiver(this)
        }
    }
}

