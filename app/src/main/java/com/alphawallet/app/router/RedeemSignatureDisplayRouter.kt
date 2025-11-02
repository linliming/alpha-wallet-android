package com.alphawallet.app.router

import android.content.Context
import android.content.Intent
import com.alphawallet.app.C
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.ui.RedeemSignatureDisplayActivity
import com.alphawallet.app.ui.widget.entity.TicketRangeParcel

/**
 * Created by James on 25/01/2018.
 */
class RedeemSignatureDisplayRouter {
    fun open(context: Context, wallet: Wallet?, token: Token, range: TicketRangeParcel?) {
        val intent = Intent(context, RedeemSignatureDisplayActivity::class.java)
        intent.putExtra(C.Key.WALLET, wallet)
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress())
        intent.putExtra(C.Key.TICKET_RANGE, range)
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(intent)
    }
}
