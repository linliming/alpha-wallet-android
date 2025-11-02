package com.alphawallet.app.service

import com.alphawallet.app.BuildConfig
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.repository.AWRealmMigration
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.exceptions.RealmMigrationNeededException
import java.util.Locale

class RealmManager {
    private val realmConfigurations: MutableMap<String, RealmConfiguration?> = HashMap()

    private fun getRealmInstanceName(wallet: Wallet): String {
        return wallet.address!!.lowercase(Locale.getDefault()) + "-db.realm"
    }

    fun getRealmInstance(wallet: Wallet): Realm {
        return getRealmInstanceInternal(getRealmInstanceName(wallet))
    }

    fun getRealmInstance(walletAddress: String?): Realm {
        return getRealmInstanceInternal(walletAddress?.lowercase(Locale.getDefault()) + "-db.realm")
    }

    private fun getRealmInstanceInternal(name: String): Realm {
        try {
            var config = realmConfigurations[name]
            if (config == null) {
                config = RealmConfiguration.Builder().name(name)
                    .schemaVersion(BuildConfig.DB_VERSION.toLong())
                    .migration(AWRealmMigration())
                    .build()
                realmConfigurations[name] = config
            }
            return Realm.getInstance(config)
        } catch (e: RealmMigrationNeededException) {
            //we require a realm migration, but this wasn't provided.
            var config = realmConfigurations[name]
            if (config == null) {
                config = RealmConfiguration.Builder().name(name)
                    .schemaVersion(BuildConfig.DB_VERSION.toLong())
                    .deleteRealmIfMigrationNeeded()
                    .build()
                realmConfigurations[name] = config
            }
            return Realm.getInstance(config)
        }
    }

    val walletDataRealmInstance: Realm
        get() = getRealmInstanceInternal("WalletData-db.realm")

    val walletTypeRealmInstance: Realm
        get() = getRealmInstanceInternal("WalletType-db.realm")
}
