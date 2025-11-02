package com.alphawallet.app.entity

import android.os.Parcel
import android.os.Parcelable
import android.util.Base64
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.token.entity.MagicLinkData
import com.alphawallet.token.entity.MessageData
import com.alphawallet.token.entity.SalesOrderMalformed
import com.alphawallet.token.tools.ParseMagicLink
import com.alphawallet.token.tools.ParseMagicLink.currencyLink
import com.alphawallet.token.tools.ParseMagicLink.spawnable
import timber.log.Timber
import java.math.BigInteger

/**
 * Parcelable wrapper around [MagicLinkData] used for passing orders between activities.
 */
class MagicLinkParcel : Parcelable {
    val magicLink: MagicLinkData

    /**
     * Wraps an existing [MagicLinkData] instance.
     */
    constructor(data: MagicLinkData) {
        magicLink = data
    }

    /**
     * Builds the parcel from raw magic-link payload values.
     *
     * @throws SalesOrderMalformed when the signature or payload cannot be parsed.
     */
    @Throws(SalesOrderMalformed::class)
    constructor(
        price: Double,
        expiry: Long,
        ticketStart: Int,
        ticketCount: Int,
        contractAddress: String,
        sig: String,
        msg: String,
        parser: ParseMagicLink,
    ) {
        magicLink = MagicLinkData()
        magicLink.message = Base64.decode(msg, Base64.URL_SAFE)
        magicLink.price = price
        magicLink.expiry = expiry
        magicLink.ticketStart = ticketStart
        magicLink.ticketCount = ticketCount
        magicLink.contractAddress = contractAddress

        val messageData: MessageData = parser.readByteMessage(
            magicLink.message,
            Base64.decode(sig, Base64.URL_SAFE),
            ticketCount,
        )

        magicLink.priceWei = messageData.priceWei
        magicLink.indices = messageData.tickets
        messageData.signature.copyInto(magicLink.signature, endIndex = 65)
    }

    private constructor(parcel: Parcel) {
        magicLink = MagicLinkData()
        magicLink.expiry = parcel.readLong()
        magicLink.price = parcel.readDouble()
        magicLink.ticketStart = parcel.readInt()
        magicLink.ticketCount = parcel.readInt()
        magicLink.contractAddress = parcel.readString()
        val ticketLength = parcel.readInt()
        magicLink.indices = IntArray(ticketLength)
        parcel.readIntArray(magicLink.indices)
        val sigLength = parcel.readInt()
        val signature = ByteArray(sigLength)
        parcel.readByteArray(signature)
        magicLink.signature = signature
        val messageLength = parcel.readInt()
        magicLink.message = ByteArray(messageLength)
        parcel.readByteArray(magicLink.message)
        magicLink.priceWei = parcel.readString()?.let { BigInteger(it) }
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(magicLink.expiry)
        dest.writeDouble(magicLink.price)
        dest.writeInt(magicLink.ticketStart)
        dest.writeInt(magicLink.ticketCount)
        dest.writeString(magicLink.contractAddress)
        val indices = magicLink.indices ?: IntArray(0)
        dest.writeInt(indices.size)
        dest.writeIntArray(indices)
        val signature = magicLink.signature ?: ByteArray(0)
        dest.writeInt(signature.size)
        dest.writeByteArray(signature)
        val message = magicLink.message ?: ByteArray(0)
        dest.writeInt(message.size)
        dest.writeByteArray(message)
        dest.writeString(magicLink.priceWei?.toString(10))
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<MagicLinkParcel> = object : Parcelable.Creator<MagicLinkParcel> {
            override fun createFromParcel(source: Parcel): MagicLinkParcel = MagicLinkParcel(source)

            override fun newArray(size: Int): Array<MagicLinkParcel?> = arrayOfNulls(size)
        }

        /**
         * Generates the reverse trade calldata required to fulfil the supplied order.
         */
        @JvmStatic
        fun generateReverseTradeData(
            order: MagicLinkData,
            token: Token,
            recipient: String,
        ): ByteArray? {
            return try {
                val expiry = BigInteger.valueOf(order.expiry)
                val sellerSig = CryptoFunctions.sigFromByteArray(order.signature)
                val v = BigInteger(sellerSig?.v).toInt()
                sellerSig?.let {
                    when (order.contractType) {
                        spawnable -> TokenRepository.createSpawnPassTo(
                            token,
                            expiry,
                            order.tokenIds,
                            v,
                            it.r,
                            it.s,
                            recipient,
                        )

                        currencyLink -> TokenRepository.createDropCurrency(order, v, it.r, it.s, recipient)

                        else -> {
                            val tokenElements = (order.indices ?: IntArray(0)).map { BigInteger.valueOf(it.toLong()) }
                            TokenRepository.createTrade(token, expiry, tokenElements, v, it.r, it.s)
                        }
                    }
                }

            } catch (e: Exception) {
                Timber.e(e)
                null
            }
        }
    }
}
