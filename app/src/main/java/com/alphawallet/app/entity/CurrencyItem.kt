package com.alphawallet.app.entity

import android.os.Parcel
import android.os.Parcelable
import com.alphawallet.app.service.TickerService

/**
 * Models a fiat currency option displayed in the UI, including selection state and display assets.
 */
class CurrencyItem(
    var code: String,
    var name: String,
    val symbol: String,
    private var flag: Int = -1,
) : Parcelable {
    var isSelected: Boolean = false

    private constructor(parcel: Parcel) : this(
        code = parcel.readString().orEmpty(),
        name = parcel.readString().orEmpty(),
        symbol = parcel.readString().orEmpty(),
        flag = parcel.readInt(),
    ) {
        isSelected = parcel.readInt() == 1
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(name)
        dest.writeString(code)
        dest.writeString(symbol)
        dest.writeInt(flag)
        dest.writeInt(if (isSelected) 1 else 0)
    }

    fun getFlag(): Int = flag

    fun getCurrencyText(value: Double): String =
        "${TickerService.getCurrencyWithoutSymbol(value)} $code"

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<CurrencyItem> = object : Parcelable.Creator<CurrencyItem> {
            override fun createFromParcel(source: Parcel): CurrencyItem = CurrencyItem(source)

            override fun newArray(size: Int): Array<CurrencyItem?> = arrayOfNulls(size)
        }
    }
}
