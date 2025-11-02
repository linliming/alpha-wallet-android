package com.alphawallet.app.entity

import android.util.Base64
import com.alphawallet.app.util.Utils
import com.alphawallet.token.entity.CryptoFunctionsInterface
import com.alphawallet.token.entity.ProviderTypedData
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.crypto.StructuredDataEncoder
import timber.log.Timber
import wallet.core.jni.Hash
import java.math.BigInteger
import java.security.SignatureException
import java.util.Arrays

class CryptoFunctions : CryptoFunctionsInterface {
    override fun Base64Decode(message: String?): ByteArray? =
        message?.let { Base64.decode(it, Base64.URL_SAFE) }

    override fun Base64Encode(data: ByteArray?): ByteArray? =
        data?.let { Base64.encode(it, Base64.URL_SAFE or Base64.NO_WRAP) }

    @Throws(SignatureException::class)
    override fun signedMessageToKey(data: ByteArray?, signature: ByteArray?): BigInteger? {
        if (data == null || signature == null) return BigInteger.ZERO
        val sigData = sigFromByteArray(signature) ?: return BigInteger.ZERO
        return Sign.signedMessageToKey(data, sigData)
    }

    override fun getAddressFromKey(recoveredKey: BigInteger?): String? =
        recoveredKey?.let { Keys.getAddress(it) } ?: ""

    override fun keccak256(message: ByteArray?): ByteArray? =
        message?.let { Hash.keccak256(it) }

    override fun formatTypedMessage(rawData: Array<ProviderTypedData?>?): CharSequence? {
        val filtered = rawData?.filterNotNull()?.toTypedArray() ?: return null
        return Utils.formatTypedMessage(filtered)
    }

    override fun formatEIP721Message(messageData: String?): CharSequence? {
        if (messageData.isNullOrEmpty()) return ""
        return try {
            val encoder = StructuredDataEncoder(messageData)
            Utils.formatEIP721Message(encoder)
        } catch (e: Exception) {
            Timber.e(e)
            ""
        }
    }

    override fun getChainId(messageData: String?): Long {
        if (messageData.isNullOrEmpty()) return -1
        return try {
            val encoder = StructuredDataEncoder(messageData)
            val domain: Any? = encoder.jsonMessageObject.domain.chainId
            when (domain) {
                is String -> domain.toLongOrNull() ?: -1
                is Number -> domain.toLong()
                else -> domain?.toString()?.toLongOrNull() ?: -1
            }
        } catch (e: Exception) {
            Timber.e(e)
            -1
        }
    }

    override fun getStructuredData(messageData: String?): ByteArray? {
        if (messageData.isNullOrEmpty()) return ByteArray(0)
        return try {
            val encoder = StructuredDataEncoder(messageData)
            encoder.structuredData
        } catch (e: Exception) {
            Timber.e(e)
            ByteArray(0)
        }
    }

    companion object {
        @JvmStatic
        fun sigFromByteArray(sig: ByteArray?): Sign.SignatureData? {
            if (sig == null || sig.size < 64 || sig.size > 65) return null

            var v = sig[64]
            if (v < 27) {
                v = (v + 27).toByte()
            }

            val r = Arrays.copyOfRange(sig, 0, 32)
            val s = Arrays.copyOfRange(sig, 32, 64)

            return Sign.SignatureData(v, r, s)
        }
    }
}
