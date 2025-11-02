package com.alphawallet.app.entity.nftassets

import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import com.alphawallet.app.entity.opensea.OpenSeaAsset
import com.alphawallet.app.entity.tokens.Attestation
import com.alphawallet.app.entity.tokens.ERC1155Token
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.entity.RealmNFTAsset
import com.alphawallet.app.util.Utils
import com.alphawallet.token.entity.AttestationDefinition
import com.alphawallet.token.tools.TokenDefinition
import kotlinx.coroutines.Job
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Collections
import java.util.Locale

/**
 * Kotlin representation of an NFT asset with metadata and helper utilities.
 */
class NFTAsset() : Parcelable {

    companion object {
        private const val LOADING_TOKEN = "*Loading*"
        private const val ID = "id"
        private const val OPENSEA_ID = "identifier"
        private const val ATTN_ID = "attn_id"
        private const val NAME = "name"
        private const val IMAGE = "image"
        private const val IMAGE_URL = "image_url"
        private const val IMAGE_PREVIEW = "image_preview_url"
        private const val COLLECTION = "collection"
        private const val DESCRIPTION = "description"
        private const val IMAGE_ORIGINAL_URL = "image_original_url"
        private const val IMAGE_ANIMATION = "animation_url"
        private const val ATTESTATION_ASSET = "__Attestation"
        private val IMAGE_DESIGNATORS =
            arrayOf(IMAGE, IMAGE_URL, IMAGE_ORIGINAL_URL, IMAGE_PREVIEW, IMAGE_ANIMATION)
        private val SVG_OVERRIDE =
            arrayOf(IMAGE_ORIGINAL_URL, IMAGE, IMAGE_URL, IMAGE_ANIMATION)
        private val IMAGE_THUMBNAIL_DESIGNATORS =
            arrayOf(IMAGE_PREVIEW, IMAGE, IMAGE_URL, IMAGE_ORIGINAL_URL, IMAGE_ANIMATION)
        private const val BACKGROUND_COLOUR = "background_color"
        private const val EXTERNAL_LINK = "external_link"
        private val DESIRED_PARAMS =
            listOf(
                NAME,
                BACKGROUND_COLOUR,
                IMAGE_URL,
                IMAGE,
                IMAGE_ORIGINAL_URL,
                IMAGE_PREVIEW,
                DESCRIPTION,
                EXTERNAL_LINK,
                IMAGE_ANIMATION,
                COLLECTION,
            )
        private val ATTRIBUTE_DESCRIPTOR = listOf("attributes", "traits")

        @JvmField
        val CREATOR: Parcelable.Creator<NFTAsset> =
            object : Parcelable.Creator<NFTAsset> {
                override fun createFromParcel(parcel: Parcel): NFTAsset = NFTAsset(parcel)

                override fun newArray(size: Int): Array<NFTAsset?> = arrayOfNulls(size)
            }
    }

    private val assetMap: MutableMap<String, String> = HashMap()
    private val attributeMap: MutableMap<String, String> = HashMap()

    var metaDataLoader: Job? = null
    var isChecked: Boolean = false
    var exposeRadio: Boolean = false
    var balance: BigDecimal = BigDecimal.ONE
    private var selected: BigDecimal? = null
    private var tokenIdList: MutableList<BigInteger>? = null
    private var openSeaAsset: OpenSeaAsset? = null

    /**
     * Creates an asset from raw metadata JSON.
     */
    constructor(metaData: String) : this() {
        loadFromMetaData(metaData)
        balance = BigDecimal.ONE
    }

    /**
     * Creates an asset based on Realm storage content.
     */
    constructor(realmAsset: RealmNFTAsset) : this() {
        val metaData =
            realmAsset.metaData ?: NFTAsset(BigInteger(realmAsset.getTokenId())).jsonMetaData()
        loadFromMetaData(metaData)
        balance = realmAsset.getBalance()
    }

    /**
     * Creates a placeholder asset for loading state.
     */
    constructor(tokenId: BigInteger) : this() {
        assetMap.clear()
        attributeMap.clear()
        balance = BigDecimal.ONE
        assetMap[NAME] = "ID #${tokenId}"
        assetMap[LOADING_TOKEN] = "."
    }

    /**
     * Creates an asset representing an attestation.
     */
    constructor(att: Attestation) : this() {
        assetMap[ATTESTATION_ASSET] = att.getName().toString()
        attributeMap[NAME] = "Attestation"
        attributeMap[ID] = att.getAttestationUID()
        balance = BigDecimal.ONE
    }

    /**
     * Copies metadata from an existing asset.
     */
    constructor(asset: NFTAsset) : this() {
        assetMap.putAll(asset.assetMap)
        attributeMap.putAll(asset.attributeMap)
        balance = asset.balance
        tokenIdList = asset.tokenIdList?.let { ArrayList(it) }
        isChecked = asset.isChecked
        exposeRadio = asset.exposeRadio
    }

