package com.alphawallet.app.entity.lifi

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class Quote {
    @SerializedName("id")
    @Expose
    var id: String? = null

    @SerializedName("type")
    @Expose
    var type: String? = null

    @SerializedName("tool")
    @Expose
    var tool: String? = null

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

    @JvmField
    @SerializedName("transactionRequest")
    @Expose
    var transactionRequest: TransactionRequest? = null

    class TransactionRequest {
        @JvmField
        @SerializedName("from")
        @Expose
        var from: String? = null

        @JvmField
        @SerializedName("to")
        @Expose
        var to: String? = null

        @SerializedName("chainId")
        @Expose
        var chainId: Long = 0

        @JvmField
        @SerializedName("data")
        @Expose
        var data: String? = null

        @JvmField
        @SerializedName("value")
        @Expose
        var value: String? = null

        @JvmField
        @SerializedName("gasLimit")
        @Expose
        var gasLimit: String? = null

        @JvmField
        @SerializedName("gasPrice")
        @Expose
        var gasPrice: String? = null
    }
}
