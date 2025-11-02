package com.alphawallet.app.entity.notification

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class DataMessage {
    @SerializedName("title")
    @Expose
    var title: Title? = null

    @SerializedName("body")
    @Expose
    var body: Body? = null

    class Title {
        @SerializedName("contract")
        @Expose
        var contract: String? = null

        @SerializedName("wallet")
        @Expose
        var wallet: String? = null

        @SerializedName("createdAt")
        @Expose
        var createdAt: String? = null

        @SerializedName("chainId")
        @Expose
        var chainId: String? = null

        @SerializedName("event")
        @Expose
        var event: String? = null
    }

    class Body {
        @SerializedName("to")
        @Expose
        var to: String? = null

        @SerializedName("from")
        @Expose
        var from: String? = null

        @SerializedName("chain")
        @Expose
        var chain: String? = null

        @SerializedName("event")
        @Expose
        var event: String? = null

        @SerializedName("contract")
        @Expose
        var contract: String? = null

        @SerializedName("blockNumber")
        @Expose
        var blockNumber: String? = null

        @SerializedName("contractType")
        @Expose
        var contractType: String? = null
    }
}
