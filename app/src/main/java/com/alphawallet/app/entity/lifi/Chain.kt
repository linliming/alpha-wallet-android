package com.alphawallet.app.entity.lifi

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class Chain {
    @SerializedName("key")
    @Expose
    var key: String? = null

    @JvmField
    @SerializedName("name")
    @Expose
    var name: String? = null

    @SerializedName("coin")
    @Expose
    var coin: String? = null

    @JvmField
    @SerializedName("id")
    @Expose
    var id: Long = 0

    @SerializedName("mainnet")
    @Expose
    var mainnet: String? = null

    @SerializedName("logoURI")
    @Expose
    var logoURI: String? = null

    @SerializedName("tokenlistUrl")
    @Expose
    var tokenlistUrl: String? = null

    @SerializedName("multicallAddress")
    @Expose
    var multicallAddress: String? = null

    @JvmField
    @SerializedName("metamask")
    @Expose
    var metamask: Metamask? = null

    var balance: String? = null

    class Metamask {
        @SerializedName("chainId")
        @Expose
        var chainId: String? = null

        @SerializedName("blockExplorerUrls")
        @Expose
        var blockExplorerUrls: List<String>? = null

        @JvmField
        @SerializedName("chainName")
        @Expose
        var chainName: String? = null

        @SerializedName("nativeCurrency")
        @Expose
        var nativeCurrency: NativeCurrency? = null

        @SerializedName("rpcUrls")
        @Expose
        var rpcUrls: List<String>? = null

        class NativeCurrency {
            @SerializedName("name")
            @Expose
            var name: String? = null

            @SerializedName("symbol")
            @Expose
            var symbol: String? = null

            @SerializedName("decimals")
            @Expose
            var decimals: Long = 0
        }
    }
}
