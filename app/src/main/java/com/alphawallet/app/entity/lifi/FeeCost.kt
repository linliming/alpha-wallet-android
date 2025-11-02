package com.alphawallet.app.entity.lifi

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class FeeCost {
    @JvmField
    @SerializedName("name")
    @Expose
    var name: String? = null

    @SerializedName("percentage")
    @Expose
    var percentage: String? = null

    @JvmField
    @SerializedName("token")
    @Expose
    var token: Token? = null

    @JvmField
    @SerializedName("amount")
    @Expose
    var amount: String? = null
}
