package com.alphawallet.app.entity.lifi

import android.text.TextUtils
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.math.RoundingMode

class Action {
    @kotlin.jvm.JvmField
    @SerializedName("fromChainId")
    @Expose
    var fromChainId: Long = 0

    @SerializedName("toChainId")
    @Expose
    var toChainId: Long = 0

    @SerializedName("fromToken")
    @Expose
    var fromToken: Token? = null

    @SerializedName("toToken")
    @Expose
    var toToken: Token? = null

    @SerializedName("fromAmount")
    @Expose
    var fromAmount: String? = null

    @SerializedName("slippage")
    @Expose
    var slippage: Double = 0.0

    @SerializedName("fromAddress")
    @Expose
    var fromAddress: String? = null

    @SerializedName("toAddress")
    @Expose
    var toAddress: String? = null

    val currentPrice: String
        get() {
            return if (fromToken == null || TextUtils.isEmpty(fromToken!!.priceUSD) || BigDecimal(
                    fromToken!!.priceUSD
                ) == BigDecimal.ZERO
            ) {
                "0"
            } else {
                BigDecimal(fromToken!!.priceUSD)
                    .divide(BigDecimal(toToken!!.priceUSD), 4, RoundingMode.DOWN)
                    .stripTrailingZeros()
                    .toPlainString()
            }
        }
}
