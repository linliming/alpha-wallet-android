package com.alphawallet.app.entity.tokens

import android.content.Context
import android.text.TextUtils
import com.alphawallet.app.R
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.EasAttestation
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokenscript.TokenscriptFunction.Companion.ZERO_ADDRESS
import com.alphawallet.app.repository.TokensRealmSource.Companion.attestationDatabaseKey
import com.alphawallet.app.repository.entity.RealmAttestation
import com.alphawallet.app.util.Utils
import com.alphawallet.token.entity.AttestationDefinition
import com.alphawallet.token.entity.AttestationValidation
import com.alphawallet.token.entity.AttestationValidationStatus
import com.alphawallet.token.entity.TokenScriptResult
import com.alphawallet.token.tools.TokenDefinition
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Type
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.crypto.StructuredDataEncoder
import org.web3j.utils.Numeric
import timber.log.Timber
import wallet.core.jni.Hash
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.util.Locale

class Attestation(
    tokenInfo: TokenInfo,
    networkName: String,
    private val attestation: ByteArray,
) : Token(tokenInfo, BigDecimal.ONE, System.currentTimeMillis(), networkName, ContractType.ATTESTATION) {

    private var attestationSubject: String? = null
    private var issuerKey: String? = null
    private var validFrom: Long = 0
    private var validUntil: Long = 0
    private val additionalMembers: MutableMap<String, MemberData> = HashMap()
    private var collectionId: String? = null
    private var isValidAttestation: Boolean = false
    private var baseTokenType: ContractType = ContractType.ERC721

    init {
        setAttributeResult(
            BigInteger.ONE,
            TokenScriptResult.Attribute(
                "attestation",
                "attestation",
                BigInteger.ONE,
                Numeric.toHexString(attestation),
            ),
        )
    }

    fun handleValidation(attValidation: AttestationValidation?) {
        if (attValidation == null) {
            return
        }

        attestationSubject = attValidation._subjectAddress
        isValidAttestation = attValidation._isValid
        issuerKey = attValidation._issuerKey

        for ((key, value) in attValidation.additionalMembers.entries) {
            addToMemberData(key, value)
        }

        val ticketId = MemberData.of(SCHEMA_DATA_PREFIX + TICKET_ID, attValidation._attestationId.toString())
        additionalMembers[SCHEMA_DATA_PREFIX + TICKET_ID] = ticketId
    }

    fun handleEASAttestation(attn: EasAttestation, names: List<String>, values: List<Type<*>>, issuer: String) {
        for (index in names.indices) {
            val name = SCHEMA_DATA_PREFIX + names[index]
            val type = values[index]
            addToMemberData(name, type)
        }

        issuerKey = issuer
        attestationSubject = attn.recipient
        validFrom = attn.time
        validUntil = attn.expirationTime
        val currentTime = System.currentTimeMillis() / 1000L
        isValidAttestation = currentTime > validFrom && (validUntil == 0L || currentTime < validUntil)

        additionalMembers[VALID_FROM] = MemberData.of(VALID_FROM, attn.time).setIsTime()
        if (attn.expirationTime > 0) {
            additionalMembers[VALID_TO] = MemberData.of(VALID_TO, attn.expirationTime).setIsTime()
        }
    }

    fun getSchemaUID(): String {
        val attn = easAttestation
        return if (attn != null) {
            attn.getSchema()
        } else {
            Numeric.toHexStringWithPrefixZeroPadded(BigInteger.ZERO, 64)
        }
    }

    fun addTokenScriptAttributes(): String {
        val outerStruct = StringBuilder()
        val innerStruct = StringBuilder()

        outerStruct.append("attest: {\n")
        innerStruct.append("data: {\n")

        for ((key, value) in additionalMembers.entries) {
            if (value.isURI() || value.isBytes()) {
                continue
            }

            if (value.isSchemaValue()) {
                TokenScriptResult.addPair(innerStruct, value.getCleanKey(), value.getString())
            } else {
                TokenScriptResult.addPair(outerStruct, key, value.getString())
            }
        }

        innerStruct.append("\n}")
        outerStruct.append(innerStruct.toString())
        outerStruct.append("\n}")

        return outerStruct.toString()
    }

    fun isValid(): AttestationValidationStatus {
        if (!isValidAttestation) {
            return AttestationValidationStatus.Expired
        }

        if (
            TextUtils.isEmpty(attestationSubject) ||
            (
                    !attestationSubject.equals(getWallet(), ignoreCase = true) &&
                            attestationSubject != "0" &&
                            attestationSubject != ZERO_ADDRESS
                    )
        ) {
            return AttestationValidationStatus.Incorrect_Subject
        }

        return AttestationValidationStatus.Pass
    }

    fun getAttestationUID(): String {
        val identifier = StringBuilder()
        for ((_, member) in additionalMembers.entries) {
            if (member.isURI() || !member.isSchemaValue() || member.isBytes()) {
                continue
            }

            if (identifier.isNotEmpty()) {
                identifier.append("-")
            }
            identifier.append(member.getString())
        }

        return Numeric.toHexStringNoPrefix(Hash.keccak256(identifier.toString().toByteArray(StandardCharsets.UTF_8)))
    }

    private fun getIdFieldValues(td: TokenDefinition?): String {
        val idFields = td?.attestationIdFields
        return if (idFields.isNullOrEmpty()) {
            getAttestationUID()
        } else {
            getFieldDataJoin(idFields)
        }
    }

    private fun getCollectionFieldValues(td: TokenDefinition?): String {
        val collectionKeys = td?.attestationCollectionKeys
        return if (collectionKeys.isNullOrEmpty()) {
            ""
        } else {
            getFieldDataJoin(collectionKeys)
        }
    }

    private fun getFieldDataJoin(keys: List<String>): String {
        val identifier = StringBuilder()
        for (key in keys) {
            var member = additionalMembers[SCHEMA_DATA_PREFIX + key]
            if (member == null) {
                member = additionalMembers[key]
            }
            addToUID(identifier, member)
        }

        return identifier.toString()
    }

    private fun addToUID(identifier: StringBuilder, member: MemberData?) {
        member ?: return
        identifier.append(member.getString())
    }

    fun getAttestationIdHash(td: TokenDefinition?): String {
        val collection = td?.let { getAttestationCollectionId(it) } ?: getAttestationCollectionId()
        val idFields = getIdFieldValues(td)
        val identifier = tokenInfo.chainId.toString() + Numeric.cleanHexPrefix(collection) + idFields
        val hash = Hash.keccak256(identifier.toByteArray(StandardCharsets.UTF_8))
        return Numeric.toHexString(hash)
    }

    override fun getAttestationCollectionId(td: TokenDefinition): String {
        if (td.attestationCollectionKeys.isNullOrEmpty()) {
            return getAttestationCollectionId()
        }

        val collectionIdStr = getCollectionFieldValues(td)
        val collectionPrefix = collectionPrefix
        val collection = collectionPrefix + collectionIdStr
        return Numeric.toHexString(Hash.keccak256(collection.toByteArray(StandardCharsets.UTF_8)))
    }

    override fun getAttestationCollectionId(): String {
        val collectionStr = collectionPrefix + getFieldDataJoin(attestationAttributeKeys)
        return Numeric.toHexString(Hash.keccak256(collectionStr.toByteArray(StandardCharsets.UTF_8)))
    }

    private val collectionPrefix: String
        get() {
            val attn = easAttestation
            return if (attn == null) {
                "No Collection"
            } else {
                Keys.getAddress(recoverPublicKey(attn)).lowercase(Locale.ROOT)
            }
        }

    private val attestationAttributeKeys: List<String>
        get() {
            val keySet = ArrayList<String>()
            for ((key, value) in additionalMembers.entries) {
                if (value.isSchemaValue() && !value.isURI()) {
                    keySet.add(key)
                }
            }
            return keySet
        }

    private fun getCollectionId(eventIds: String): String {
        var collection = ""
        val candidates = eventIds.split(",")
        for (candidate in candidates) {
            val memberData = additionalMembers[SCHEMA_DATA_PREFIX + candidate]
            if (memberData != null) {
                collection = memberData.getString()
                break
            }
        }
        return collection
    }

    override fun getTSKey(): String {
        return if (collectionId != null) {
            "${collectionId}-${tokenInfo.chainId}"
        } else {
            "${getAttestationCollectionId()}-${tokenInfo.chainId}"
        }
    }

    override fun getTSKey(td: TokenDefinition): String {
        return "${getAttestationCollectionId(td)}-${tokenInfo.chainId}"
    }

    fun getCollectionId(): String {
        return collectionId ?: getAttestationCollectionId()
    }

    fun getAttestationDescription(td: TokenDefinition?): String {
        val att = td?.attestation
        return if (att != null && !att.attributes.isNullOrEmpty()) {
            displayTokenScriptAttrs(att)
        } else {
            displayIntrinsicAttrs()
        }
    }

    private fun displayTokenScriptAttrs(att: AttestationDefinition): String {
        val identifier = StringBuilder()
        for ((typeName, attrTitle) in att.attributes.entries) {
            val attrVal = additionalMembers[typeName]
            if (attrVal == null || !attrVal.isSchemaValue()) {
                continue
            }

            val attrValue = getAttrValue(typeName)
            if (!attrValue.isNullOrEmpty()) {
                if (identifier.isNotEmpty()) {
                    identifier.append(" ")
                }
                identifier.append(attrTitle).append(": ").append(attrValue)
            }
        }
        return identifier.toString()
    }

    private fun displayIntrinsicAttrs(): String {
        val identifier = StringBuilder()
        for ((_, value) in additionalMembers.entries) {
            if (value.isSchemaValue() && !value.isURI() && !value.isBytes()) {
                if (identifier.isNotEmpty()) {
                    identifier.append(" ")
                }
                identifier.append(value.getCleanKey()).append(": ").append(value.getString())
            }
        }
        return identifier.toString()
    }

    fun getIssuer(): String? = issuerKey

    fun loadAttestationData(rAtt: RealmAttestation, recoveredIssuer: String) {
        additionalMembers.putAll(getMembersFromJSON(rAtt.subTitle))
        isValidAttestation = rAtt.isValid
        patchLegacyAttestation(rAtt)

        val validFromData = additionalMembers[VALID_FROM]
        val validToData = additionalMembers[VALID_TO]

        validFrom = validFromData?.value?.toLong() ?: 0
        validUntil = validToData?.value?.toLong() ?: 0

        issuerKey = recoveredIssuer
        collectionId = rAtt.collectionId
    }

    fun isEAS(): Boolean {
        val attn = easAttestation
        return attn != null && attn.getSignatureBytes().size == 65 && !TextUtils.isEmpty(attn.version)
    }

    override fun addAssetElements(asset: NFTAsset, ctx: Context) {
        for ((keyRaw, value) in additionalMembers.entries) {
            if (!value.isSchemaValue() || keyRaw.contains(SCRIPT_URI) || value.isBytes()) {
                continue
            }

            var key = keyRaw
            if (key.startsWith(SCHEMA_DATA_PREFIX)) {
                key = key.substring(SCHEMA_DATA_PREFIX.length)
            }

            asset.addAttribute(key, value.getString())
        }

        val validFromData = additionalMembers[VALID_FROM]
        val validToData = additionalMembers[VALID_TO]

        addDateToAttributes(asset, validFromData, R.string.valid_from, ctx)
        addDateToAttributes(asset, validToData, R.string.valid_until, ctx)
    }

    override fun getAttrValue(typeName: String): String {
        val attrVal = additionalMembers[typeName]
        return attrVal?.getString() ?: ""
    }

    private fun addDateToAttributes(asset: NFTAsset, member: MemberData?, resource: Int, ctx: Context) {
        if (member != null && member.value > BigInteger.ZERO) {
            asset.addAttribute(ctx.getString(resource), member.getString())
        }
    }

    private fun patchLegacyAttestation(rAtt: RealmAttestation) {
        if (additionalMembers.isEmpty()) {
            val id = recoverId(rAtt)
            val tId = MemberData.of(SCHEMA_DATA_PREFIX + TICKET_ID, id.toLong())
            additionalMembers[SCHEMA_DATA_PREFIX + TICKET_ID] = tId
        }
    }

    private fun recoverId(rAtt: RealmAttestation): BigInteger {
        return try {
            val index = rAtt.getAttestationKey().lastIndexOf("-")
            BigInteger(rAtt.getAttestationKey().substring(index + 1))
        } catch (e: Exception) {
            BigInteger.ONE
        }
    }

    fun populateRealmAttestation(rAtt: RealmAttestation) {
        rAtt.subTitle = generateMembersJSON()
        rAtt.chains = tokenInfo.chainId.toString()
        rAtt.name = tokenInfo.name
    }

    private fun generateMembersJSON(): String {
        val members = JSONArray()
        for ((_, value) in additionalMembers.entries) {
            members.put(value.element)
        }
        return members.toString()
    }

    override fun getDatabaseKey(): String {
        return attestationDatabaseKey(tokenInfo.chainId, tokenInfo.address.toString(), getAttestationUID())
    }

    fun setBaseTokenType(baseType: ContractType) {
        baseTokenType = baseType
    }

    fun getBaseTokenType(): ContractType = baseTokenType

    private fun addToMemberData(name: String, type: Type<*>?) {
        if (type != null) {
            additionalMembers[name] = MemberData.of(name, type)
        }
    }

    val easAttestation: EasAttestation?
        get() = try {
            val rawAttestation = String(attestation, StandardCharsets.UTF_8)
            val taglessAttestation = Utils.parseEASAttestation(rawAttestation)
            Gson().fromJson(Utils.toAttestationJson(taglessAttestation), EasAttestation::class.java)
        } catch (e: Exception) {
            null
        }

    fun isSmartPass(): Boolean = knownIssuerKey() && orgIsSmartLayer()

    private fun orgIsSmartLayer(): Boolean = getCollectionId(EVENT_IDS).equals(SMART_LAYER, ignoreCase = true)

    fun getRawAttestation(): String {
        val attestationLink = String(attestation, StandardCharsets.UTF_8)
        return Utils.extractRawAttestation(attestationLink)
    }

    fun getStoredCollectionId(): String? = collectionId

    fun getAttestation(): ByteArray = attestation

    override suspend fun getScriptURI(): List<String> {
        val memberData = additionalMembers[SCHEMA_DATA_PREFIX + SCRIPT_URI]
        val uri = memberData?.getString()?.takeIf { it.isNotBlank() }
        return if (uri != null) {
            listOf(uri)
        } else {
            super.getScriptURI()
        }
    }

    override fun getIntrinsicType(name: String): Type<*>? {
        val attn = easAttestation
        return when (name) {
            "attestation" -> attn?.getAttestationCore()
            "attestationSig" -> attn?.let { DynamicBytes(it.getSignatureBytes()) }
            else -> null
        }
    }

    override fun getUUID(): BigInteger {
        return if (isEAS()) {
            Hash.keccak256(Numeric.hexStringToByteArray(easAttestation!!.data)).let { Numeric.toBigInt(it) }
        } else {
            BigInteger.ONE
        }
    }

    fun getAttestationName(td: TokenDefinition?): String {
        val nftAsset = NFTAsset()
        nftAsset.setupScriptElements(td)
        val name = nftAsset.name
        return if (!TextUtils.isEmpty(name)) name else tokenInfo.name.toString()
    }

    private fun recoverPublicKey(attestation: EasAttestation): String {
        var recoveredKey = ""
        try {
            val dataEncoder = StructuredDataEncoder(attestation.getEIP712Attestation())
            val hash = dataEncoder.hashStructuredData()
            val r = Numeric.hexStringToByteArray(attestation.r)
            val s = Numeric.hexStringToByteArray(attestation.s)
            val v = (attestation.getV() and 0xFF).toByte()

            val sig = Sign.SignatureData(v, r, s)
            val key = Sign.signedMessageHashToKey(hash, sig)
            recoveredKey = Numeric.toHexString(Numeric.toBytesPadded(key, 64))
            Timber.i("Public Key: %s", recoveredKey)
        } catch (e: Exception) {
            Timber.w(e)
        }

        return recoveredKey
    }

    private fun getMembersFromJSON(jsonData: String?): Map<String, MemberData> {
        val members: MutableMap<String, MemberData> = HashMap()
        if (jsonData.isNullOrEmpty()) {
            return members
        }

        try {
            val elements = JSONArray(jsonData)
            for (index in 0 until elements.length()) {
                val element = elements.getJSONObject(index)
                members[element.getString("name")] = MemberData.of("JSON",element)
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        return members
    }

    fun knownIssuerKey(): Boolean = getKnownRootIssuers(tokenInfo.chainId).contains(issuerKey)

    private class MemberData  private constructor(val element: JSONObject) {

        // ========== 工厂函数（推荐对外仅使用这些） ==========
        companion object {
            fun of(name: String?, value: Any?): MemberData {
                val key = name ?: ""
                return when (value) {
                    null -> fromString(key, null)
                    is Boolean -> fromBoolean(key, value)
                    is Long -> fromUInt(key, value)
                    is Int -> fromUInt(key, value.toLong())
                    is Short -> fromUInt(key, value.toLong())
                    is Byte -> fromUInt(key, value.toLong())
                    is Double -> fromString(key, value.toString())      // 保留小数精度
                    is Float -> fromString(key, value.toString())
                    is BigInteger -> fromString(key, value.toString())  // 避免溢出
                    is BigDecimal -> fromString(key, value.toPlainString())
                    is org.json.JSONObject -> fromJson(value)
                    is Type<*> -> fromType(key, value)
                    else -> fromString(key, value.toString())
                }
            }

            fun fromBoolean(name: String, value: Boolean): MemberData =
                MemberData(JSONObject().apply {
                    put("name", name)
                    put("type", "boolean")
                    put("value", value)
                })

            /** 用 uint/int/time 这类整数路径；按你原有语义命名为 uint */
            fun fromUInt(name: String, value: Long): MemberData =
                MemberData(JSONObject().apply {
                    put("name", name)
                    put("type", "uint")
                    put("value", value)
                })

            fun fromString(name: String, value: String?): MemberData =
                MemberData(JSONObject().apply {
                    put("name", name)
                    put("type", "string")
                    put("value", value)
                })

            fun fromType(name: String, type: Type<*>): MemberData =
                MemberData(JSONObject().apply {
                    put("name", name)
                    put("type", type.typeAsString)
                    if (type.typeAsString == "bytes") {
                        put("value", Numeric.toHexString(type.value as ByteArray))
                    } else {
                        put("value", type.value)
                    }
                })

            fun fromJson(json: JSONObject): MemberData =
                // 拷贝一份，避免外部 json 后续修改影响内部
                MemberData(JSONObject(json.toString()))
        }


            val value: BigInteger
                get() = try {
                    val type = element.getString("type")
                    if (type.startsWith("uint") || type.startsWith("int") || type.startsWith("time")) {
                        BigInteger.valueOf(element.getLong("value"))
                    } else {
                        BigInteger.ZERO
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                    BigInteger.ZERO
                }

            fun getEncoding(): String = element.toString()

            fun getCleanKey(): String {
                return try {
                    var name = element.getString("name")
                    if (name.startsWith(SCHEMA_DATA_PREFIX)) {
                        name = name.substring(SCHEMA_DATA_PREFIX.length)
                    }
                    name
                } catch (e: JSONException) {
                    ""
                }
            }

            fun getString(): String {
                return try {
                    val type = element.getString("type")
                    if (type == "time") {
                        formatDate(element.getLong("value"))
                    } else {
                        element.getString("value")
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                    ""
                }
            }

            fun isTrue(): Boolean {
                return try {
                    element.getBoolean("value")
                } catch (e: Exception) {
                    Timber.e(e)
                    false
                }
            }

            fun isSchemaValue(): Boolean {
                return try {
                    element.getString("name").startsWith(SCHEMA_DATA_PREFIX)
                } catch (e: Exception) {
                    Timber.e(e)
                    false
                }
            }

            fun isURI(): Boolean {
                return try {
                    element.getString("name").endsWith(SCRIPT_URI)
                } catch (e: Exception) {
                    Timber.e(e)
                    false
                }
            }

            fun setIsTime(): MemberData {
                try {
                    element.put("type", "time")
                } catch (e: Exception) {
                    Timber.e(e)
                }
                return this
            }

            private fun formatDate(time: Long): String {
                val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
                return formatter.format(time * 1000)
            }

            fun isBytes(): Boolean {
                return try {
                    element.getString("type") == "bytes"
                } catch (e: Exception) {
                    Timber.e(e)
                    false
                }
            }
        }



    companion object {
        private const val VALID_FROM = "time"
        private const val VALID_TO = "expirationTime"
        private const val TICKET_ID = "TicketId"
        private const val SCRIPT_URI = "scriptURI"
        private const val EVENT_IDS = "orgId,eventId,devconId"
        private const val SECONDARY_IDS = "version"
        private const val SCHEMA_DATA_PREFIX = "data."
        const val ATTESTATION_SUFFIX = "-att"
        const val EAS_ATTESTATION_TEXT = "EAS Attestation"
        const val EAS_ATTESTATION_SYMBOL = "ATTN"
        private const val SMART_LAYER = "SMARTLAYER"

        fun getDefaultAttestationInfo(chainId: Long, collectionHash: String): TokenInfo {
            return TokenInfo(collectionHash, EAS_ATTESTATION_TEXT, EAS_ATTESTATION_SYMBOL, 0, true, chainId)
        }

        fun getKnownRootIssuers(chainId: Long): List<String> {
            val knownIssuers = ArrayList<String>()
            knownIssuers.add("0x715e50699db0a553119a4eb1cd13808eedc2910d")
            knownIssuers.add("0xA20efc4B9537d27acfD052003e311f762620642D".lowercase(Locale.ROOT))

            if (!com.alphawallet.app.repository.EthereumNetworkBase.hasRealValue(chainId)) {
                knownIssuers.add("0x4461110869a5d65df76b85e2cd8bbfdda2ca6e4d")
            }

            return knownIssuers
        }
    }
}
