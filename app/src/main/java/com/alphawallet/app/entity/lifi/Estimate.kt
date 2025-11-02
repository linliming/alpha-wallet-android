package com.alphawallet.app.entity.lifi

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class Estimate {
    @SerializedName("fromAmount")
    @Expose
    var fromAmount: String? = null

    @SerializedName("toAmount")
    @Expose
    var toAmount: String? = null

    @JvmField
    @SerializedName("toAmountMin")
    @Expose
    var toAmountMin: String? = null

    @SerializedName("approvalAddress")
    @Expose
    var approvalAddress: String? = null

    @SerializedName("executionDuration")
    @Expose
    var executionDuration: Long = 0

    @JvmField
    @SerializedName("feeCosts")
    @Expose
    var feeCosts: ArrayList<FeeCost>? = null

    @JvmField
    @SerializedName("gasCosts")
    @Expose
    var gasCosts: ArrayList<GasCost>? = null

    @SerializedName("data")
    @Expose
    var data: Data? = null

    @SerializedName("fromAmountUSD")
    @Expose
    var fromAmountUSD: String? = null

    @SerializedName("toAmountUSD")
    @Expose
    var toAmountUSD: String? = null

    class Data {
        @SerializedName("blockNumber")
        @Expose
        var blockNumber: Long = 0

        @SerializedName("network")
        @Expose
        var network: Long = 0

        @SerializedName("srcToken")
        @Expose
        var srcToken: String? = null

        @SerializedName("srcDecimals")
        @Expose
        var srcDecimals: Long = 0

        @SerializedName("srcAmount")
        @Expose
        var srcAmount: String? = null

        @SerializedName("destToken")
        @Expose
        var destToken: String? = null

        @SerializedName("destDecimals")
        @Expose
        var destDecimals: Long = 0

        @SerializedName("destAmount")
        @Expose
        var destAmount: String? = null

        @SerializedName("gasCostUSD")
        @Expose
        var gasCostUSD: String? = null

        @SerializedName("gasCost")
        @Expose
        var gasCost: String? = null

        @SerializedName("buyAmount")
        @Expose
        var buyAmount: String? = null

        @SerializedName("sellAmount")
        @Expose
        var sellAmount: String? = null
    }
}
