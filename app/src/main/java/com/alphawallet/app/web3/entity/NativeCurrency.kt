package com.alphawallet.app.web3.entity

import android.os.Parcel
import android.os.Parcelable

class NativeCurrency : Parcelable {
    var name: String?
    @JvmField
    var symbol: String?
    var decimals: Int

    constructor(name: String?, symbol: String?, decimals: Int) {
        this.name = name
        this.symbol = symbol
        this.decimals = decimals
    }


    protected constructor(`in`: Parcel) {
        name = `in`.readString()
        symbol = `in`.readString()
        decimals = `in`.readInt()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(name)
        dest.writeString(symbol)
        dest.writeInt(decimals)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<NativeCurrency> =
            object : Parcelable.Creator<NativeCurrency> {
                override fun createFromParcel(`in`: Parcel): NativeCurrency? {
                    return NativeCurrency(`in`)
                }

                override fun newArray(size: Int): Array<NativeCurrency?> {
                    return arrayOfNulls(size)
                }
            }
    }
}
