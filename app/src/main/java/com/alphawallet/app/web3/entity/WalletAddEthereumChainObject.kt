package com.alphawallet.app.web3.entity

import android.os.Parcel
import android.os.Parcelable
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigInteger

/**
 * Created by JB on 28/07/21
 */
class WalletAddEthereumChainObject : Parcelable {
    @JvmField
    var nativeCurrency: NativeCurrency? = null

    @JvmField
    var blockExplorerUrls: Array<String>? = null

    @JvmField
    var chainName: String? = null
    var chainType: String? = null //ignore this
    var chainId: String? =
        null //this is a hex number with "0x" prefix. If it is without "0x", process it as dec

    @JvmField
    var rpcUrls: Array<String>? = null

    constructor()

    fun getChainId(): Long {
        try {
            return if (Numeric.containsHexPrefix(chainId)) {
                Numeric.toBigInt(chainId).toLong()
            } else {
                BigInteger(chainId).toLong()
            }
        } catch (e: NumberFormatException) {
            Timber.e(e)
            return (0).toLong()
        }
    }

    protected constructor(`in`: Parcel) {
        nativeCurrency = NativeCurrency.CREATOR.createFromParcel(`in`)
        blockExplorerUrls = if (`in`.readInt() == 1) `in`.createStringArray() else null
        chainName = `in`.readString()
        chainId = `in`.readString()
        rpcUrls = if (`in`.readInt() == 1) `in`.createStringArray() else null
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        nativeCurrency!!.writeToParcel(dest, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
        dest.writeInt(if (blockExplorerUrls == null) 0 else 1)
        if (blockExplorerUrls != null) {
            dest.writeStringArray(blockExplorerUrls)
        }
        dest.writeString(chainName)
        dest.writeString(chainId)
        dest.writeInt(if (rpcUrls == null) 0 else 1)
        if (rpcUrls != null) {
            dest.writeStringArray(rpcUrls)
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<WalletAddEthereumChainObject> =
            object : Parcelable.Creator<WalletAddEthereumChainObject> {
                override fun createFromParcel(`in`: Parcel): WalletAddEthereumChainObject? {
                    return WalletAddEthereumChainObject(`in`)
                }

                override fun newArray(size: Int): Array<WalletAddEthereumChainObject?> {
                    return arrayOfNulls(size)
                }
            }
    }
}
