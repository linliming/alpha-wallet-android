package com.alphawallet.app.entity

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable FAQ entry that holds a question/answer pair or a reference to a string resource.
 */
class HelpItem(
    val question: String,
    val answer: String,
    val resource: Int,
) : Parcelable {

    /** Optional analytics identifier associated with the help item. */
    var eventName: String? = null

    constructor(question: String, answer: String) : this(question, answer, 0)

    constructor(question: String, resource: Int) : this(question, "", resource)

    private constructor(parcel: Parcel) : this(
        question = parcel.readString().orEmpty(),
        answer = parcel.readString().orEmpty(),
        resource = parcel.readInt(),
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(question)
        dest.writeString(answer)
        dest.writeInt(resource)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<HelpItem> = object : Parcelable.Creator<HelpItem> {
            override fun createFromParcel(source: Parcel): HelpItem = HelpItem(source)

            override fun newArray(size: Int): Array<HelpItem?> = arrayOfNulls(size)
        }
    }
}

