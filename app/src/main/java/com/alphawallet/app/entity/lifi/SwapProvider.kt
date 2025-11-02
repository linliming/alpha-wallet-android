package com.alphawallet.app.entity.lifi

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class SwapProvider {
    @JvmField
    @SerializedName("key")
    @Expose
    var key: String? = null

    @JvmField
    @SerializedName("name")
    @Expose
    var name: String? = null

    @JvmField
    @SerializedName("logoURI")
    @Expose
    var logoURI: String? = null

    @JvmField
    @SerializedName("url")
    @Expose
    var url: String? = null

    @JvmField
    var isChecked: Boolean = false
}
