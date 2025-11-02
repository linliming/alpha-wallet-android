package com.alphawallet.app.router

import android.content.Context
import android.content.Intent
import android.net.Uri

class ExternalBrowserRouter {
    fun open(context: Context, uri: Uri?) {
        val launchBrowser = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(launchBrowser)
    }
}
