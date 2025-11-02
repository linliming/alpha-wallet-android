package com.alphawallet.app.entity

import com.alphawallet.app.entity.attestation.AttestationCoreData
import com.alphawallet.app.service.KeystoreAccountService
import org.json.JSONArray
import org.json.JSONObject
import org.web3j.abi.datatypes.Address
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigInteger

/**
 * Represents an EAS (Ethereum Attestation Service) signature payload and exposes utilities for
 * serialising it into EIP-712 JSON structures or the core attestation byte payload.
 */
class EasAttestation(
    var version: String,
    var chainId: Long,
    var verifyingContract: String,
    var r: String,
    var s: String,
    v: Long,
    var signer: String,
    var uid: String,
    schema: String,
    var recipient: String,
    var time: Long,
    var expirationTime: Long,
    refUID: String,
    var revocable: Boolean,
    var data: String,
    var nonce: Long,
    var messageVersion: Long,
) {
    private var vValue: Long = v
    private val schemaValue: String = schema
    private var refUidValue: String = refUID

    fun getV(): Long {
        if (vValue == 0L || vValue == 1L) {
            vValue += 27
        }
        return vValue
    }

    fun setV(value: Long) {
        vValue = value
    }

    fun getSchema(): String {
        val schemaVal = BigInteger(Numeric.cleanHexPrefix(schemaValue), 16)
        return if (schemaVal == BigInteger.ZERO) {
            Numeric.toHexStringWithPrefixZeroPadded(BigInteger.ZERO, 64)
        } else {
            schemaValue
        }
    }

    fun getRefUID(): String =
        if (refUidValue == "0") {
            Numeric.toHexStringWithPrefixZeroPadded(BigInteger.ZERO, 64)
        } else {
            refUidValue
        }

    fun setRefUID(value: String) {
        refUidValue = value
    }

    fun getSignatureBytes(): ByteArray {
        val rBytes = Numeric.hexStringToByteArray(r)
        val sBytes = Numeric.hexStringToByteArray(s)
        val vByte = (getV() and 0xFF).toByte()
        val sig = Sign.SignatureData(vByte, rBytes, sBytes)
        return KeystoreAccountService.bytesFromSignature(sig)
    }

    fun getEIP712Attestation(): String {
        val eip712 = JSONObject()
        try {
            val types = JSONObject()
            val domainTypes = JSONArray().apply {
                this@EasAttestation.putElement(this, "name", "string")
                this@EasAttestation.putElement(this, "version", "string")
                this@EasAttestation.putElement(this, "chainId", "uint256")
                this@EasAttestation.putElement(this, "verifyingContract", "address")
            }
            types.put("EIP712Domain", domainTypes)

            val attestTypes = JSONArray().apply {
                if (messageVersion > 0) {
                    this@EasAttestation.putElement(this, "version", "uint16")
                }
                this@EasAttestation.putElement(this, "schema", "bytes32")
                this@EasAttestation.putElement(this, "recipient", "address")
                this@EasAttestation.putElement(this, "time", "uint64")
                this@EasAttestation.putElement(this, "expirationTime", "uint64")
                this@EasAttestation.putElement(this, "revocable", "bool")
                this@EasAttestation.putElement(this, "refUID", "bytes32")
                this@EasAttestation.putElement(this, "data", "bytes")
            }
            types.put("Attest", attestTypes)

            eip712.put("types", types)

            val jsonDomain = JSONObject().apply {
                put("name", "EAS Attestation")
                put("version", version)
                put("chainId", chainId)
                put("verifyingContract", verifyingContract)
            }

            eip712.put("primaryType", "Attest")
            eip712.put("domain", jsonDomain)
            eip712.put("message", formMessage())
        } catch (e: Exception) {
            Timber.e(e)
        }

        return eip712.toString()
    }

    fun getEIP712Message(): String =
        try {
            formMessage().toString()
        } catch (e: Exception) {
            Timber.e(e)
            ""
        }

    @Throws(Exception::class)
    private fun formMessage(): JSONObject {
        return JSONObject().apply {
            if (messageVersion > 0) {
                put("version", messageVersion)
            }
            put("time", time)
            put("data", data)
            put("expirationTime", expirationTime)
            put("recipient", recipient)
            put("refUID", getRefUID())
            put("revocable", revocable)
            put("schema", getSchema())
        }
    }

    @Throws(Exception::class)
    private fun putElement(jsonType: JSONArray, name: String, type: String) {
        val element = JSONObject().apply {
            put("name", name)
            put("type", type)
        }
        jsonType.put(element)
    }

    fun getAttestationCore(): AttestationCoreData {
        val refVal = BigInteger(refUidValue)
        val refBytes = Numeric.toBytesPadded(refVal, 32)
        val schemaBytes = Numeric.toBytesPadded(Numeric.toBigInt(schemaValue), 32)
        val dataBytes = Numeric.hexStringToByteArray(data)

        return AttestationCoreData(
            schemaBytes,
            Address(recipient),
            time,
            expirationTime,
            revocable,
            refBytes,
            dataBytes,
        )
    }
}
