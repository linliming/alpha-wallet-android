package com.alphawallet.app.entity

import android.os.Parcel
import android.os.Parcelable

/**
 * Represents a lightweight log entry that can be persisted or sent between Android components.
 */
class Event(
    val eventText: String,
    val timeStamp: Long,
    val chainId: Long,
) : Parcelable {

    /**
     * Provides a stable hash string derived from the event text and timestamp.
     */
    fun getHash(): String {
        val composite = "$eventText-$timeStamp"
        return composite.hashCode().toString()
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(eventText)
        dest.writeLong(timeStamp)
        dest.writeLong(chainId)
    }

    private constructor(parcel: Parcel) : this(
        eventText = parcel.readString().orEmpty(),
        timeStamp = parcel.readLong(),
        chainId = parcel.readLong(),
    )

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Event> = object : Parcelable.Creator<Event> {
            override fun createFromParcel(source: Parcel): Event = Event(source)

            override fun newArray(size: Int): Array<Event?> = arrayOfNulls(size)
        }
    }
}

