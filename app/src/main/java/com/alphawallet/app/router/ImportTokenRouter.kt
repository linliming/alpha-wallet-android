package com.alphawallet.app.router

import android.content.Context
import android.content.Intent
import com.alphawallet.app.C
import com.alphawallet.app.ui.ImportTokenActivity

/**
 * Created by James on 9/03/2018.
 */
class ImportTokenRouter {
    fun open(context: Context, importTxt: String?) {
        val intent = Intent(context, ImportTokenActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.putExtra(C.IMPORT_STRING, importTxt)
        context.startActivity(intent)
    }
}
