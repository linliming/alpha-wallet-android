package com.alphawallet.app.entity

import android.os.Parcel
import android.os.Parcelable
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.token.entity.ContractInfo

class ContractLocator(
    @JvmField val address: String,
    @JvmField val chainId: Long,
    private val type: ContractType = ContractType.NOT_SET
) : Parcelable {

    constructor(parcel: Parcel) : this(
        address = parcel.readString().orEmpty(),
        chainId = parcel.readLong(),
        type = ContractType.values()[parcel.readInt()]
    )

    fun equals(token: Token?): Boolean =
        token != null &&
            chainId == token.tokenInfo.chainId &&
            address.equals(token.getAddress(), ignoreCase = true)

    fun equals(token: TokenCardMeta): Boolean {
        return TokensRealmSource.databaseKey(chainId, address).equals(token.tokenId, ignoreCase = true)
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(address)
        dest.writeLong(chainId)
        dest.writeInt(type.ordinal)
    }

    companion object {
        @JvmStatic
        fun fromAddresses(addresses: Array<String>, chainId: Long): Array<ContractLocator> {
            return Array(addresses.size) { index ->
                ContractLocator(addresses[index], chainId)
            }
        }

        @JvmStatic
        fun fromContractInfo(contractInfo: ContractInfo): List<ContractLocator> {
            val result = mutableListOf<ContractLocator>()
            for ((chainId, addressList) in contractInfo.addresses) {
                for (address in addressList) {
                    result.add(ContractLocator(address, chainId))
                }
            }
            return result
        }

        @JvmField
        val CREATOR: Parcelable.Creator<ContractLocator> = object : Parcelable.Creator<ContractLocator> {
            override fun createFromParcel(source: Parcel): ContractLocator = ContractLocator(source)

            override fun newArray(size: Int): Array<ContractLocator?> = arrayOfNulls(size)
        }
    }
}
