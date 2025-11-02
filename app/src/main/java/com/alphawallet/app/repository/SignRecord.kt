package com.alphawallet.app.repository

import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import com.alphawallet.app.repository.entity.RealmWCSignElement

class SignRecord(
    val date: Long,
    val type: String?,
    val message: CharSequence?
) : Parcelable {

    constructor(element: RealmWCSignElement) : this(
        element.signTime,
        element.signType,
        element.signMessage.toString()
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        TextUtils.writeToParcel(message, dest, flags)
        dest.writeLong(date)
        dest.writeString(type)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<SignRecord> {
        override fun createFromParcel(parcel: Parcel): SignRecord {
            val message = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(parcel)
            val date = parcel.readLong()
            val type = parcel.readString()
            return SignRecord(date, type, message)
        }

        override fun newArray(size: Int): Array<SignRecord?> = arrayOfNulls(size)
    }
}
