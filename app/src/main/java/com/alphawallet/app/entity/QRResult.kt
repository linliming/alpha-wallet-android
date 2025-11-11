
package com.alphawallet.app.entity

import android.os.Parcel
import android.os.Parcelable
import com.alphawallet.app.util.Utils
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Merepresentasikan payload yang telah di-parse dari hasil pindaian QR (alamat, EIP-681, atestasi, dll.).
 *
 * Kelas ini telah dimutakhirkan untuk menggunakan properti Kotlin (val/var) alih-alih metode get/set ala Java.
 */
class QRResult() : Parcelable {
    // 1. Properti privat (private var) yang sebelumnya memiliki get/set kini menjadi 'var' publik.
    // Ini menggantikan getProtocol(), getFunction(), setFunction(), dll.
    var protocol: String = ""
    var address: String? = null
    var function: String = "" // Menggantikan 'functionStr'

    @JvmField
    var chainId: Long = DEFAULT_CHAIN_ID

    @JvmField
    var weiValue: BigInteger = BigInteger.ZERO

    @JvmField
    var functionDetail: String = ""

    @JvmField
    var gasLimit: BigInteger = BigInteger.ZERO

    @JvmField
    var gasPrice: BigInteger = BigInteger.ZERO

    @JvmField
    var tokenAmount: BigDecimal = BigDecimal.ZERO

    @JvmField
    var functionToAddress: String = ""

    @JvmField
    var type: EIP681Type = EIP681Type.ADDRESS

    // 2. Properti terkomputasi (computed property) untuk 'attestation'.
    // Ini adalah implementasi langsung dari contoh Anda (val ... get() = ...).
    // Ini menggantikan metode getAttestation()
    val attestation: String
        get() = if (type == EIP681Type.ATTESTATION || type == EIP681Type.EAS_ATTESTATION) {
            functionDetail
        } else {
            ""
        }

    init {
        resetDefaults()
    }

    constructor(address: String) : this() {
        protocol = PROTOCOL_ADDRESS
        this.address = address
    }

    constructor(protocol: String, address: String) : this() {
        this.protocol = protocol
        this.address = address
    }

    constructor(data: String, type: EIP681Type) : this() {
        this.type = type
        address = data
    }

    constructor(attestation: String, chainId: Long, contractAddress: String) : this() {
        try {
            val attestationBI = Numeric.toBigInt(attestation)
            if (Utils.isAddressValid(contractAddress) &&
                chainId > 0 &&
                attestationBI.compareTo(BigInteger.ZERO) > 0
            ) {
                this.type = EIP681Type.ATTESTATION
                this.chainId = chainId
                this.address = contractAddress
                this.functionDetail = attestation
            } else {
                throw IllegalArgumentException("Not a valid attestation")
            }
        } catch (_: Exception) {
            this.type = EIP681Type.OTHER
        }
    }

    private constructor(parcel: Parcel) : this() {
        protocol = parcel.readString().orEmpty()
        address = parcel.readString()
        chainId = parcel.readLong()
        function = parcel.readString().orEmpty() // Menggunakan 'function'
        functionDetail = parcel.readString().orEmpty()
        gasLimit = parcel.readString()?.let { BigInteger(it, HEX_RADIX) } ?: BigInteger.ZERO
        gasPrice = parcel.readString()?.let { BigInteger(it, HEX_RADIX) } ?: BigInteger.ZERO
        weiValue = parcel.readString()?.let { BigInteger(it, HEX_RADIX) } ?: BigInteger.ZERO
        tokenAmount = parcel.readString()?.let { BigDecimal(it) } ?: BigDecimal.ZERO
        functionToAddress = parcel.readString().orEmpty()
        val resultType = parcel.readInt()
        val types = EIP681Type.values()
        type = if (resultType in types.indices) types[resultType] else EIP681Type.OTHER
    }

    // 3. Metode get...() dan set...() yang sederhana telah dihapus
    // karena properti publik (var) sudah menggantikannya.
    // Contoh: alih-alih memanggil 'qrResult.getAddress()', Anda kini memanggil 'qrResult.address'
    // Alih-alih 'qrResult.setAddress("...")', Anda kini melakukan 'qrResult.address = "..."'

