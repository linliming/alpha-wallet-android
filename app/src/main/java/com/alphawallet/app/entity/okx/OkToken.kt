package com.alphawallet.app.entity.okx

import com.alphawallet.app.entity.tokens.TokenInfo
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class OkToken {
    @SerializedName("symbol")
    @Expose
    var symbol: String? = null

    @SerializedName("tokenContractAddress")
    @Expose
    var tokenContractAddress: String? = null

    @SerializedName("holdingAmount")
    @Expose
    var holdingAmount: String? = null

    @SerializedName("priceUsd")
    @Expose
    var priceUsd: String? = null

    @SerializedName("tokenId")
    @Expose
    var tokenId: String? = null

    fun createInfo(chainId: Long): TokenInfo {
        return TokenInfo(tokenContractAddress, "", symbol, 0, true, chainId)
    }
}
