package com.alphawallet.app.util

import android.text.TextUtils
import com.alphawallet.app.C
import com.alphawallet.app.util.pattern.Patterns
import org.web3j.crypto.Keys
import org.web3j.crypto.WalletUtils
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigInteger
import java.util.regex.Matcher
import java.util.regex.Pattern

private const val ISOLATE_NUMERIC = "(0?x?[0-9a-fA-F]+)"
private const val IPFS_INFURA_RESOLVER = "https://alphawallet.infura-ipfs.io"
private const val IPFS_PREFIX = "ipfs://"
private const val IPFS_DESIGNATOR = "/ipfs/"
const val IPFS_MATCHER = "^Qm[1-9A-Za-z]{44}(\\/.*)?$"
private const val TOKEN_ID_CODE = "{id}"

fun String?.isValidUrl(): Boolean {
    if (TextUtils.isEmpty(this)) return false
    val p = Patterns.WEB_URL
    val m = p.matcher(this!!.lowercase())
    return m.matches() || this.isIPFS()
}

fun String?.isAlNum(): Boolean {
    if (this.isNullOrEmpty()) return false
    return this.all { c ->
        Character.isIdeographic(c.code) || Character.isLetterOrDigit(c) || Character.isWhitespace(c) || (c.code in 32..126)
    }
}

fun String?.isValidValue(): Boolean {
    if (this.isNullOrEmpty()) return false
    return this.all { c -> Character.isDigit(c) || c == '.' || c == ',' }
}

private fun String?.getFirstWord(): String {
    if (this.isNullOrEmpty()) return ""
    val trimmed = this.trim()
    val index = trimmed.indices.firstOrNull { i ->
        (i > 4 && !Character.isLetterOrDigit(trimmed[i])) || Character.isWhitespace(trimmed[i])
    } ?: trimmed.length
    return trimmed.substring(0, index).trim()
}

fun String?.getIconisedText(): String {
    if (this.isNullOrEmpty()) return ""
    if (this.length <= 4) return this
    val firstWord = this.getFirstWord()
    return if (firstWord.isNotEmpty()) {
        firstWord.substring(0, firstWord.length.coerceAtMost(5))
    } else {
        ""
    }
}

fun String?.getShortSymbol(): String {
    if (this.isNullOrEmpty()) return ""
    val firstWord = this.getFirstWord()
    return if (firstWord.isNotEmpty()) {
        firstWord.substring(0, firstWord.length.coerceAtMost(C.SHORT_SYMBOL_LENGTH))
    } else {
        ""
    }
}

fun String?.isAddressValid(): Boolean {
    return !this.isNullOrEmpty() && WalletUtils.isValidAddress(this)
}

fun String?.isNumeric(): Boolean {
    if (this.isNullOrEmpty()) return false
    return this.all { Character.digit(it, 10) != -1 }
}

fun String?.isHex(): Boolean {
    if (this.isNullOrEmpty()) return false
    val clean = Numeric.cleanHexPrefix(this)
    return clean.all { Character.digit(it, 16) != -1 }
}

fun String.isolateNumeric(): String {
    return try {
        val regexResult = Pattern.compile(ISOLATE_NUMERIC).matcher(this)
        if (regexResult.find() && regexResult.groupCount() >= 1) {
            regexResult.group(0)
        } else {
            this
        }
    } catch (e: Exception) {
        this // Silent fail
    }
}

fun String?.formatAddress(frontCharCount: Int = 4): String {
    if (!this.isAddressValid()) return "0x"
    val checksumAddress = Keys.toChecksumAddress(this)
    val front = checksumAddress.substring(0, frontCharCount + 2)
    val back = checksumAddress.substring(checksumAddress.length - 4)
    return "$front...$back"
}

fun String.splitAddress(lines: Int): String {
    return Keys.toChecksumAddress(this).splitHex(lines)
}

