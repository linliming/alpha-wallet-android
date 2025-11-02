package com.alphawallet.app.router

import android.content.Context
import android.content.Intent
import com.alphawallet.app.C
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.ui.MyAddressActivity

class MyAddressRouter {
    fun open(context: Context, wallet: Wallet?) {
        val intent = Intent(context, MyAddressActivity::class.java)
        intent.putExtra(C.Key.WALLET, wallet)
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        context.startActivity(intent)
    }

    fun open(context: Context, wallet: Wallet?, token: Token) {
        val intent = Intent(context, MyAddressActivity::class.java)
        intent.putExtra(C.Key.WALLET, wallet)
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress())
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        context.startActivity(intent)
    }
}
