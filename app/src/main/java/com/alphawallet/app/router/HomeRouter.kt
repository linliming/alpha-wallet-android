package com.alphawallet.app.router

import android.content.Context
import android.content.Intent
import com.alphawallet.app.C
import com.alphawallet.app.ui.HomeActivity

class HomeRouter {
    fun open(context: Context, isClearStack: Boolean) {
        val intent = Intent(context, HomeActivity::class.java)
        intent.putExtra(
            C.FROM_HOME_ROUTER,
            C.FROM_HOME_ROUTER
        ) //HomeRouter should restart the app at the wallet
        if (isClearStack) {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
    }
}
