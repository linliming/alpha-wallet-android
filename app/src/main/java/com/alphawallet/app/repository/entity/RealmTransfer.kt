package com.alphawallet.app.repository.entity

import io.realm.RealmObject
import io.realm.annotations.RealmField

open class RealmTransfer : RealmObject() {

    @RealmField(name = "hash")
    private var hashValue: String? = null

    private var tokenAddress: String? = null
    private var eventName: String? = null
    private var transferDetail: String? = null

    fun getHash(): String {
        val key = hashValue ?: return ""
        val firstDash = key.indexOf('-')
        return if (firstDash >= 0) {
            key.substring(0, firstDash)
        } else {
            key
        }
    }

    fun setHashKey(chainId: Long, hash: String) {
        hashValue = databaseKey(chainId, hash)
    }

    fun getChain(): Long {
        val key = hashValue ?: return 0L
        val firstDash = key.indexOf('-')
        if (firstDash == -1 || firstDash == key.lastIndex) {
            return 0L
        }
        val remainder = key.substring(firstDash + 1)
        val secondDash = remainder.indexOf('-')
        val chainSegment = if (secondDash >= 0) {
            remainder.substring(0, secondDash)
        } else {
            remainder
        }
        return chainSegment.toLongOrNull() ?: 0L
    }

    fun getTokenAddress(): String? = tokenAddress

    fun setTokenAddress(address: String?) {
        tokenAddress = address
    }

    fun setTransferDetail(detail: String?) {
        transferDetail = detail
    }

    fun getTransferDetail(): String? = transferDetail

    fun getEventName(): String? = eventName

    fun setEventName(name: String?) {
        eventName = name
    }

    companion object {
        @JvmStatic
        fun databaseKey(chainId: Long, hash: String): String = "$hash-$chainId"
    }
}
