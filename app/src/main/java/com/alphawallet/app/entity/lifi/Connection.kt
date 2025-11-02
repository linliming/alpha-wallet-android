package com.alphawallet.app.entity.lifi

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class Connection {
    @SerializedName("fromChainId")
    @Expose
    var fromChainId: String? = null

    @SerializedName("toChainId")
    @Expose
    var toChainId: String? = null

    @JvmField
    @SerializedName("fromTokens")
    @Expose
    var fromTokens: List<Token>? = null

    @JvmField
    @SerializedName("toTokens")
    @Expose
    var toTokens: List<Token>? = null
}
