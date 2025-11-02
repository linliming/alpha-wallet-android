package com.alphawallet.app.entity.tokens

import android.os.Parcel
import android.os.Parcelable
import java.util.Locale

class TokenInfo : Parcelable {
    @JvmField
    val address: String?
    @JvmField
    val name: String?
    @JvmField
    val symbol: String?
    @JvmField
    val decimals: Int
    @JvmField
    val chainId: Long
    @JvmField
    var isEnabled: Boolean

    constructor(
        address: String?,
        name: String?,
        symbol: String?,
        decimals: Int,
        isEnabled: Boolean,
        chainId: Long
    ) {
        var address = address
        if (address!!.contains("-")) {
            address = address.split("-".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[0]
        }
        if (address != null) {
            this.address = address.lowercase(Locale.getDefault())
        } else {
            this.address = null
        }
        this.name = name
        this.symbol = symbol?.uppercase(Locale.getDefault())
        this.decimals = decimals
        this.isEnabled = isEnabled
        this.chainId = chainId
    }

    constructor() {
        address = ""
        name = ""
        symbol = ""
        decimals = 0
        chainId = 0
        isEnabled = false
    }

    constructor(into: Parcel) {
        address = into.readString()
        name = into.readString()
        symbol = into.readString()
        decimals = into.readInt()
        isEnabled = into.readInt() == 1
        chainId = into.readLong()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(address)
        dest.writeString(name)
        dest.writeString(symbol)
        dest.writeInt(decimals)
        dest.writeInt(if (isEnabled) 1 else 0)
        dest.writeLong(chainId)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<TokenInfo?> = object : Parcelable.Creator<TokenInfo?> {
            override fun createFromParcel(`in`: Parcel): TokenInfo {
                return TokenInfo(`in`)
            }

            override fun newArray(size: Int): Array<TokenInfo?> {
                return arrayOfNulls(size)
            }
        }
    }
}
