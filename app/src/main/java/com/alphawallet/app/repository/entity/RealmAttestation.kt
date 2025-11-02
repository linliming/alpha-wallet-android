package com.alphawallet.app.repository.entity

import android.util.Base64
import com.alphawallet.app.util.Utils
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmAttestation : RealmObject() {

    @PrimaryKey
    var address: String? = null
    var name: String? = null
    var chains: String? = null
    var subTitle: String? = null
    var id: String? = null
    var collectionId: String? = null
    var attestation: String? = null
    private var identifierHash: String? = null

    fun getTokenAddress(): String {
        val value = address.orEmpty()
        return if (value.contains("-")) {
            value.split("-")[0]
        } else {
            value
        }
    }

    fun getAttestationID(): String {
        val value = address.orEmpty()
        val firstDash = value.indexOf("-")
        val secondDash = if (firstDash != -1) value.indexOf("-", firstDash + 1) else -1
        return if (secondDash != -1) {
            value.substring(secondDash + 1)
        } else {
            ""
        }
    }

    fun getAttestationKey(): String = address.orEmpty()

    fun getChains(): List<Long> = Utils.longListToArray(chains.orEmpty())

    fun addChain(chainId: Long) {
        val knownChains = HashSet(Utils.longListToArray(chains.orEmpty()))
        knownChains.add(chainId)
        chains = Utils.longArrayToString(knownChains.toTypedArray())
    }

    fun setChain(chainId: Long) {
        chains = chainId.toString()
    }

    fun setAttestation(attestation: ByteArray?) {
        this.attestation = attestation?.let { Base64.encodeToString(it, Base64.DEFAULT) }
    }

    fun setAttestationLink(attestation: String) {
        this.attestation = attestation
    }

    fun getAttestationLink(): String = attestation.orEmpty()

    fun getAttestation(): ByteArray = attestation?.let { Base64.decode(it, Base64.DEFAULT) } ?: ByteArray(0)

    fun supportsChain(networkFilters: List<Long>): Boolean {
        val knownChains = HashSet(Utils.longListToArray(chains.orEmpty()))
        for (chainId in knownChains) {
            if (networkFilters.contains(chainId)) {
                return true
            }
        }
        return false
    }

    fun getIdentifierHash(): String? = identifierHash

    fun setIdentifierHash(identifierHash: String?) {
        this.identifierHash = identifierHash
    }

    fun getCollectionId(): String? = collectionId

    fun setCollectionId(collectionId: String?) {
        this.collectionId = collectionId
    }

    fun getName(): String? = name

    fun setName(name: String?) {
        this.name = name
    }

    fun getSubTitle(): String? = subTitle

    fun setSubTitle(subTitle: String?) {
        this.subTitle = subTitle
    }

    fun getId(): String? = id

    fun setId(id: String?) {
        this.id = id
    }
}