    /**
     * Restores an asset from a Parcel.
     */
    private constructor(parcel: Parcel) : this() {
        balance = parcel.readString()?.let { BigDecimal(it) } ?: BigDecimal.ONE
        selected = parcel.readString()?.let { BigDecimal(it) }
        val assetCount = parcel.readInt()
        val attrCount = parcel.readInt()
        val tokenIdCount = parcel.readInt()

        repeat(assetCount) {
            val key = parcel.readString()
            val value = parcel.readString()
            if (key != null && value != null) {
                assetMap[key] = value
            }
        }

        repeat(attrCount) {
            val key = parcel.readString()
            val value = parcel.readString()
            if (key != null && value != null) {
                attributeMap[key] = value
            }
        }

        if (tokenIdCount > 0) {
            tokenIdList = ArrayList()
        }

        repeat(tokenIdCount) {
            parcel.readString()?.let { tokenIdList?.add(BigInteger(it)) }
        }
    }

    /**
     * Returns the stored asset value for the supplied key.
     */
    fun getAssetValue(key: String): String? = assetMap[key]

    /**
     * Returns all stored attribute pairs.
     */
    fun getAttributes(): Map<String, String> = attributeMap

    /**
     * Returns an attribute value for the supplied key.
     */
    fun getAttributeValue(key: String): String? = attributeMap[key]

    /**
     * Returns the current display name.
     */
    fun getName(): String? = assetMap[NAME]

    /**
     * Returns the animation URL if present.
     */
    fun getAnimation(): String? = assetMap[IMAGE_ANIMATION]

    /**
     * Resolves and returns the primary image URL.
     */
    fun getImage(): String {
        for (key in IMAGE_DESIGNATORS) {
            if (assetMap.containsKey(key)) {
                return Utils.parseIPFS(assetMap[key])
            }
        }
        return ""
    }

    /**
     * Resolves and returns a thumbnail image URL prioritising SVG assets.
     */
    fun getThumbnail(): String {
        val svgOverride = getSVGOverride()
        if (!TextUtils.isEmpty(svgOverride)) {
            return svgOverride
        }

        for (key in IMAGE_THUMBNAIL_DESIGNATORS) {
            if (assetMap.containsKey(key)) {
                return Utils.parseIPFS(assetMap[key])
            }
        }
        return ""
    }

    /**
     * Returns the background color if defined.
     */
    fun getBackgroundColor(): String? = assetMap[BACKGROUND_COLOUR]

    /**
     * Returns the NFT description.
     */
    fun getDescription(): String? = assetMap[DESCRIPTION]

    /**
     * Returns the external link associated with this asset.
     */
    fun getExternalLink(): String? = assetMap[EXTERNAL_LINK]

    /**
     * Updates the balance and reports whether it changed.
     */
    fun setBalance(value: BigDecimal): Boolean {
        val changed = balance != value
        balance = value
        return changed
    }

    /**
     * Returns the current balance.
     */
    fun getBalance(): BigDecimal = balance

    /**
     * Indicates whether the asset represents multiple units.
     */
    fun isAssetMultiple(): Boolean = balance.compareTo(BigDecimal.ONE) > 0

    /**
     * Parses JSON metadata into local maps.
     */
    private fun loadFromMetaData(metaData: String) {
        try {
            var jsonData = JSONObject(metaData)
            if (jsonData.has("nft")) {
                jsonData = jsonData.getJSONObject("nft")
            }
            val keys: Iterator<String> = jsonData.keys()
            var id: String? = null

            while (keys.hasNext()) {
                val key = keys.next()
                val value = jsonData.getString(key)
                if (key == ID || key == OPENSEA_ID) {
                    id = value
                } else if (!ATTRIBUTE_DESCRIPTOR.contains(key)) {
                    if (validJSONString(value) && DESIRED_PARAMS.contains(key)) {
                        assetMap[key] = value
                    }
                } else {
                    val attrArray = jsonData.getJSONArray(key)
                    for (i in 0 until attrArray.length()) {
                        val order = attrArray.getJSONObject(i)
                        if (validJSONString(order.getString("value"))) {
                            attributeMap[order.getString("trait_type")] =
                                order.getString("value")
                        }
                    }
                }
            }

            if (!TextUtils.isEmpty(getImage()) && TextUtils.isEmpty(getName()) && id != null) {
                assetMap[NAME] = "ID #$id"
            }
        } catch (e: JSONException) {
            // ignore invalid metadata
        }
    }

