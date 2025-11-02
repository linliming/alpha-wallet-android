package com.alphawallet.app.entity

import org.web3j.utils.Numeric
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date

/**
 * Created by James on 5/02/2018.
 */
class TradeInstance {
    var expiry: BigInteger
    val price: BigInteger
    val tickets: IntArray
    val contractAddress: BigInteger
    val ticketStart: BigInteger
    val publicKey: String
    var signatures: MutableList<ByteArray> = ArrayList()

    constructor(
        price: BigInteger,
        expiry: BigInteger,
        tickets: IntArray,
        contractAddress: String?,
        publicKey: BigInteger,
        ticketStartId: BigInteger
    ) {
        this.price = price
        this.expiry = expiry
        this.tickets = tickets
        val keyBytes = publicKey.toByteArray()
        this.publicKey = padLeft(Numeric.toHexString(keyBytes, 0, keyBytes.size, false), 128)
        this.ticketStart = ticketStartId
        this.contractAddress = Numeric.toBigInt(contractAddress)
    }

    constructor(t: TradeInstance, sig: ByteArray?) {
        this.price = t.price
        this.expiry = t.expiry
        this.tickets = t.tickets
        this.publicKey = t.publicKey
        this.ticketStart = t.ticketStart
        this.contractAddress = t.contractAddress
    }

    fun addSignature(sig: ByteArray) {
        signatures.add(sig)
    }

    fun getStringSig(index: Int): String? {
        var sigStr: String? = null
        if (index < signatures.size) {
            sigStr = String(signatures[index])
        }
        return sigStr
    }

    val expiryString: String
        get() {
            val expire = expiry.toLong()
            val date = Date(expire * 1000L)
            val sdf = SimpleDateFormat("dd-MM HH:mm z")
            //sdf.setTimeZone(TimeZone.getTimeZone("GMT-4"));
            return sdf.format(date)
        }

    fun getSignatureBytes(index: Int): ByteArray? {
        var sig: ByteArray? = null
        if (index < signatures.size) {
            sig = signatures[index]
        }
        return sig
    }

    fun getSignatures(): List<ByteArray> {
        return signatures
    }

    @Throws(Exception::class)
    fun addSignatures(ds: DataOutputStream) {
        //now add the signatures
        for (sig in signatures) {
            ds.write(sig)
        }
    }

    @get:Throws(Exception::class)
    val tradeBytes: ByteArray
        get() {
            val buffer = ByteArrayOutputStream()
            val ds = DataOutputStream(buffer)
            ds.write(Numeric.toBytesPadded(price, 32))
            ds.write(Numeric.toBytesPadded(expiry, 32))
            ds.write(Numeric.toBytesPadded(contractAddress, 20))

            val uint16 = ByteArray(2)
            for (ticketIndex in tickets) {
                //write big endian encoding
                uint16[0] = (ticketIndex shr 8).toByte()
                uint16[1] = (ticketIndex and 0xFF).toByte()
                ds.write(uint16)
            }
            ds.flush()

            return buffer.toByteArray()
        }

    private fun padLeft(source: String, length: Int): String {
        if (source.length > length) return source
        val out = CharArray(length)
        val sourceOffset = length - source.length
        System.arraycopy(source.toCharArray(), 0, out, sourceOffset, source.length)
        Arrays.fill(out, 0, sourceOffset, '0')
        return String(out)
    }

    fun sigCount(): Int {
        return signatures.size
    }
}
