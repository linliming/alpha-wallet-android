package com.alphawallet.app.repository.entity

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmTokenTicker : RealmObject() {

    @PrimaryKey
    var contract: String? = null
    var price: String? = null
    var percentChange24h: String? = null
    var createdTime: Long = 0L
    var id: String? = null
    var image: String? = null
    var updatedTime: Long = 0L
    var currencySymbol: String? = null

    fun getContract(): String {
        val key = contract.orEmpty()
        val delimiter = key.lastIndexOf('-')
        return if (delimiter > 0) key.substring(0, delimiter) else key
    }

    fun getChain(): Long {
        val key = contract.orEmpty()
        val delimiter = key.lastIndexOf('-')
        if (delimiter == -1 || delimiter == key.lastIndex) {
            return 0L
        }
        return key.substring(delimiter + 1).toLongOrNull() ?: 0L
    }
}
