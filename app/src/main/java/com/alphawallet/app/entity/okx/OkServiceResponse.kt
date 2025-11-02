package com.alphawallet.app.entity.okx

import com.alphawallet.app.entity.OkxEvent
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class OkServiceResponse {
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

        @SerializedName("transactionLists")
        @Expose
        var transactionLists: List<OkxEvent>? = null

        @SerializedName("tokenList")
        @Expose
        var tokenList: List<OkToken>? = null
    }
}
