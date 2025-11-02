package com.alphawallet.app.router

import android.content.Context
import android.content.Intent
import com.alphawallet.app.C
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.ui.TransferTicketDetailActivity

/**
 * Created by James on 22/02/2018.
 */
class TransferTicketDetailRouter {
    fun open(context: Context, token: Token, ticketIDs: String?, wallet: Wallet?) {
        val intent = Intent(context, TransferTicketDetailActivity::class.java)
        intent.putExtra(C.Key.WALLET, wallet)
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress())
        intent.putExtra(C.EXTRA_TOKENID_LIST, ticketIDs)
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(intent)
    }

    fun openTransfer(
        context: Context,
        token: Token,
        ticketIDs: String?,
        wallet: Wallet?,
        state: Int
    ) {
        val intent = Intent(context, TransferTicketDetailActivity::class.java)
        intent.putExtra(C.Key.WALLET, wallet)
        intent.putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
        intent.putExtra(C.EXTRA_ADDRESS, token.getAddress())
        intent.putExtra(C.EXTRA_TOKENID_LIST, ticketIDs)
        intent.putExtra(C.EXTRA_STATE, state)
        intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        context.startActivity(intent)
    }
}