    /**
     * Checks if a JSON string is usable.
     */
    private fun validJSONString(value: String?): Boolean =
        !value.isNullOrEmpty() && !value.equals("null", ignoreCase = true)

    /**
     * Returns the SVG URL override when available.
     */
    private fun getSVGOverride(): String {
        for (key in SVG_OVERRIDE) {
            val assetValue = assetMap[key]
            if (!assetValue.isNullOrEmpty() && assetValue.lowercase(Locale.ROOT).endsWith("svg")) {
                return Utils.parseIPFS(assetValue)
            }
        }
        return ""
    }

    /**
     * Serialises asset metadata back to JSON.
     */
    fun jsonMetaData(): String {
        val jsonData = JSONObject()
        try {
            for (key in assetMap.keys) {
                jsonData.put(key, assetMap[key])
            }

            if (attributeMap.isNotEmpty()) {
                val attrs = JSONArray()
                var index = 0
                for (key in attributeMap.keys) {
                    val entry =
                        JSONObject()
                            .put("trait_type", key)
                            .put("value", attributeMap[key])
                    attrs.put(index, entry)
                    index++
                }
                jsonData.put("attributes", attrs)
            }
        } catch (e: JSONException) {
            // ignore invalid metadata during serialization
        }
        return jsonData.toString()
    }

    /**
     * Writes the parcelable content.
     */
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(balance.toString())
        dest.writeString(selected?.toString() ?: "0")
        dest.writeInt(assetMap.size)
        dest.writeInt(attributeMap.size)
        dest.writeInt(tokenIdList?.size ?: 0)

        for (key in assetMap.keys) {
            dest.writeString(key)
            dest.writeString(assetMap[key])
        }

        for (key in attributeMap.keys) {
            dest.writeString(key)
            dest.writeString(attributeMap[key])
        }

