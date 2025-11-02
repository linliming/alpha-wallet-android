package com.alphawallet.app.entity

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import com.alphawallet.app.web3.entity.Web3Transaction

/**
 * Created by JB on 16/01/2021.
 */
interface ActionSheetInterface {
    fun lockDragging(shouldLock: Boolean)
    fun fullExpand()

    fun success() {
    }

    fun setURL(url: String?) {
    }

    fun setGasEstimate(estimate: GasEstimate?) {
    }

    fun completeSignRequest(gotAuth: Boolean?) {
    }

    fun setSigningWallet(account: String?) {
    }

    fun setIcon(icon: String?) {
    }

    fun transactionWritten(hash: String?) {
    }

    fun updateChain(chainId: Long) {
    }

    val transaction: Web3Transaction
        get() {
            throw RuntimeException("Implement getTransaction")
        }

    fun setSignOnly() {
    }

    fun setCurrentGasIndex(result: ActivityResult?) {
    }

    fun gasSelectLauncher(): ActivityResultLauncher<Intent>? {
        return null
    }

    fun gasEstimateReady() {
    }
}
