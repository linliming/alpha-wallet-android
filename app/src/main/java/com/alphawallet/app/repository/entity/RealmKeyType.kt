package com.alphawallet.app.repository.entity

import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.service.KeyService.AuthenticationLevel
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

class RealmKeyType : RealmObject() {
    @JvmField
    @PrimaryKey
    var address: String? = null
    private var type: Byte = 0
    private var authLevel: Byte = 0

    @JvmField
    var lastBackup: Long = 0

    @JvmField
    var dateAdded: Long = 0
    var keyModulus: String? = null

    fun getType(): WalletType = WalletType.entries[type.toInt()]

    fun setType(type: WalletType) {
        this.type = type.ordinal.toByte()
    }

    fun getAuthLevel(): AuthenticationLevel = AuthenticationLevel.entries.toTypedArray().get(authLevel.toInt())

    fun setAuthLevel(authLevel: AuthenticationLevel) {
        this.authLevel = authLevel.ordinal.toByte()
    }
}
