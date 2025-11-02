package com.alphawallet.app.repository.entity

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmField

open class RealmWalletData : RealmObject() {

    @PrimaryKey
    var address: String? = null
    var ENSName: String? = null
    var balance: String? = null
    var name: String? = null
    var ENSAvatar: String? = null

    @RealmField(name = "lastWarning")
    private var lastWarningStorage: Long = 0L

    fun getLastWarning(): Long = lastWarningStorage

    fun setLastWarning(lastWarning: Long) {
        lastWarningStorage = lastWarning and DISMISS_WARNING_IN_SETTINGS_MASK
    }

    fun getIsDismissedInSettings(): Boolean = (lastWarningStorage and 0x1L) == 1L

    fun setIsDismissedInSettings(isDismissed: Boolean) {
        lastWarningStorage =
            (lastWarningStorage and DISMISS_WARNING_IN_SETTINGS_MASK) + if (isDismissed) 1L else 0L
    }

    companion object {
        private const val DISMISS_WARNING_IN_SETTINGS_MASK = 0xFFFFFFFEL
    }
}
