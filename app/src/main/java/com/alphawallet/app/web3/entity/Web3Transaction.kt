package com.alphawallet.app.web3.entity

import android.content.Context
import android.graphics.Typeface
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import android.text.style.StyleSpan
import com.alphawallet.app.R
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.analytics.ActionSheetMode
import com.alphawallet.app.entity.walletconnect.SignType
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.util.BalanceUtils.getScaledValueWithLimit
import com.alphawallet.app.util.BalanceUtils.weiToGwei
import com.alphawallet.app.util.Hex
import com.alphawallet.app.util.StyledStringBuilder
import com.alphawallet.app.walletconnect.entity.WCEthereumTransaction
import java.math.BigDecimal
import java.math.BigInteger

class Web3Transaction : Parcelable {
    @JvmField
    val recipient: Address?
    @JvmField
    val contract: Address?
    @JvmField
    val sender: Address?
    @JvmField
    val value: BigInteger?
    @JvmField
    val gasPrice: BigInteger?
    @JvmField
    val gasLimit: BigInteger?

    // EIP1559
    @JvmField
    var maxFeePerGas: BigInteger?
    @JvmField
    var maxPriorityFeePerGas: BigInteger?

    @JvmField
    val nonce: Long
    @JvmField
    val payload: String?
    @JvmField
    val leafPosition: Long
    @JvmField
    val description: String?

    constructor(
        sender: Address?,
        contract: Address?,
        value: BigInteger?,
        gasPrice: BigInteger?,
        gasLimit: BigInteger?,
        nonce: Long,
        payload: String?
    ) {
        this.recipient = contract
        this.sender = sender
        this.contract = contract
        this.value = value
        this.gasPrice = gasPrice
        this.gasLimit = gasLimit
        this.nonce = nonce
        this.payload = payload
        this.leafPosition = 0
        this.description = null
        this.maxPriorityFeePerGas = BigInteger.ZERO
        this.maxFeePerGas = BigInteger.ZERO
    }

    constructor(
        recipient: Address?,
        contract: Address?,
        value: BigInteger?,
        gasPrice: BigInteger?,
        gasLimit: BigInteger?,
        nonce: Long,
        payload: String?,
        description: String?
    ) {
        this.sender = null
        this.recipient = recipient
        this.contract = contract
        this.value = value
        this.gasPrice = gasPrice
        this.gasLimit = gasLimit
        this.nonce = nonce
        this.payload = payload
        this.leafPosition = 0
        this.description = description
        this.maxFeePerGas = BigInteger.ZERO
        this.maxPriorityFeePerGas = BigInteger.ZERO
    }

    constructor(
        recipient: Address?,
        contract: Address?,
        value: BigInteger?,
        maxFee: BigInteger?,
        maxPriorityFee: BigInteger?,
        gasLimit: BigInteger?,
        nonce: Long,
        payload: String?,
        description: String?
    ) {
        this.recipient = recipient
        this.contract = contract
        this.sender = null
        this.value = value
        this.gasPrice = BigInteger.ZERO
        this.gasLimit = gasLimit
        this.nonce = nonce
        this.payload = payload
        this.leafPosition = 0
        this.description = description
        this.maxFeePerGas = maxFee
        this.maxPriorityFeePerGas = maxPriorityFee
    }

    constructor(
        recipient: Address?,
        contract: Address?,
        value: BigInteger?,
        gasPrice: BigInteger?,
        gasLimit: BigInteger?,
        nonce: Long,
        payload: String?,
        leafPosition: Long
    ) {
        this.recipient = recipient
        this.contract = contract
        this.sender = null
        this.value = value
        this.gasPrice = gasPrice
        this.gasLimit = gasLimit
        this.nonce = nonce
        this.payload = payload
        this.leafPosition = leafPosition
        this.description = null
        this.maxFeePerGas = BigInteger.ZERO
        this.maxPriorityFeePerGas = BigInteger.ZERO
    }

    constructor(
        recipient: Address?,
        contract: Address?,
        sender: Address?,
        value: BigInteger?,
        gasPrice: BigInteger?,
        gasLimit: BigInteger?,
        nonce: Long,
        payload: String?,
        leafPosition: Long
    ) {
        this.recipient = recipient
        this.contract = contract
        this.sender = sender
        this.value = value
        this.gasPrice = gasPrice
        this.gasLimit = gasLimit
        this.nonce = nonce
        this.payload = payload
        this.leafPosition = leafPosition
        this.description = null
        this.maxFeePerGas = BigInteger.ZERO
        this.maxPriorityFeePerGas = BigInteger.ZERO
    }

