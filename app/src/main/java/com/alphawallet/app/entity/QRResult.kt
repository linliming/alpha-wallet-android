package com.alphawallet.app.entity

import android.os.Parcel
import android.os.Parcelable
import com.alphawallet.app.util.Utils
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Represents the parsed payload of a QR scan (addresses, EIP-681 requests, attestations, etc.).
 */
class QRResult() : Parcelable {
    private var protocol: String = ""
    private var address: String? = null
    private var functionStr: String = ""

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
    var function: String? = null

    @JvmField
    var type: EIP681Type = EIP681Type.ADDRESS

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
        functionStr = parcel.readString().orEmpty()
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

    /**
     * Returns the protocol prefix extracted from the QR payload (eg. `ethereum`).
     */
    fun getProtocol(): String = protocol

    /**
     * Returns the primary address or data element carried by the QR payload.
     */
    fun getAddress(): String? = address

    /**
     * Overrides the stored address or data element for downstream processing.
     */
    fun setAddress(address: String) {
        this.address = address
    }

    /**
     * Returns the textual function signature (eg. `transfer`) parsed from the payload.
     */
    fun getFunction(): String = functionStr

    /**
     * Stores the textual function signature that should be associated with the payload.
     */
    fun setFunction(function: String) {
        functionStr = function
    }

    /**
     * Returns a human-readable description of the function arguments captured from the payload.
     */
    fun getFunctionDetail(): String = functionDetail

    /**
     * Returns the requested wei value associated with the payload (defaults to zero).
     */
    fun getValue(): BigInteger = weiValue

    /**
     * Returns the requested gas price parsed from the payload (defaults to zero).
     */
    fun getGasPrice(): BigInteger = gasPrice

    /**
     * Returns the requested gas limit parsed from the payload (defaults to zero).
     */
    fun getGasLimit(): BigInteger = gasLimit

    /**
     * Builds the function prototype and updates type inference based on the supplied parameters.
     */
    fun createFunctionPrototype(params: List<EthTypeParam>) {
        var override = false

        val signatureBuilder = StringBuilder()
        val detailBuilder = StringBuilder()

        if (functionStr.isNotEmpty()) {
            signatureBuilder.append(functionStr)
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

        functionStr = signatureBuilder.toString()
        functionDetail = detailBuilder.toString()

        if (functionDetail.isNotEmpty() && functionDetail == "()") {
            functionDetail = ""
        }

        if (!override && isEip681()) {
            type = if (functionStr.startsWith("transfer")) {
                EIP681Type.TRANSFER
            } else {
                EIP681Type.FUNCTION_CALL
            }
        }

        if (!isEip681() || (type == EIP681Type.FUNCTION_CALL && functionDetail.isEmpty())) {
            type = EIP681Type.OTHER
        }
    }

    /**
     * Stores an attestation payload for later processing or persistence.
     */
    fun setAttestation(attestation: String) {
        functionDetail = attestation
    }

    /**
     * Returns the attestation payload when available, otherwise an empty string.
     */
    fun getAttestation(): String {
        return if (type == EIP681Type.ATTESTATION || type == EIP681Type.EAS_ATTESTATION) {
            functionDetail
        } else {
            ""
        }
    }

    /**
     * Parcelable contract – no special file descriptors are required.
     */
    override fun describeContents(): Int = 0

    /**
     * Serialises the object so it can be passed between Android components.
     */
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(protocol)
        parcel.writeString(address)
        parcel.writeLong(chainId)
        parcel.writeString(functionStr)
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
        functionStr = ""
        chainId = DEFAULT_CHAIN_ID
        type = EIP681Type.ADDRESS
        functionDetail = ""
        functionToAddress = ""
        tokenAmount = BigDecimal.ZERO
        gasLimit = BigInteger.ZERO
        gasPrice = BigInteger.ZERO
        weiValue = BigInteger.ZERO
        function = null
    }

    private fun isEip681(): Boolean {
        return protocol.equals(PROTOCOL_ETHEREUM, ignoreCase = true)
    }

    object QRType {
        @JvmField val ADDRESS: EIP681Type = EIP681Type.ADDRESS
        @JvmField val PAYMENT: EIP681Type = EIP681Type.PAYMENT
        @JvmField val TRANSFER: EIP681Type = EIP681Type.TRANSFER
        @JvmField val FUNCTION_CALL: EIP681Type = EIP681Type.FUNCTION_CALL
        @JvmField val URL: EIP681Type = EIP681Type.URL
        @JvmField val MAGIC_LINK: EIP681Type = EIP681Type.MAGIC_LINK
        @JvmField val OTHER_PROTOCOL: EIP681Type = EIP681Type.OTHER_PROTOCOL
        @JvmField val WALLET_CONNECT: EIP681Type = EIP681Type.WALLET_CONNECT
        @JvmField val ATTESTATION: EIP681Type = EIP681Type.ATTESTATION
        @JvmField val OTHER: EIP681Type = EIP681Type.OTHER
        @JvmField val EAS_ATTESTATION: EIP681Type = EIP681Type.EAS_ATTESTATION
    }

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
