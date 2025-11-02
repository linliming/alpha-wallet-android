package com.alphawallet.app.entity

import android.os.Parcel
import android.os.Parcelable
import com.alphawallet.app.BuildConfig
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.service.KeyService
import com.alphawallet.app.util.BalanceUtils

class Wallet : Parcelable {
    @JvmField
    val address: String?

    @JvmField
    var balance: String?

    @JvmField
    var ENSname: String?

    @JvmField
    var name: String?

    @JvmField
    var type: WalletType

    @JvmField
    var lastBackupTime: Long

    @JvmField
    var authLevel: KeyService.AuthenticationLevel

    @JvmField
    var walletCreationTime: Long

    @JvmField
    var balanceSymbol: String?

    @JvmField
    var ENSAvatar: String?

    @JvmField
    var isSynced: Boolean = false

    lateinit var tokens: Array<Token>

    constructor(address: String?) {
        this.address = address
        this.balance = "-"
        this.ENSname = ""
        this.name = ""
        this.type = WalletType.NOT_DEFINED
        this.lastBackupTime = 0
        this.authLevel = KeyService.AuthenticationLevel.NOT_SET
        this.walletCreationTime = 0
        this.balanceSymbol = ""
        this.ENSAvatar = ""
    }

    private constructor(`in`: Parcel) {
        address = `in`.readString()
        balance = `in`.readString()
        ENSname = `in`.readString()
        name = `in`.readString()
        var t: Int = `in`.readInt()
        type = WalletType.entries[t]
        lastBackupTime = `in`.readLong()
        t = `in`.readInt()
        authLevel =
            KeyService.AuthenticationLevel.entries
                .toTypedArray()
                .get(t)
        walletCreationTime = `in`.readLong()
        balanceSymbol = `in`.readString()
        ENSAvatar = `in`.readString()
    }

    fun setWalletType(wType: WalletType) {
        type = wType
    }

    fun sameAddress(address: String?): Boolean = this.address.equals(address, ignoreCase = true)

    override fun describeContents(): Int = 0

    override fun writeToParcel(
        parcel: Parcel,
        i: Int,
    ) {
        parcel.writeString(address)
        parcel.writeString(balance)
        parcel.writeString(ENSname)
        parcel.writeString(name)
        parcel.writeInt(type.ordinal)
        parcel.writeLong(lastBackupTime)
        parcel.writeInt(authLevel.ordinal)
        parcel.writeLong(walletCreationTime)
        parcel.writeString(balanceSymbol)
        parcel.writeString(ENSAvatar)
    }

    fun setWalletBalance(token: Token): Boolean {
        balanceSymbol = if (token.tokenInfo != null) token.tokenInfo.symbol else "ETH"
        val newBalance = token.getFixedFormattedBalance()
        if (newBalance == balance) {
            return false
        } else {
            balance = newBalance
            return true
        }
    }

    fun zeroWalletBalance(networkInfo: NetworkInfo) {
        if (balance == "-") {
            balanceSymbol = networkInfo.symbol
            balance =
                BalanceUtils.getScaledValueFixed(
                    java.math.BigDecimal.ZERO,
                    0,
                    Token.TOKEN_BALANCE_PRECISION,
                )
        }
    }

    fun canSign(): Boolean = BuildConfig.DEBUG || !watchOnly()

    fun watchOnly(): Boolean = type == WalletType.WATCH

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val wallet = o as Wallet
        return address == wallet.address && type == wallet.type
    }

    override fun hashCode(): Int = java.util.Objects.hash(address, type)

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Wallet> =
            object : Parcelable.Creator<Wallet> {
                override fun createFromParcel(`in`: Parcel): Wallet = Wallet(`in`)

                override fun newArray(size: Int): Array<Wallet?> = arrayOfNulls(size)
            }
    }
}
