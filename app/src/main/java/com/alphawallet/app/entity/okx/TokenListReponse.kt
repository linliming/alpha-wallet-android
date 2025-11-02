package com.alphawallet.app.entity.okx

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class TokenListReponse {
    @SerializedName("code")
    @Expose
    var code: String? = null

    @SerializedName("msg")
    @Expose
    var msg: String? = null

    @SerializedName("data")
    @Expose
    var data: List<Data>? = null

    class Data {
        @SerializedName("page")
        @Expose
        var page: String? = null

        @SerializedName("limit")
        @Expose
        var limit: String? = null

        @SerializedName("totalPage")
        @Expose
        var totalPage: String? = null

        @SerializedName("chainFullName")
        @Expose
        var chainFullName: String? = null

        @SerializedName("chainShortName")
        @Expose
        var chainShortName: String? = null

        @SerializedName("tokenList")
        @Expose
        var tokenList: List<TokenDetails>? = null
    }

    class TokenDetails {
        @SerializedName("tokenFullName")
        @Expose
        var tokenFullName: String? = null

        @SerializedName("token")
        @Expose
        var token: String? = null

        @SerializedName("precision")
        @Expose
        var precision: String? = null

        @SerializedName("tokenContractAddress")
        @Expose
        var tokenContractAddress: String? = null

        @SerializedName("protocolType")
        @Expose
        var protocolType: String? = null

        @SerializedName("addressCount")
        @Expose
        var addressCount: String? = null

        @SerializedName("totalSupply")
        @Expose
        var totalSupply: String? = null

        @SerializedName("circulatingSupply")
        @Expose
        var circulatingSupply: String? = null

        @SerializedName("price")
        @Expose
        var price: String? = null

        @SerializedName("website")
        @Expose
        var website: String? = null

        @SerializedName("totalMarketCap")
        @Expose
        var totalMarketCap: String? = null
    }
}
