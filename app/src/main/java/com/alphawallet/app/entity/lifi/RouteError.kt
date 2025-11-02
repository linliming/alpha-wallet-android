package com.alphawallet.app.entity.lifi

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class RouteError {
    @SerializedName("tool")
    @Expose
    var tool: String? = null

    @SerializedName("message")
    @Expose
    var message: String? = null

    @SerializedName("errorType")
    @Expose
    var errorType: String? = null

    @SerializedName("code")
    @Expose
    var code: String? = null
}
