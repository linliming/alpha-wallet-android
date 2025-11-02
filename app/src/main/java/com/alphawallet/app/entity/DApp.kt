package com.alphawallet.app.entity

import android.os.Parcel
import android.os.Parcelable

/**
 * Represents a decentralised application entry in the browser catalogue, including its metadata and selection state.
 */
class DApp(
    var name: String?,
    var url: String?,
) : Parcelable {

    var category: String? = null
    var description: String? = null
    var added: Boolean = false

    private constructor(parcel: Parcel) : this(
        name = parcel.readString(),
        url = parcel.readString(),
    ) {
        category = parcel.readString()
        description = parcel.readString()
        added = parcel.readByte().toInt() != 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(name)
        dest.writeString(url)
        dest.writeString(category)
        dest.writeString(description)
        dest.writeByte(if (added) 1 else 0)
    }

    override fun describeContents(): Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DApp) return false

        val otherName = other.name
        val otherUrl = other.url

        if (name == null || url == null || otherName == null || otherUrl == null) return false

        return name == otherName && url == otherUrl
    }

    override fun hashCode(): Int {
        val fields = arrayOf(name, url)
        var result = 1
        for (element in fields) {
            result = 31 * result + (element?.hashCode() ?: 0)
        }
        return result
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<DApp> = object : Parcelable.Creator<DApp> {
            override fun createFromParcel(source: Parcel): DApp = DApp(source)

            override fun newArray(size: Int): Array<DApp?> = arrayOfNulls(size)
        }
    }
}