    /**
     * Membangun prototipe fungsi dan memperbarui tipe berdasarkan parameter yang diberikan.
     */
    fun createFunctionPrototype(params: List<EthTypeParam>) {
        var override = false

        val signatureBuilder = StringBuilder()
        val detailBuilder = StringBuilder()

        if (function.isNotEmpty()) { // Menggunakan 'function'
            signatureBuilder.append(function)
        } else {
            when {
                params.isNotEmpty() && isEip681() -> {
                    override = true
                    type = EIP681Type.TRANSFER
                }
                params.isEmpty() && isEip681() && weiValue.compareTo(BigInteger.ZERO) > 0 -> {
                    type = EIP681Type.PAYMENT
                    return
                }
                params.size == 2 -> {
                    type = EIP681Type.OTHER_PROTOCOL
                }
                isEip681() && Utils.isAddressValid(address.orEmpty()) -> {
                    type = EIP681Type.ADDRESS
                    return
                }
                else -> {
                    type = EIP681Type.OTHER
                    return
                }
            }
        }

        signatureBuilder.append("(")
        detailBuilder.append(signatureBuilder)

        var first = true
        for (param in params) {
            if (!first) {
                signatureBuilder.append(",")
                detailBuilder.append(",")
            }

            signatureBuilder.append(param.type)
            detailBuilder.append(param.type)
            detailBuilder.append("{")
            detailBuilder.append(param.value)
            detailBuilder.append("}")
            first = false

            when (param.type) {
                "uint", "uint256" -> tokenAmount = BigDecimal(param.value)
                "address" -> functionToAddress = param.value
                "token" -> Unit
                "contractAddress" -> {
                    functionToAddress = address.orEmpty()
                    address = param.value
                }
                else -> Unit
            }
        }

        signatureBuilder.append(")")
        detailBuilder.append(")")

        function = signatureBuilder.toString() // Menggunakan 'function'
        functionDetail = detailBuilder.toString()

        if (functionDetail.isNotEmpty() && functionDetail == "()") {
            functionDetail = ""
        }

        if (!override && isEip681()) {
            type = if (function.startsWith("transfer")) { // Menggunakan 'function'
                EIP681Type.TRANSFER
            } else {
                EIP681Type.FUNCTION_CALL
            }
        }

        if (!isEip681() || (type == EIP681Type.FUNCTION_CALL && functionDetail.isEmpty())) {
            type = EIP681Type.OTHER
        }
    }

    // 4. Metode setAttestation() dihapus karena properti 'functionDetail' sudah publik (var)
    // dan dapat diatur langsung: qrResult.functionDetail = "..."

    /**
     * Parcelable contract – tidak ada deskriptor file khusus yang diperlukan.
     */
    override fun describeContents(): Int = 0

    /**
     * Serialisasi objek sehingga dapat dikirim antar komponen Android.
     */
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(protocol)
        parcel.writeString(address)
        parcel.writeLong(chainId)
        parcel.writeString(function) // Menggunakan 'function'
        parcel.writeString(functionDetail)
        parcel.writeString(gasLimit.toString(HEX_RADIX))
        parcel.writeString(gasPrice.toString(HEX_RADIX))
        parcel.writeString(weiValue.toString(HEX_RADIX))
        parcel.writeString(tokenAmount.toString())
        parcel.writeString(functionToAddress)
        parcel.writeInt(type.ordinal)
    }

    private fun resetDefaults() {
        protocol = ""
        address = null
        function = "" // Menggunakan 'function'
        chainId = DEFAULT_CHAIN_ID
        type = EIP681Type.ADDRESS
        functionDetail = ""
        functionToAddress = ""
        tokenAmount = BigDecimal.ZERO
        gasLimit = BigInteger.ZERO
        gasPrice = BigInteger.ZERO
        weiValue = BigInteger.ZERO
        // Hapus 'function = null' yang lama
    }

    private fun isEip681(): Boolean {
        return protocol.equals(PROTOCOL_ETHEREUM, ignoreCase = true)
    }

    // 5. Menghapus 'object QRType'.
    // Ini adalah pola Java-style yang tidak diperlukan di Kotlin.
    // Alih-alih memanggil 'QRResult.QRType.ADDRESS',
    // Anda seharusnya langsung memanggil 'EIP681Type.ADDRESS'.
    // Menghapus 'QRType' membuat API lebih bersih.

    companion object {
        private const val HEX_RADIX = 16
        private const val PROTOCOL_ADDRESS = "address"
        private const val PROTOCOL_ETHEREUM = "ethereum"
        private const val DEFAULT_CHAIN_ID = 1L

        @JvmField
        val CREATOR: Parcelable.Creator<QRResult> = object : Parcelable.Creator<QRResult> {
            override fun createFromParcel(parcel: Parcel): QRResult {
                return QRResult(parcel)
            }

            override fun newArray(size: Int): Array<QRResult?> {
                return arrayOfNulls(size)
            }
        }
    }
}
