package com.alphawallet.app.router

import android.app.Activity
import android.content.Intent
import com.alphawallet.app.C
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.ui.SwapActivity


class SwapRouter {
    fun open(context: Activity, token: Token, wallet: Wallet?) {
        val intent = Intent(context, SwapActivity::class.java)
        intent.putExtra(C.Key.WALLET, wallet)
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress())
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        context.startActivityForResult(intent, C.TOKEN_SEND_ACTIVITY)
    }
}
