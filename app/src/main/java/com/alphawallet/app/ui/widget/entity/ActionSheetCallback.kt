package com.alphawallet.app.ui.widget.entity

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.alphawallet.app.entity.SignAuthenticationCallback
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.service.GasService
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.token.entity.Signable
import java.math.BigInteger

/**
 * Created by JB on 27/11/2020.
 */
interface ActionSheetCallback {
    fun getAuthorisation(callback: SignAuthenticationCallback?)

    fun sendTransaction(tx: Web3Transaction?)

    //For Hardware wallet
    fun completeSendTransaction(
        tx: Web3Transaction?,
        signature: SignatureFromKey?
    ) //return from hardware signing

    fun completeSignTransaction(
        tx: Web3Transaction?,
        signature: SignatureFromKey?
    ) //return from hardware signing - sign tx only
    {
    }

    fun dismissed(txHash: String?, callbackId: Long, actionCompleted: Boolean)

    fun notifyConfirm(mode: String?)

    fun gasSelectLauncher(): ActivityResultLauncher<Intent>?

    fun signTransaction(tx: Web3Transaction?) {
    } // only WalletConnect uses this so far

    fun buttonClick(callbackId: Long, baseToken: Token?) {
    }

    fun notifyWalletConnectApproval(chainId: Long) {
    }

    fun denyWalletConnect() {
    }

    fun openChainSelection() {
    }

    fun signingComplete(signature: SignatureFromKey, message: Signable) {
    }

    fun signingFailed(error: Throwable, message: Signable) {
    }

    val walletType: WalletType?

    val tokenId: BigInteger?
        get() = BigInteger.ZERO

    val gasService: GasService?
}
