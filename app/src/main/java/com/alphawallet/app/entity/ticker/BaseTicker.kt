package com.alphawallet.app.entity.ticker

import android.text.TextUtils
import com.alphawallet.app.entity.tokendata.TokenTicker
import java.math.BigDecimal
import java.math.RoundingMode

abstract class BaseTicker
    (val address: String, val fiatPrice: Double, val fiatChange: BigDecimal) {
    fun toTokenTicker(currentCurrencySymbolTxt: String): TokenTicker {
        return TokenTicker(
            fiatPrice.toString(),
            fiatChange.setScale(3, RoundingMode.DOWN).toString(),
            currentCurrencySymbolTxt,
            "",
            System.currentTimeMillis()
        )
    }

    fun toTokenTicker(currentCurrencySymbolTxt: String, conversionRate: Double): TokenTicker {
        return TokenTicker(
            (fiatPrice * conversionRate).toString(),
            fiatChange.setScale(3, RoundingMode.DOWN).toString(),
            currentCurrencySymbolTxt,
            "",
            System.currentTimeMillis()
        )
    }

    companion object {
        @JvmStatic
        protected fun getFiatChange(fiatChangeStr: String?): BigDecimal {
            if (TextUtils.isEmpty(fiatChangeStr)) return BigDecimal.ZERO

            return try {
                BigDecimal(fiatChangeStr)
            } catch (e: Exception) {
                BigDecimal.ZERO
            }
        }
    }
}