fun String.splitHex(lines: Int): String {
    var hex = this
    val split = hex.length / lines
    val sb = StringBuilder()
    var addend: Int
    for (i in 0 until (lines - 1)) {
        addend = 0
        if (sb.isNotEmpty()) {
            sb.append(" ")
        } else {
            if (lines % 2 != 0) {
                addend = 1
            }
        }
        sb.append(hex.substring(0, split + addend))
        hex = hex.substring(split + addend)
    }
    sb.append(" ")
    sb.append(hex)
    return sb.toString()
}

fun String?.formatTxHash(frontCharCount: Int = 4): String {
    if (!this.isTxHashValid()) return "0x"
    val checksumAddress = Keys.toChecksumAddress(this)
    val front = checksumAddress.substring(0, frontCharCount + 2)
    val back = checksumAddress.substring(checksumAddress.length - 4)
    return "$front...$back"
}

fun String?.isTxHashValid(): Boolean {
    return !this.isNullOrEmpty() && WalletUtils.isValidAddress(this, 64)
}

fun String.escapeHTML(): String {
    val out = StringBuilder(16.coerceAtLeast(length))
    for (c in this) {
        when (c) {
            '"' -> out.append("&quot;")
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            else -> out.append(c)
        }
    }
    return out.toString()
}

fun String?.getDomainName(): String {
    if (this == null) return ""
    return try {
        val uri = java.net.URI(this)
        val domain = uri.host
        domain.removePrefix("www.")
    } catch (e: Exception) {
        this
    }
}

fun String.isIPFS(): Boolean {
    return contains(IPFS_DESIGNATOR) || startsWith(IPFS_PREFIX) || shouldBeIPFS()
}

fun String.shouldBeIPFS(): Boolean {
    val regexResult: Matcher = Pattern.compile(IPFS_MATCHER).matcher(this)
    return regexResult.find()
}

fun parseIPFS(URL: String): String {
    return resolveIPFS(URL, IPFS_INFURA_RESOLVER)
}

fun resolveIPFS(URL: String, resolver: String): String {
    if (TextUtils.isEmpty(URL)) return URL
    val ipfsIndex = URL.lastIndexOf(IPFS_DESIGNATOR)

    return when {
        ipfsIndex >= 0 -> resolver + URL.substring(ipfsIndex)
        URL.startsWith(IPFS_PREFIX) -> resolver + IPFS_DESIGNATOR + URL.substring(IPFS_PREFIX.length)
        URL.shouldBeIPFS() -> resolver + IPFS_DESIGNATOR + URL // Handle hash-only
        else -> URL
    }
}



fun String?.isTransactionHash(): Boolean {
    if (this == null || (length != 66 && length != 64)) return false
    val cleanInput = Numeric.cleanHexPrefix(this)
    if (cleanInput.length != 64) return false

    return try {
        Numeric.toBigIntNoPrefix(cleanInput)
        true
    } catch (e: NumberFormatException) {
        false
    }
}

fun String.isJson(): Boolean {
    return try {
        org.json.JSONObject(this)
        true
    } catch (e: Exception) {
        false
    }
}

fun String?.stringToBigInteger(): BigInteger {
    if (this.isNullOrEmpty()) return BigInteger.ZERO
    return try {
        if (Numeric.containsHexPrefix(this)) {
            Numeric.toBigInt(this)
        } else {
            BigInteger(this)
        }
    } catch (e: NumberFormatException) {
        Timber.e(e)
        BigInteger.ZERO
    }
}

fun String?.removeDoubleQuotes(): String? = this?.replace("\"", "")

fun String?.parseResponseValue(tokenId: BigInteger): String? {
    return if (this != null && this.contains(TOKEN_ID_CODE)) {
        val formattedTokenId = Numeric.toHexStringNoPrefixZeroPadded(tokenId, 64)
        this.replace(TOKEN_ID_CODE, formattedTokenId)
    } else {
        this
    }
}

fun String?.isDivisibleString(): Boolean {
    return !this.isNullOrEmpty() && this.length <= 64
}
