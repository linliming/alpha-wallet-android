package com.alphawallet.app.entity.unstoppable

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class GetRecordsResult {
    @SerializedName("meta")
    @Expose
    var meta: Meta? = null

    @kotlin.jvm.JvmField
    @SerializedName("records")
    @Expose
    var records: HashMap<String, String>? = null
}
