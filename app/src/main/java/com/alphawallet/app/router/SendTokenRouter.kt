package com.alphawallet.app.router

import android.app.Activity
import android.content.Intent
import com.alphawallet.app.C
import com.alphawallet.app.entity.QRResult
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.ui.SendActivity


class SendTokenRouter {
    fun open(
        context: Activity,
        address: String?,
        symbol: String?,
        decimals: Int,
        wallet: Wallet?,
        token: Token,
        chainId: Long
    ) {
        val intent = Intent(context, SendActivity::class.java)
        intent.putExtra(C.EXTRA_CONTRACT_ADDRESS, address)
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress())
        intent.putExtra(C.EXTRA_NETWORKID, chainId)
        intent.putExtra(C.EXTRA_SYMBOL, symbol)
        intent.putExtra(C.EXTRA_DECIMALS, decimals)
        intent.putExtra(C.Key.WALLET, wallet)
        intent.putExtra(C.EXTRA_AMOUNT, null as QRResult?)
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        context.startActivityForResult(intent, C.COMPLETED_TRANSACTION)
    }
}