    constructor(
        recipient: Address?,
        contract: Address?,
        sender: Address?,
        value: BigInteger?,
        maxFee: BigInteger?,
        maxPriorityFee: BigInteger?,
        gasLimit: BigInteger?,
        nonce: Long,
        payload: String?,
        leafPosition: Long
    ) {
        this.recipient = recipient
        this.contract = contract
        this.sender = sender
        this.value = value
        this.gasPrice = BigInteger.ZERO
        this.gasLimit = gasLimit
        this.nonce = nonce
        this.payload = payload
        this.leafPosition = leafPosition
        this.description = null
        this.maxFeePerGas = maxFee
        this.maxPriorityFeePerGas = maxPriorityFee
    }

    /**
     * Initialise from WalletConnect Transaction
     *
     * @param wcTx
     * @param callbackId
     */
    constructor(wcTx: WCEthereumTransaction, callbackId: Long, signType: SignType) {
        val gasPrice = wcTx.gasPrice ?: "0"
        //WC2 uses "gas" for gas limit
        val gasLimit = wcTx.gas ?: "0"
        val nonce = wcTx.nonce ?: ""

        this.recipient = if (TextUtils.isEmpty(wcTx.to)) Address.EMPTY else Address(
            wcTx.to!!
        )
        this.contract = null
        this.sender = if (TextUtils.isEmpty(wcTx.from)) Address.EMPTY else Address(wcTx.from)
        this.value = if (wcTx.value == null) BigInteger.ZERO else Hex.hexToBigInteger(
            wcTx.value,
            BigInteger.ZERO
        )
        this.gasPrice = Hex.hexToBigInteger(gasPrice, BigInteger.ZERO)
        this.gasLimit = Hex.hexToBigInteger(gasLimit, BigInteger.ZERO)
        this.maxFeePerGas = Hex.hexToBigInteger(wcTx.maxFeePerGas, BigInteger.ZERO)
        this.maxPriorityFeePerGas = Hex.hexToBigInteger(wcTx.maxPriorityFeePerGas, BigInteger.ZERO)
        this.nonce = Hex.hexToLong(nonce, -1)
        this.payload = wcTx.data
        this.leafPosition = callbackId
        this.description = signType.ordinal.toString()
    }

    val signType: SignType
        get() {
            if (description != null && description.length == 1 && Character.isDigit(
                    description[0]
                )
            ) {
                val ordinal = description.toInt()
                return SignType.entries[ordinal]
            } else {
                return SignType.SEND_TX
            }
        }

    /**
     * Initialise from previous Transaction for Resending (Speeding up or cancelling)
     *
     * @param tx
     * @param mode
     * @param minGas
     */
    constructor(tx: Transaction, mode: ActionSheetMode, minGas: BigInteger?) {
        recipient = Address(tx.to)
        contract = Address(tx.to)
        value =
            if (mode == ActionSheetMode.CANCEL_TRANSACTION) BigInteger.ZERO else BigInteger(tx.value)
        gasPrice = minGas
        gasLimit = BigInteger(tx.gasUsed)
        nonce = tx.nonce.toLong()
        payload = if (mode == ActionSheetMode.CANCEL_TRANSACTION) "0x" else tx.input
        leafPosition = -1
        description = null
        maxFeePerGas = BigInteger.ZERO
        maxPriorityFeePerGas = BigInteger.ZERO
        sender = Address(tx.from)
    }

    internal constructor(`in`: Parcel) {
        recipient = `in`.readParcelable(Address::class.java.classLoader)
        contract = `in`.readParcelable(Address::class.java.classLoader)
        sender = `in`.readParcelable(Address::class.java.classLoader)
        value = BigInteger(`in`.readString())
        gasPrice = BigInteger(`in`.readString())
        gasLimit = BigInteger(`in`.readString())
        maxFeePerGas = BigInteger(`in`.readString())
        maxPriorityFeePerGas = BigInteger(`in`.readString())
        nonce = `in`.readLong()
        payload = `in`.readString()
        leafPosition = `in`.readLong()
        description = `in`.readString()
    }

