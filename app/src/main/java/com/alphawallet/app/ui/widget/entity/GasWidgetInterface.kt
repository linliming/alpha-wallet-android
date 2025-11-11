package com.alphawallet.app.ui.widget.entity

import com.alphawallet.app.entity.analytics.ActionSheetMode
import com.alphawallet.app.web3.entity.Web3Transaction
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Created by JB on 23/03/2022.
 *
 * This might be better done using a common class containing these functions which the 1559 and legacy widgets derive from
 * All common code can go into this class.
 */
interface GasWidgetInterface {
    fun isSendingAll(tx: Web3Transaction?): Boolean
    val value: BigInteger?
    fun getGasPrice(defaultPrice: BigInteger?): BigInteger?
    val gasLimit: BigInteger?
    val nonce: Long
    val gasMax: BigInteger?
    val priorityFee: BigInteger?
    val gasPrice: BigInteger?
    fun setGasEstimate(estimate: BigInteger?)
    fun setGasEstimateExact(estimate: BigInteger?)
    fun onDestroy()
    fun checkSufficientGas(): Boolean
    fun setupResendSettings(mode: ActionSheetMode?, gasPrice: BigInteger?)
    fun setCurrentGasIndex(
        gasSelectionIndex: Int,
        maxFeePerGas: BigInteger?,
        maxPriorityFee: BigInteger?,
        customGasLimit: BigDecimal?,
        expectedTxTime: Long,
        customNonce: Long
    )

    val expectedTransactionTime: Long
    fun gasPriceReady(gasEstimateTime: Long): Boolean {
        return gasEstimateTime > (System.currentTimeMillis() - 30 * 1000)
    }

    fun gasPriceReady(): Boolean
}
