package com.alphawallet.app.router

import android.content.Context
import android.content.Intent
import com.alphawallet.app.C
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.ui.RedeemAssetSelectActivity

/**
 * Created by James on 27/02/2018.
 */
class RedeemAssetSelectRouter {
    fun open(context: Context, token: Token) {
        val intent = Intent(context, RedeemAssetSelectActivity::class.java)
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress())
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        context.startActivity(intent)
    }
}
