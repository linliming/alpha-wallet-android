package com.alphawallet.app.repository.entity

import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.token.entity.ContractAddress
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmTokenMapping : RealmObject() {

    @PrimaryKey
    var address: String? = null
    var base: String? = null
    var group: Int = TokenGroup.ASSET.ordinal

    fun getBase(): ContractAddress? = base?.takeUnless { it.isEmpty() }?.let { ContractAddress(it) }

    fun getGroup(): TokenGroup {
        val values = TokenGroup.values()
        return if (group in values.indices) values[group] else TokenGroup.ASSET
    }
}
