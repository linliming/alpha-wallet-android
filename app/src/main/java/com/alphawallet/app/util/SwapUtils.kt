package com.alphawallet.app.util

import com.alphawallet.app.entity.lifi.Action
import com.alphawallet.app.entity.lifi.Estimate
import com.alphawallet.app.entity.lifi.FeeCost
import com.alphawallet.app.entity.lifi.GasCost
import com.alphawallet.app.util.BalanceUtils.getScaledValue
import com.alphawallet.app.util.BalanceUtils.getScaledValueFixed
import java.math.BigDecimal

object SwapUtils {
    private const val CURRENT_PRICE_FORMAT = "1 %s ≈ %s %s"
    private const val GAS_PRICE_FORMAT = "%s %s"
    private const val FEE_FORMAT = "%s %s"
    private const val MINIMUM_RECEIVED_FORMAT = "%s %s"

    fun getTotalGasFees(gasCosts: ArrayList<GasCost>?): String {
        if (gasCosts != null) {
            val gas = StringBuilder()
            for (gc in gasCosts) {
                gas.append(getGasFee(gc)).append(System.lineSeparator())
            }
            return gas.toString().trim { it <= ' ' }
        } else {
            return ""
        }
    }

    @JvmStatic
    fun getGasFee(gasCost: GasCost): String {
        return String.format(
            GAS_PRICE_FORMAT,
            getScaledValueFixed(BigDecimal(gasCost.amount), gasCost.token!!.decimals, 4),
            gasCost.token!!.symbol
        )
    }

    fun getOtherFees(feeCosts: ArrayList<FeeCost>?): String {
        if (feeCosts != null) {
            val fees = StringBuilder()
            for (fc in feeCosts) {
                fees.append(fc.name)
                fees.append(": ")
                fees.append(getFee(fc)).append(System.lineSeparator())
            }
            return fees.toString().trim { it <= ' ' }
        } else {
            return ""
        }
    }

    fun getFee(feeCost: FeeCost): String {
        return String.format(
            FEE_FORMAT,
            getScaledValueFixed(BigDecimal(feeCost.amount), feeCost.token!!.decimals, 4),
            feeCost.token!!.symbol
        )
    }

    @JvmStatic
    fun getFormattedCurrentPrice(action: Action): String {
        return String.format(
            CURRENT_PRICE_FORMAT,
            action.fromToken!!.symbol,
            action.currentPrice,
            action.toToken!!.symbol
        )
    }

    @JvmStatic
    fun getFormattedMinAmount(estimate: Estimate, action: Action): String {
        return String.format(
            MINIMUM_RECEIVED_FORMAT,
            getScaledValue(estimate.toAmountMin!!, action.toToken!!.decimals, 4),
            action.toToken!!.symbol
        )
    }
}