    val transactionDestination: Address?
        get() {
            return if (this.contract != null) {
                contract
            } else {
                recipient
            }
        }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(recipient, flags)
        dest.writeParcelable(contract, flags)
        dest.writeParcelable(sender, flags)
        dest.writeString((value ?: BigInteger.ZERO).toString())
        dest.writeString((gasPrice ?: BigInteger.ZERO).toString())
        dest.writeString((gasLimit ?: BigInteger.ZERO).toString())
        dest.writeString((if (maxFeePerGas == null) BigInteger.ZERO else maxFeePerGas).toString())
        dest.writeString((if (maxPriorityFeePerGas == null) BigInteger.ZERO else maxPriorityFeePerGas).toString())
        dest.writeLong(nonce)
        dest.writeString(payload)
        dest.writeLong(leafPosition)
        dest.writeString(description)
    }

    val isConstructor: Boolean
        get() = ((TextUtils.isEmpty(recipient.toString()) || recipient == Address.EMPTY) && payload != null) && (payload.startsWith(
            "0x6080"
        ) || payload.lowercase().startsWith("0x5b6080"))

    val isBaseTransfer: Boolean
        get() = payload == null || payload == "0x"

    /**
     * Can be used anywhere to generate an 'instant' human readable transaction dump
     *
     * @param ctx
     * @param chainId
     * @return
     */
    fun getFormattedTransaction(ctx: Context, chainId: Long, symbol: String?): CharSequence {
        val sb = StyledStringBuilder()
        sb.startStyleGroup().append(ctx.getString(R.string.recipient)).append(": \n")
        sb.setStyle(StyleSpan(Typeface.BOLD))
        sb.append(recipient.toString()).append("\n")

        sb.startStyleGroup().append("\n").append(ctx.getString(R.string.value)).append(": \n")
        sb.setStyle(StyleSpan(Typeface.BOLD))
        sb.append(getScaledValueWithLimit(BigDecimal(value), 18))
        sb.append(" ").append(symbol).append("\n")

        sb.startStyleGroup().append("\n").append(ctx.getString(R.string.label_gas_limit))
            .append(": \n")
        sb.setStyle(StyleSpan(Typeface.BOLD))
        sb.append(gasLimit.toString()).append("\n")

        if (nonce >= 0) {
            sb.startStyleGroup().append("\n").append(ctx.getString(R.string.label_nonce))
                .append(": \n")
            sb.setStyle(StyleSpan(Typeface.BOLD))
            sb.append(nonce.toString()).append("\n")
        }

        if (!TextUtils.isEmpty(payload)) {
            sb.startStyleGroup().append("\n").append(ctx.getString(R.string.payload)).append(": \n")
            sb.setStyle(StyleSpan(Typeface.BOLD))
            sb.append(payload).append("\n")
        }

        sb.startStyleGroup().append("\n").append(ctx.getString(R.string.subtitle_network))
            .append(": \n")
        sb.setStyle(StyleSpan(Typeface.BOLD))
        sb.append(EthereumNetworkBase.getNetworkInfo(chainId)?.shortName).append("\n")

        if (isLegacyTransaction) {
            if (gasPrice!!.compareTo(BigInteger.ZERO) > 0) {
                sb.startStyleGroup().append("\n").append(ctx.getString(R.string.label_gas_price))
                    .append(": \n")
                sb.setStyle(StyleSpan(Typeface.BOLD))
                sb.append(weiToGwei(gasPrice)).append("\n")
            }
        } else {
            sb.startStyleGroup().append("\n").append("Max Priority").append(": \n")
            sb.setStyle(StyleSpan(Typeface.BOLD))
            sb.append(weiToGwei(maxPriorityFeePerGas!!)).append("\n")

            sb.startStyleGroup().append("\n").append(ctx.getString(R.string.label_gas_price_max))
                .append(": \n")
            sb.setStyle(StyleSpan(Typeface.BOLD))
            sb.append(weiToGwei(maxFeePerGas!!)).append("\n")
        }

        sb.applyStyles()

        return sb
    }

    /**
     * Use this for debugging; it's sometimes handy to dump these transactions
     *
     * @param chainId
     * @return
     */
    fun getTxDump(chainId: Long): CharSequence {
        val sb = StringBuilder()
        sb.append("Recipient: ").append(recipient.toString()).append(" : ")
        sb.append("Value: ").append(getScaledValueWithLimit(BigDecimal(value), 18)).append(" : ")
        sb.append("Gas Limit: ").append(gasLimit.toString()).append(" : ")
        sb.append("Nonce: ").append(nonce).append(" : ")
        if (!TextUtils.isEmpty(payload)) {
            sb.append("Payload: ").append(payload).append(" : ")
        }

        sb.append("Network: ").append(EthereumNetworkBase.getNetworkInfo(chainId)?.shortName)
            .append(" : ")

        if (isLegacyTransaction) {
            sb.append("Gas Price: ").append(weiToGwei(gasPrice!!)).append(" : ")
        } else {
            sb.append("Max Priority: ").append(weiToGwei(maxPriorityFeePerGas!!)).append(" : ")
            sb.append("Max Fee Gas: ").append(weiToGwei(maxFeePerGas!!)).append(" : ")
        }

        return sb
    }

    fun getWeb3jTransaction(
        walletAddress: String?,
        nonce: Long
    ): org.web3j.protocol.core.methods.request.Transaction {
        return org.web3j.protocol.core.methods.request.Transaction(
            walletAddress,
            BigInteger.valueOf(nonce),
            gasPrice,
            gasLimit,
            recipient.toString(),
            value,
            payload
        )
    }

    val isLegacyTransaction: Boolean
        get() = gasPrice != BigInteger.ZERO || maxFeePerGas!!.compareTo(BigInteger.ZERO) <= 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Web3Transaction> =
            object : Parcelable.Creator<Web3Transaction> {
                override fun createFromParcel(`in`: Parcel): Web3Transaction? {
                    return Web3Transaction(`in`)
                }

                override fun newArray(size: Int): Array<Web3Transaction?> {
                    return arrayOfNulls(size)
                }
            }
    }
}
