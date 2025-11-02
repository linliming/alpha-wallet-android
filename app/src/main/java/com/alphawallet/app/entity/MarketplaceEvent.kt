package com.alphawallet.app.entity

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable wrapper for marketplace events propagated through intents.
 */
class MarketplaceEvent(
    val eventName: String,
) : Parcelable {

    private constructor(parcel: Parcel) : this(parcel.readString().orEmpty())

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(eventName)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<MarketplaceEvent> = object : Parcelable.Creator<MarketplaceEvent> {
            override fun createFromParcel(source: Parcel): MarketplaceEvent = MarketplaceEvent(source)

            override fun newArray(size: Int): Array<MarketplaceEvent?> = arrayOfNulls(size)
        }
    }
}

