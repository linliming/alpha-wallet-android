package com.alphawallet.app.entity

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.util.Collections

class Result {
    @SerializedName("count")
    @Expose
    var count: String? = null

    @SerializedName("next")
    @Expose
    var next: String? = null

    @SerializedName("previous")
    @Expose
    var previous: String? = null

    @SerializedName("results")
    @Expose
    var signatures: List<Signature>? = null

    class Signature : Comparable<Signature> {
        @SerializedName("id")
        @Expose
        var id: Long = 0

        @SerializedName("created_at")
        @Expose
        var created_at: String? = null

        @JvmField
        @SerializedName("text_signature")
        @Expose
        var text_signature: String? = null

        @SerializedName("hex_signature")
        @Expose
        var hex_signature: String? = null

        @SerializedName("bytes_signature")
        @Expose
        var bytes_signature: String? = null

        override fun compareTo(signature: Signature): Int {
            return java.lang.Long.compare(id, signature.id)
        }
    }

    val first: Signature?
        get() {
            if (signatures != null && signatures!!.size > 0) {
                Collections.sort(signatures)
                return signatures!![0]
            } else {
                return null
            }
        }
}
