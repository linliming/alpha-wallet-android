package com.alphawallet.app.entity.lifi

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class Route {
    @SerializedName("gasCostUSD")
    @Expose
    var gasCostUSD: String? = null

    @JvmField
    @SerializedName("steps")
    @Expose
    var steps: List<Step>? = null

    @JvmField
    @SerializedName("tags")
    @Expose
    var tags: List<String>? = null

    class Step {
        @JvmField
        @SerializedName("toolDetails")
        @Expose
        var swapProvider: SwapProvider? = null

        @JvmField
        @SerializedName("action")
        @Expose
        var action: Action? = null

        @JvmField
        @SerializedName("estimate")
        @Expose
        var estimate: Estimate? = null
    }
}
