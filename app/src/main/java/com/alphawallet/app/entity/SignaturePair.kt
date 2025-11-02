package com.alphawallet.app.entity

import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.hardware.SignatureReturnType
import java.math.BigInteger

/**
 * Container mapping a token selection payload to its signature and the constructed message.
 */
class SignaturePair {
    @JvmField
    var selection: ByteArray? = null


     var signature: SignatureFromKey

     var message: String

    @JvmField
    var signatureStr: String = ""

    @JvmField
    var selectionStr: String = ""

    /**
     * Creates a pair directly from an explicit selection string, signature, and message body.
     *
     * @param selection preformatted selection payload (decimal string)
     * @param sig signature bytes returned from the signer (65-bytes uncompressed)
     * @param message complete message string that was signed (including the selection prefix)
     */
    constructor(selection: String, sig: SignatureFromKey, message: String) {
        selectionStr = selection
        signature = sig
        signatureStr = BigInteger(1, signature.signature).toString(DECIMAL_BASE)
        this.message = message
    }

    /**
     * Rehydrates a signature pair from its compact QR-code representation.
     *
     * @param qrMessage combined selection/signature decimal string read from the QR payload
     * @param timeMessage timestamp written during signing, used to rebuild the signed message
     * @param contractAddr contract address scoped to the signature; will be normalised to lowercase
     */
    constructor(qrMessage: String, timeMessage: String, contractAddr: String) {
        val selectionLength =
            qrMessage.substring(0, SELECTION_DESIGNATOR_SIZE).toInt()
        @Suppress("UNUSED_VARIABLE")
        val trailingZeros =
            qrMessage.substring(
                SELECTION_DESIGNATOR_SIZE,
                SELECTION_DESIGNATOR_SIZE + TRAILING_ZEROES_SIZE,
            ).toInt()

        selectionStr = qrMessage.substring(
            0,
            SELECTION_DESIGNATOR_SIZE + TRAILING_ZEROES_SIZE + selectionLength,
        )
        signatureStr = qrMessage.substring(
            SELECTION_DESIGNATOR_SIZE + TRAILING_ZEROES_SIZE + selectionLength,
        )
        message = "$selectionStr,$timeMessage,${contractAddr.lowercase()}"
        selection = selectionStr.toByteArray()

        val sigBi = BigInteger(signatureStr, DECIMAL_BASE)
        var sigBytes = sigBi.toByteArray()
        if (sigBytes.size < SIGNATURE_SIZE) {
            val offset = SIGNATURE_SIZE - sigBytes.size
            val sigCopy = ByteArray(SIGNATURE_SIZE)
            sigBytes.copyInto(sigCopy, destinationOffset = offset)
            for (i in 0 until offset) {
                sigCopy[i] = 0
            }
            sigBytes = sigCopy
        } else if (sigBytes.size > SIGNATURE_SIZE) {
            val sigCopy = ByteArray(SIGNATURE_SIZE)
            java.lang.System.arraycopy(sigBytes, 1, sigCopy, 0, SIGNATURE_SIZE)
            sigBytes = sigCopy
        }

        signature = SignatureFromKey().apply {
            sigType = SignatureReturnType.SIGNATURE_GENERATED
            signature = sigBytes
        }
    }

    /**
     * Concatenates the selection and signature decimal strings for QR encoding.
     *
     * @return decimal payload ready to embed in a QR code
     */
    fun formQRMessage(): String = selectionStr + signatureStr

    /**
     * Determines whether the current pair contains a valid selection string.
     *
     * @return `true` when the selection component is present
     */
    fun isValid(): Boolean = selectionStr.isNotEmpty()

    companion object {
        private const val SELECTION_DESIGNATOR_SIZE = 2
        private const val TRAILING_ZEROES_SIZE = 3
        private const val DECIMAL_BASE = 10
        private const val NIBBLE = 4
        private const val SIGNATURE_SIZE = 65

        /**
         * Produces the decimal-encoded payload describing a list of ERC721 token IDs.
         *
         * Format: `[formatVersion][length][tokenIds]` where `length` is two decimal digits describing
         * the length of the comma-separated token ID list, ensuring the payload stays decimal-only.
         *
         * @throws IllegalArgumentException if [tokenIds] is empty
         */
        @JvmStatic
        fun generateSelection721Tickets(tokenIds: List<BigInteger>): String {
            require(tokenIds.isNotEmpty()) { "tokenIds must not be empty" }
            var prefix = "0"
            val tokenIdsString = tokenIds.joinToString(",") { it.toString(DECIMAL_BASE) }
            prefix += if (tokenIdsString.length < 10) "0" else ""
            prefix += tokenIdsString.length
            return prefix + tokenIdsString
        }

        /**
         * Generates the compact bitfield-based selection string for ERC875 indices.
         *
         * @param indexList list of selected token indices (decimal)
         * @return decimal-only string encoding the bitfield, prefixed with length metadata
         */
        @JvmStatic
        fun generateSelection(indexList: List<BigInteger>): String {
            val sorted = indexList.toMutableList()
            sorted.sort()
            val lowestValue = sorted.first()
            val zeroCount = lowestValue.divide(BigInteger.valueOf(NIBBLE.toLong())).toInt()
            val correctionFactor = zeroCount * NIBBLE

            var bitFieldLookup = BigInteger.ZERO
            for (value in sorted) {
                val adder =
                    BigInteger.valueOf(2).pow(value.toInt() - correctionFactor)
                bitFieldLookup = bitFieldLookup.add(adder)
            }
            val truncatedValueDecimal = bitFieldLookup.toString(DECIMAL_BASE)
            val formatDecimals = "%1$0${SELECTION_DESIGNATOR_SIZE}d"
            val formatZeros = "%1$0${TRAILING_ZEROES_SIZE}d"

            return buildString {
                append(String.format(formatDecimals, truncatedValueDecimal.length))
                append(String.format(formatZeros, zeroCount))
                append(truncatedValueDecimal)
            }
        }

        /**
         * Reconstructs the list of ERC875 indices from a compact selection string.
         *
         * @param selection compact decimal string produced by [generateSelection]
         * @return decoded list of token indices represented by the selection
         */
        @JvmStatic
        fun buildIndexList(selection: String): List<Int> {
            val lengthStr = selection.substring(0, SELECTION_DESIGNATOR_SIZE)
            val selectionLength = lengthStr.toInt()
            val trailingZerosStr = selection.substring(
                SELECTION_DESIGNATOR_SIZE,
                SELECTION_DESIGNATOR_SIZE + TRAILING_ZEROES_SIZE,
            )
            val trailingZeros = trailingZerosStr.toInt()
            val correctionFactor = trailingZeros * NIBBLE

            val selectionStr = selection.substring(
                SELECTION_DESIGNATOR_SIZE + TRAILING_ZEROES_SIZE,
                SELECTION_DESIGNATOR_SIZE + TRAILING_ZEROES_SIZE + selectionLength,
            )
            var bitField = BigInteger(selectionStr, DECIMAL_BASE)

            val indices = mutableListOf<Int>()
            var radix = bitField.lowestSetBit
            while (bitField != BigInteger.ZERO) {
                if (bitField.testBit(radix)) {
                    indices.add(radix + correctionFactor)
                    bitField = bitField.clearBit(radix)
                }
                radix++
            }
            return indices
        }
    }
}
