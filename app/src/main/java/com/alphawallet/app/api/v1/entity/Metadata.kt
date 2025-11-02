package com.alphawallet.app.api.v1.entity

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class Metadata {
    @JvmField
    @SerializedName("appurl")
    @Expose
    var appUrl: String? = null

    @JvmField
    @SerializedName("iconurl")
    @Expose
    var iconUrl: String? = null

    @JvmField
    @SerializedName("name")
    @Expose
    var name: String? = null
}
