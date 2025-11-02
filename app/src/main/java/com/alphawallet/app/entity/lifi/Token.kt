package com.alphawallet.app.entity.lifi

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.util.Objects

class Token {
    @JvmField
    @SerializedName("address")
    @Expose
    var address: String? = null

    @JvmField
    @SerializedName("symbol")
    @Expose
    var symbol: String? = null

    @JvmField
    @SerializedName("decimals")
    @Expose
    var decimals: Long = 0

    @JvmField
    @SerializedName("chainId")
    @Expose
    var chainId: Long = 0

    @JvmField
    @SerializedName("name")
    @Expose
    var name: String? = null

    @SerializedName("coinKey")
    @Expose
    var coinKey: String? = null

    @JvmField
    @SerializedName("priceUSD")
    @Expose
    var priceUSD: String? = null

    @JvmField
    @SerializedName("logoURI")
    @Expose
    var logoURI: String? = null

    @JvmField
    var balance: String? = null
    @JvmField
    var fiatEquivalent: Double = 0.0

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val lToken = o as Token
        return address == lToken.address && symbol == lToken.symbol
    }

    override fun hashCode(): Int {
        return Objects.hash(address, symbol)
    }

    val isNativeToken: Boolean
        // Note: In the LIFI API, the native token has either of these two addresses.
        get() = address.equals("0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", ignoreCase = true) ||
                address.equals("0x0000000000000000000000000000000000000000", ignoreCase = true)

    val fiatValue: Double
        get() {
            try {
                val value = balance!!.toDouble()
                val priceUSD = priceUSD!!.toDouble()
                return value * priceUSD
            } catch (e: NumberFormatException) {
                return 0.0
            } catch (e: NullPointerException) {
                return 0.0
            }
        }

    fun isSimilarTo(
        aToken: com.alphawallet.app.entity.tokens.Token,
        walletAddress: String?
    ): Boolean {
        if (this.chainId == aToken.tokenInfo.chainId
            && address.equals(aToken.getAddress(), ignoreCase = true)
        ) {
            return true
        }

        return aToken.getAddress().equals(walletAddress, ignoreCase = true) && isNativeToken
    }
}
