package com.alphawallet.app.entity

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class KnownContract {
    @JvmField
    @SerializedName("MainNet")
    @Expose
    val mainNet: List<UnknownToken>? = null

    @SerializedName("xDAI")
    @Expose
    val xDAI: List<UnknownToken>? = null
}
