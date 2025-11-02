package com.alphawallet.app.entity

import android.os.Parcel
import android.os.Parcelable
import java.math.BigInteger

/**
 * Parcelable container holding the gas price and gas limit for a transaction.
 */
class GasSettings(
    val gasPrice: BigInteger,
    val gasLimit: BigInteger,
) : Parcelable {

    private constructor(parcel: Parcel) : this(
        gasPrice = parcel.readString()?.let { BigInteger(it) } ?: BigInteger.ZERO,
        gasLimit = parcel.readString()?.let { BigInteger(it) } ?: BigInteger.ZERO,
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(gasPrice.toString(10))
        dest.writeString(gasLimit.toString(10))
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<GasSettings> = object : Parcelable.Creator<GasSettings> {
            override fun createFromParcel(source: Parcel): GasSettings = GasSettings(source)

            override fun newArray(size: Int): Array<GasSettings?> = arrayOfNulls(size)
        }
    }
}

