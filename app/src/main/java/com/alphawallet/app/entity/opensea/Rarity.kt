package com.alphawallet.app.entity.opensea

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class Rarity {
    @SerializedName("strategy_id")
    @Expose
    var strategyId: String? = null

    @SerializedName("strategy_version")
    @Expose
    var strategyVersion: String? = null

    @JvmField
    @SerializedName("rank")
    @Expose
    var rank: Long = 0

    @SerializedName("score")
    @Expose
    var score: Double = 0.0

    @SerializedName("max_rank")
    @Expose
    var maxRank: Long = 0

    @SerializedName("tokens_scored")
    @Expose
    var tokensScored: Long = 0
}
