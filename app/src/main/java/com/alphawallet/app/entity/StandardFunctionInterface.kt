package com.alphawallet.app.entity

import com.alphawallet.token.entity.TSAction
import java.math.BigInteger

interface StandardFunctionInterface {
    fun selectRedeemTokens(selection: List<BigInteger>) {}

    fun sellTicketRouter(selection: List<BigInteger>) {}

    fun showTransferToken(selection: List<BigInteger>) {}

    fun showSend() {}

    fun showReceive() {}

    fun updateAmount() {}

    fun displayTokenSelectionError(action: TSAction?) {}

    fun handleClick(action: String?, actionId: Int) {}

    fun handleTokenScriptFunction(function: String?, selection: List<BigInteger?>?) {}

    fun showWaitSpinner(show: Boolean) {}

    fun handleFunctionDenied(denialMessage: String?) {}

    fun completeFunctionSetup() {}
}