        tokenIdList?.forEach { dest.writeString(it.toString()) }
    }

    /**
     * Parcelable descriptor implementation.
     */
    override fun describeContents(): Int = 0

    /**
     * Indicates whether metadata is still loading.
     */
    fun needsLoading(): Boolean =
        assetMap.isEmpty() || assetMap.containsKey(LOADING_TOKEN)

    /**
     * Returns true when at least one image is available.
     */
    fun hasImageAsset(): Boolean = !TextUtils.isEmpty(getThumbnail())

    /**
     * Returns whether metadata should be refreshed from network.
     */
    fun requiresReplacement(): Boolean =
        needsLoading() || !assetMap.containsKey(NAME) || TextUtils.isEmpty(getImage())

    /**
     * Generates a combined hash for metadata and balance.
     */
    override fun hashCode(): Int =
        assetMap.hashCode() + attributeMap.hashCode() + balance.hashCode()

    /**
     * Compares this asset instance with another object.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NFTAsset) return false
        return hashCode() == other.hashCode()
    }

    /**
     * Compares this asset with a Realm stored representation.
     */
    fun equals(realmAsset: RealmNFTAsset): Boolean = hashCode() == NFTAsset(realmAsset).hashCode()

    /**
     * Indicates whether asset metadata is empty.
     */
    fun isBlank(): Boolean = assetMap.isEmpty()

    /**
     * Updates this asset using data from an older instance.
     */
    fun updateFromRaw(oldAsset: NFTAsset?) {
        if (oldAsset == null) {
            return
        }

        if (oldAsset.assetMap.containsKey(IMAGE_PREVIEW)) {
            assetMap[IMAGE_PREVIEW] = oldAsset.getAssetValue(IMAGE_PREVIEW) ?: ""
        }
        balance = oldAsset.balance

        updateAsset(oldAsset)

        if (assetMap.size > 1) {
            assetMap.remove(LOADING_TOKEN)
        }

        val previousOpenSea = oldAsset.openSeaAsset
        if (previousOpenSea != null) {
            val osName = previousOpenSea.name
            if (TextUtils.isEmpty(getName()) && !TextUtils.isEmpty(osName)) {
                assetMap[NAME] = osName.toString()
            }

            val osImageUrl = previousOpenSea.imageUrl
            if (TextUtils.isEmpty(getImage()) && !TextUtils.isEmpty(osImageUrl)) {
                assetMap[IMAGE] = osImageUrl.toString()
            }

            val osDescription = previousOpenSea.description
            if (TextUtils.isEmpty(getDescription()) && !TextUtils.isEmpty(osDescription)) {
                assetMap[DESCRIPTION] = osDescription.toString()
            }
        }
    }

    /**
     * Reconciles metadata using a token ID lookup.
     */
    fun updateAsset(tokenId: BigInteger, oldAssets: Map<BigInteger, NFTAsset>?) {
        val oldAsset = oldAssets?.get(tokenId)
        updateAsset(oldAsset)
    }

    /**
     * Propagates desired metadata fields from an older asset.
     */
    fun updateAsset(oldAsset: NFTAsset?) {
        if (oldAsset == null) {
            return
        }

        for (param in oldAsset.assetMap.keys) {
            if (assetMap[param] == null && DESIRED_PARAMS.contains(param)) {
                attributeMap[param] = oldAsset.assetMap[param] ?: ""
            }
        }

        if (oldAsset.balance.compareTo(BigDecimal.ZERO) > 0) {
            setBalance(oldAsset.balance)
        }
    }

    /**
     * Returns whether this asset is flagged selected.
     */
    fun isSelected(): Boolean = isChecked

    /**
     * Updates selection state.
     */
    fun setSelected(selected: Boolean) {
        isChecked = selected
    }

    /**
     * Returns the amount chosen for transfers.
     */
    fun getSelectedBalance(): BigDecimal = selected ?: BigDecimal.ZERO

    /**
     * Stores the amount chosen for transfers.
     */
    fun setSelectedBalance(amount: BigDecimal) {
        selected = amount
    }

    /**
     * Tracks extra token IDs for ERC1155 collections.
     */
    fun addCollectionToken(nftTokenId: BigInteger) {
        if (tokenIdList == null) {
            tokenIdList = ArrayList()
        }
        tokenIdList?.add(nftTokenId)
    }

    /**
     * Indicates whether this asset aggregates a collection.
     */
    fun isCollection(): Boolean = tokenIdList?.size ?: 0 > 1

    /**
     * Returns the collection size.
     */
    fun getCollectionCount(): Int = tokenIdList?.size ?: 0

    /**
     * Returns sorted collection IDs.
     */
    fun getCollectionIds(): List<BigInteger> {
        val list = tokenIdList ?: return emptyList()
        Collections.sort(list)
        return list
    }

    /**
     * Classifies the desired display category for this asset.
     */
    fun getAssetCategory(tokenId: BigInteger): Category {
        return when {
            assetMap.containsKey(ATTESTATION_ASSET) -> Category.ATTESTATION
            tokenIdList != null && isCollection() -> Category.COLLECTION
            ERC1155Token.isNFT(tokenId) ->
                if (balance == BigDecimal.ONE) {
                    Category.NFT
                } else {
                    Category.SEMI_FT
                }
            else -> Category.FT
        }
    }

    /**
     * Associates OpenSea metadata with this asset.
     */
    fun attachOpenSeaAssetData(openSeaAsset: OpenSeaAsset?) {
        this.openSeaAsset = openSeaAsset
    }

    /**
     * Returns the attached OpenSea metadata, if any.
     */
    fun getOpenSeaAsset(): OpenSeaAsset? = openSeaAsset

    /**
     * Indicates whether this asset represents an attestation.
     */
    fun isAttestation(): Boolean = assetMap.containsKey(ATTESTATION_ASSET)

    /**
     * Returns the token ID in string form.
     */
    fun getTokenIdStr(): String = attributeMap.getOrDefault(ID, "1")

    /**
     * Returns the attestation identifier.
     */
    fun getAttestationID(): String = attributeMap.getOrDefault(ID, "1")

    /**
     * Adds a new attribute entry.
     */
    fun addAttribute(name: String, value: String) {
        attributeMap[name] = value
    }

    /**
     * Injects metadata from an embedded attestation definition.
     */
    fun setupScriptElements(td: TokenDefinition?): Boolean {
        var hasMetaData = false
        val internalAtt: AttestationDefinition? = td?.attestation
        if (internalAtt != null && internalAtt.metadata.isNotEmpty()) {
            internalAtt.metadata.keys.forEach { key ->
                assetMap[key] = internalAtt.metadata[key] ?: ""
            }
            hasMetaData = true
        }
        return hasMetaData
    }

    /**
     * Populates scripted attributes from the provided token.
     */
    fun setupScriptAttributes(td: TokenDefinition, token: Token) {
        val internalAtt: AttestationDefinition? = td.attestation
        if (internalAtt != null && !internalAtt.attributes.isNullOrEmpty()) {
            for (attr in internalAtt.attributes.entries) {
                val typeName = attr.key
                val attrTitle = attr.value
                val attrValue = token.getAttrValue(typeName)
                if (!TextUtils.isEmpty(attrValue)) {
                    attributeMap[attrTitle] = attrValue
                }
            }
        }
    }

    /**
     * Supported NFT display categories.
     */
    enum class Category(val value: String) {
        NFT("NFT"),
        FT("Fungible Token"),
        COLLECTION("Collection"),
        SEMI_FT("Semi-Fungible"),
        ATTESTATION("Attestation");

        /**
         * Returns the display string for this category.
         */
        fun getValue(): String = value
    }
}
