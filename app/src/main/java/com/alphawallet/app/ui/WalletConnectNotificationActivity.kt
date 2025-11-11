package com.alphawallet.app.ui

import android.content.Intent
import android.os.Bundle
import com.alphawallet.app.entity.walletconnect.WalletConnectSessionItem
import com.alphawallet.app.interact.WalletConnectInteract
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * This activity is created to simplify notification click event, according to sessions count, when:
 * 1: to session details
 * more than 1: to sessions list
 */
@AndroidEntryPoint
class WalletConnectNotificationActivity : BaseActivity() {
    @Inject
    var walletConnectInteract: WalletConnectInteract? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        route()
        finish()
    }

    private fun route() {
        val intent: Intent?
        val sessions: MutableList<WalletConnectSessionItem> = walletConnectInteract?.getSessions()?: mutableListOf()

        intent = if (sessions.size == 1) {
            WalletConnectSessionActivity.newIntent(applicationContext, sessions.get(0))
        } else {
            Intent(applicationContext, WalletConnectSessionActivity::class.java)
        }

        startActivity(intent)
    }
}
