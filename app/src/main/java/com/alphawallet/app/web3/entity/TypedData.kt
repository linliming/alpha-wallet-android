package com.alphawallet.app.web3.entity

import android.os.Parcel
import android.os.Parcelable

class TypedData : Parcelable {
    val name: String?
    val type: String?
    val data: Any?

    constructor(name: String?, type: String?, data: Any?) {
        this.name = name
        this.type = type
        this.data = data
    }

    protected constructor(`in`: Parcel) {
        name = `in`.readString()
        type = `in`.readString()
        val type = `in`.readSerializable() as Class<*>?
        data = `in`.readValue(type!!.classLoader)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(name)
        dest.writeString(type)
        dest.writeSerializable(data!!.javaClass)
        dest.writeValue(data)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<TypedData> = object : Parcelable.Creator<TypedData> {
            override fun createFromParcel(`in`: Parcel): TypedData? {
                return TypedData(`in`)
            }

            override fun newArray(size: Int): Array<TypedData?> {
                return arrayOfNulls(size)
            }
        }
    }

}
