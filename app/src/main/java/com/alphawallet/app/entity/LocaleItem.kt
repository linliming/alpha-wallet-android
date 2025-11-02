package com.alphawallet.app.entity

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable representation of a selectable locale entry shown in the settings list.
 */
class LocaleItem(
    var name: String,
    var code: String? = null,
    var isSelected: Boolean = false,
) : Parcelable {

    constructor(name: String, isSelected: Boolean) : this(name, null, isSelected)

    constructor(name: String, code: String) : this(name, code, false)

    private constructor(parcel: Parcel) : this(
        name = parcel.readString().orEmpty(),
        code = parcel.readString(),
        isSelected = parcel.readInt() == 1,
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(name)
        dest.writeString(code)
        dest.writeInt(if (isSelected) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<LocaleItem> = object : Parcelable.Creator<LocaleItem> {
            override fun createFromParcel(source: Parcel): LocaleItem = LocaleItem(source)

            override fun newArray(size: Int): Array<LocaleItem?> = arrayOfNulls(size)
        }
    }
}
