package com.alphawallet.app.router

import android.content.Context
import android.content.Intent
import com.alphawallet.app.ui.WalletsActivity

class ManageWalletsRouter {
    fun open(context: Context, isClearStack: Boolean) {
        val intent = Intent(context, WalletsActivity::class.java)
        if (isClearStack) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
    }
}
