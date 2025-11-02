package com.alphawallet.app.entity.lifi

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class GasCost {
    @JvmField
    @SerializedName("amount")
    @Expose
    var amount: String? = null

    @SerializedName("amountUSD")
    @Expose
    var amountUSD: String? = null

    @JvmField
    @SerializedName("token")
    @Expose
    var token: Token? = null
}
