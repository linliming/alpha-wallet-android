package com.alphawallet.app.repository.entity

import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.TokensRealmSource
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.math.BigDecimal
import java.math.BigInteger

open class RealmNFTAsset : RealmObject() {

    @PrimaryKey
    var tokenIdAddr: String? = null
    var metaData: String? = null
    var balance: String? = null

    fun getTokenId(): String {
        val key = tokenIdAddr ?: return ""
        val parts = key.split("-")
        return parts.lastOrNull().orEmpty()
    }

    fun setMetaData(metaData: String?) {
        this.metaData = metaData
    }

    fun getMetaData(): String? = metaData

    fun setBalance(balance: BigDecimal) {
        this.balance = balance.toString()
    }

    fun getBalance(): BigDecimal = balance?.let { BigDecimal(it) } ?: BigDecimal.ZERO

    companion object {
        @JvmStatic
        fun databaseKey(token: Token, tokenId: BigInteger): String =
            "${TokensRealmSource.databaseKey(token)}-${tokenId}" // maintain original format
    }
}
