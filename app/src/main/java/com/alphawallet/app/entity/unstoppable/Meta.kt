package com.alphawallet.app.entity.unstoppable

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class Meta {
    @SerializedName("resolver")
    @Expose
    var resolver: String? = null

    @SerializedName("blockchain")
    @Expose
    var blockchain: String? = null

    @SerializedName("networkId")
    @Expose
    var networkId: Long = 0

    @SerializedName("registry")
    @Expose
    var registry: String? = null

    @SerializedName("domain")
    @Expose
    var domain: String? = null

    @SerializedName("owner")
    @Expose
    var owner: String? = null

    @SerializedName("reverse")
    @Expose
    var reverse: Boolean = false
}
