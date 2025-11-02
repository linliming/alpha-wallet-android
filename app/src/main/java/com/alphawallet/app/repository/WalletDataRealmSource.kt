package com.alphawallet.app.repository

import android.text.TextUtils
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.repository.entity.RealmKeyType
import com.alphawallet.app.repository.entity.RealmWalletData
import com.alphawallet.app.service.KeyService
import com.alphawallet.app.service.RealmManager
import io.realm.Case
import io.realm.Realm
import io.realm.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.crypto.WalletUtils
import timber.log.Timber

/**
 * Kotlin 版本的 WalletDataRealmSource
 * 使用协程替代 RxJava，提供更好的异步操作支持
 */
class WalletDataRealmSource(
    private val realmManager: RealmManager,
) {
    companion object {
        private const val TAG = "WalletDataRealmSource"
    }

    // ==================== 钱包数据操作 ====================

    /**
     * 填充钱包数据
     * 转换前: Single<Wallet[]>
     * 转换后: suspend fun(): Array<Wallet>
     */
    suspend fun populateWalletData(
        keystoreWallets: Array<Wallet>,
        keyService: KeyService,
    ): Array<Wallet> =
        withContext(Dispatchers.IO) {
            try {
                val walletList = mutableMapOf<String, Wallet>()

                realmManager.walletDataRealmInstance.use { realm ->
                    walletList.putAll(loadOrCreateKeyRealmDB(realm, keystoreWallets, keyService))

                    // 添加额外的非关键钱包数据
                    for (wallet in walletList.values) {
                        val data =
                            realm
                                .where(RealmWalletData::class.java)
                                .equalTo("address", wallet.address, Case.INSENSITIVE)
                                .findFirst()

                        composeWallet(wallet, data)
                    }
                }

                migrateWalletTypeData(walletList, keyService)

                Timber.tag("RealmDebug").d("populate %s", walletList.size)
                walletList.values.toTypedArray()
            } catch (e: Exception) {
                Timber.e(e, "Error populating wallet data")
                throw e
            }
        }

    /**
     * 存储钱包数组
     * 转换前: Single<Wallet[]>
     * 转换后: suspend fun(): Array<Wallet>
     */
    suspend fun storeWallets(wallets: Array<Wallet>): Array<Wallet> =
        withContext(Dispatchers.IO) {
            try {
                realmManager.walletDataRealmInstance.use { realm ->
                    storeWallets(realm, wallets)
                }
                wallets
            } catch (e: Exception) {
                Timber.e(e, "storeWallets: %s", e.message)
                throw e
            }
        }

    /**
     * 存储单个钱包
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     */
    suspend fun storeWallet(wallet: Wallet): Wallet =
        withContext(Dispatchers.IO) {
            try {
                // 先删除钱包以刷新数据
                deleteWallet(wallet)

                storeKeyData(wallet)
                storeWalletData(wallet)

                wallet
            } catch (e: Exception) {
                Timber.e(e, "Error storing wallet")
                throw e
            }
        }

    /**
     * 更新钱包数据
     * 转换前: void
     * 转换后: suspend fun()
     */
    suspend fun updateWalletData(
        wallet: Wallet,
        onSuccess: Realm.Transaction.OnSuccess,
    ) {
        withContext(Dispatchers.IO) {
            try {
                realmManager.walletDataRealmInstance.use { realm ->
                    realm.executeTransactionAsync({ r ->
                        storeKeyData(wallet, r)
                        storeWalletData(wallet, r)
                        Timber.tag("RealmDebug").d("storedKeydata %s", wallet.address)
                    }, onSuccess)
                }
            } catch (e: Exception) {
                Timber.e(e)
                onSuccess.onSuccess()
            }
        }
    }

    /**
     * 更新钱包项目
     * 转换前: void
     * 转换后: suspend fun()
     */
    suspend fun updateWalletItem(
        wallet: Wallet,
        item: WalletItem,
        onSuccess: Realm.Transaction.OnSuccess,
    ) {
        withContext(Dispatchers.IO) {
            try {
                realmManager.walletDataRealmInstance.use { realm ->
                    realm.executeTransactionAsync({ r ->
                        val walletData =
                            r
                                .where(RealmWalletData::class.java)
                                .equalTo("address", wallet.address, Case.INSENSITIVE)
                                .findFirst()

                        if (walletData != null) {
                            when (item) {
                                WalletItem.NAME -> walletData.name = wallet.name
                                WalletItem.ENS_NAME -> walletData.ENSName = wallet.ENSname
                                WalletItem.BALANCE -> walletData.balance = (wallet.balance)
                                WalletItem.ENS_AVATAR -> walletData.ENSAvatar = (wallet.ENSAvatar)
                            }

                            r.insertOrUpdate(walletData)
                        }

                        Timber.tag("RealmDebug").d("storedKeydata %s", wallet.address)
                    }, onSuccess)
                }
            } catch (e: Exception) {
                Timber.e(e)
                onSuccess.onSuccess()
            }
        }
    }

    // ==================== 钱包信息操作 ====================

    /**
     * 获取钱包名称
     * 转换前: Single<String>
     * 转换后: suspend fun(): String
     */
    suspend fun getName(address: String): String =
        withContext(Dispatchers.IO) {
            try {
                var name = ""
                realmManager.walletDataRealmInstance.use { realm ->
                    val realmWallet =
                        realm
                            .where(RealmWalletData::class.java)
                            .equalTo("address", address, Case.INSENSITIVE)
                            .findFirst()

                    if (realmWallet != null) {
                        name = realmWallet.name.toString()
                    }
                }
                name
            } catch (e: Exception) {
                Timber.e(e, "getName: %s", e.message)
                ""
            }
        }

    /**
     * 更新备份时间
     * 转换前: void
     * 转换后: suspend fun()
     */
    suspend fun updateBackupTime(walletAddr: String) {
        withContext(Dispatchers.IO) {
            try {
                updateWarningTime(walletAddr)

                realmManager.walletDataRealmInstance.use { realm ->
                    realm.executeTransactionAsync({ r ->
                        val realmKey =
                            r
                                .where(RealmKeyType::class.java)
                                .equalTo("address", walletAddr, Case.INSENSITIVE)
                                .findFirst()

                        if (realmKey != null) {
                            realmKey.lastBackup = System.currentTimeMillis()
                        }
                    })
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating backup time for: $walletAddr")
                throw e
            }
        }
    }

    /**
     * 更新警告时间
     * 转换前: void
     * 转换后: suspend fun()
     */
    suspend fun updateWarningTime(walletAddr: String) {
        withContext(Dispatchers.IO) {
            try {
                realmManager.walletDataRealmInstance.use { realm ->
                    realm.executeTransactionAsync({ r ->
                        val realmWallet =
                            r
                                .where(RealmWalletData::class.java)
                                .equalTo("address", walletAddr)
                                .findFirst()

                        if (realmWallet != null) {
                            // 总是更新警告时间，但只在备份时才更新备份时间
                            realmWallet.setLastWarning(System.currentTimeMillis())
                        }
                    })
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating warning time for: $walletAddr")
                throw e
            }
        }
    }

    /**
     * 获取需要备份的钱包
     * 转换前: Single<String>
     * 转换后: suspend fun(): String
     */
    suspend fun getWalletRequiresBackup(walletAddr: String): String =
        withContext(Dispatchers.IO) {
            try {
                val wasDismissed = isDismissedInSettings(walletAddr)
                val backupTime = getKeyBackupTime(walletAddr)

                if (!wasDismissed && backupTime == 0L) {
                    walletAddr
                } else {
                    ""
                }
            } catch (e: Exception) {
                Timber.e(e, "Error getting wallet requires backup for: $walletAddr")
                ""
            }
        }

    /**
     * 获取钱包备份警告
     * 转换前: Single<Boolean>
     * 转换后: suspend fun(): Boolean
     */
    suspend fun getWalletBackupWarning(walletAddr: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val backupTime = getKeyBackupTime(walletAddr)
                val warningTime = getWalletWarningTime(walletAddr)

                requiresBackup(backupTime, warningTime)
            } catch (e: Exception) {
                Timber.e(e, "Error getting wallet backup warning for: $walletAddr")
                false
            }
        }

    /**
     * 删除钱包
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     */
    suspend fun deleteWallet(wallet: Wallet): Wallet =
        withContext(Dispatchers.IO) {
            try {
                realmManager.walletDataRealmInstance.use { realm ->
                    realm.executeTransaction { r ->
                        val realmWallet =
                            r
                                .where(RealmWalletData::class.java)
                                .equalTo("address", wallet.address, Case.INSENSITIVE)
                                .findAll()

                        if (realmWallet != null) {
                            realmWallet.deleteAllFromRealm()
                        }

                        val realmKey =
                            r
                                .where(RealmKeyType::class.java)
                                .equalTo("address", wallet.address, Case.INSENSITIVE)
                                .findAll()

                        if (realmKey != null) {
                            realmKey.deleteAllFromRealm()
                        }
                    }
                }

                realmManager.getRealmInstance(wallet).use { instance ->
                    instance.executeTransaction { r -> r.deleteAll() }
                    instance.refresh()
                }

                wallet
            } catch (e: Exception) {
                Timber.e(e, "Error deleting wallet")
                throw e
            }
        }

    /**
     * 设置是否已忽略
     * 转换前: void
     * 转换后: suspend fun()
     */
    suspend fun setIsDismissed(
        walletAddr: String,
        isDismissed: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            try {
                realmManager.walletDataRealmInstance.use { realm ->
                    realm.executeTransactionAsync({ r ->
                        val realmWallet =
                            r
                                .where(RealmWalletData::class.java)
                                .equalTo("address", walletAddr, Case.INSENSITIVE)
                                .findFirst()

                        if (realmWallet != null) {
                            realmWallet.setIsDismissedInSettings(isDismissed)
                        }
                    })
                }
            } catch (e: Exception) {
                Timber.e(e, "Error setting is dismissed for: $walletAddr")
                throw e
            }
        }
    }

    /**
     * 获取钱包 Realm
     * 转换前: Realm
     * 转换后: suspend fun(): Realm
     */
    suspend fun getWalletRealm(): Realm =
        withContext(Dispatchers.IO) {
            try {
                realmManager.walletDataRealmInstance
            } catch (e: Exception) {
                Timber.e(e, "Error getting wallet realm")
                throw e
            }
        }

    // ==================== 私有方法 ====================

    private fun loadOrCreateKeyRealmDB(
        realm: Realm,
        wallets: Array<Wallet>,
        keyService: KeyService,
    ): Map<String, Wallet> {
        val walletList = mutableMapOf<String, Wallet>()
        val keyStoreList = walletArrayToAddressList(wallets)

        realm.refresh() // 确保在查询前完全更新
        val realmKeyTypes =
            realm
                .where(RealmKeyType::class.java)
                .sort("dateAdded", Sort.ASCENDING)
                .findAll()

        val walletUpdates = mutableListOf<Wallet>()

        if (realmKeyTypes.size > 0) {
            // 加载固定钱包数据：钱包类型、创建和备份时间
            for (walletTypeData in realmKeyTypes) {
                val w = composeKeyType(walletTypeData)
                if (w == null || (w.type == WalletType.KEYSTORE || w.type == WalletType.KEYSTORE_LEGACY) &&
                    !keyStoreList.contains(walletTypeData.address?.lowercase())
                ) {
                    continue
                }

                if (w.type == WalletType.KEYSTORE_LEGACY && !testLegacyCipher(w, keyService)) {
                    w.type = WalletType.KEYSTORE
                    walletUpdates.add(w)
                }

                walletList[w.address!!.lowercase()] = w
            }
        } else {
            // 仅在从 v2.01.3 及更低版本升级时为零（预 HD 密钥）
            for (wallet in wallets) {
                wallet.authLevel = KeyService.AuthenticationLevel.TEE_NO_AUTHENTICATION
                if (testLegacyCipher(wallet, keyService)) {
                    wallet.type = WalletType.KEYSTORE_LEGACY
                } else {
                    wallet.type = WalletType.KEYSTORE
                }
                walletList[wallet.address!!.lowercase()] = wallet
                walletUpdates.add(wallet)
            }
        }

        if (walletUpdates.isNotEmpty()) {
            storeWallets(realm, walletUpdates.toTypedArray())
        }

        Timber.tag("RealmDebug").d("loadorcreate %s", walletList.size)
        return walletList
    }

    private fun walletArrayToAddressList(wallets: Array<Wallet>): List<String> = wallets.map { it.address!!.lowercase() }

    private fun composeWallet(
        wallet: Wallet,
        d: RealmWalletData?,
    ) {
        if (d != null) {
            wallet.ENSname = d.ENSName
            wallet.balance = balance(d)
            wallet.name = d.name
            wallet.ENSAvatar = d.ENSAvatar
        }
    }

    private fun composeKeyType(keyType: RealmKeyType?): Wallet? {
        if (keyType != null && !TextUtils.isEmpty(keyType.address) &&
            WalletUtils.isValidAddress(keyType.address)
        ) {
            val wallet = Wallet(keyType.address)
            wallet.type = keyType.getType()
            wallet.walletCreationTime = keyType.dateAdded
            wallet.lastBackupTime = keyType.lastBackup
            wallet.authLevel = keyType.getAuthLevel()
            return wallet
        }
        return null
    }

    private fun balance(data: RealmWalletData): String {
        val value = data.balance
        return if (value == null) "0" else value
    }

    private fun storeWallets(
        realm: Realm,
        wallets: Array<Wallet>,
    ) {
        realm.executeTransaction { r ->
            for (wallet in wallets) {
                storeKeyData(wallet, r)
                storeWalletData(wallet, r)
            }
        }
    }

    private fun storeKeyData(wallet: Wallet) {
        realmManager.walletDataRealmInstance.use { realm ->
            realm.executeTransaction { r ->
                storeKeyData(wallet, r)
                Timber.tag("RealmDebug").d("storedKeyData %s", wallet.address)
            }
        }
    }

    private fun storeWalletData(wallet: Wallet) {
        try {
            realmManager.walletDataRealmInstance.use { realm ->
                realm.executeTransaction { r ->
                    storeWalletData(wallet, r)
                    Timber.tag("RealmDebug").d("storedwalletdata %s", wallet.address)
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun isDismissedInSettings(wallet: String): Boolean {
        realmManager.walletDataRealmInstance.use { realm ->
            val data =
                realm
                    .where(RealmWalletData::class.java)
                    .equalTo("address", wallet, Case.INSENSITIVE)
                    .findFirst()
            return data != null && data.getIsDismissedInSettings()
        }
    }

    private fun getWalletWarningTime(walletAddr: String): Long {
        realmManager.walletDataRealmInstance.use { realm ->
            val data =
                realm
                    .where(RealmWalletData::class.java)
                    .equalTo("address", walletAddr, Case.INSENSITIVE)
                    .findFirst()

            return data?.getLastWarning() ?: 0L
        }
    }

    private fun getKeyBackupTime(walletAddr: String): Long {
        realmManager.walletDataRealmInstance.use { realm ->
            val realmKey =
                realm
                    .where(RealmKeyType::class.java)
                    .equalTo("address", walletAddr, Case.INSENSITIVE)
                    .findFirst()

            return realmKey?.lastBackup ?: 0L
        }
    }

    private fun requiresBackup(
        backupTime: Long,
        warningTime: Long,
    ): Boolean {
        val warningDismissed = System.currentTimeMillis() < (warningTime + KeyService.TIME_BETWEEN_BACKUP_WARNING_MILLIS)

        // 钱包从未备份，但备份警告可能已被滑动掉
        return !warningDismissed && backupTime == 0L
    }

    private fun storeKeyData(
        wallet: Wallet,
        r: Realm,
    ) {
        var realmKey =
            r
                .where(RealmKeyType::class.java)
                .equalTo("address", wallet.address, Case.INSENSITIVE)
                .findFirst()

        if (realmKey == null) {
            realmKey = r.createObject(RealmKeyType::class.java, wallet.address)
            realmKey.dateAdded = if (wallet.walletCreationTime != 0L) wallet.walletCreationTime else System.currentTimeMillis()
        } else if (realmKey.dateAdded == 0L) {
            realmKey.dateAdded = System.currentTimeMillis()
        }

        realmKey?.setType(wallet.type)
        realmKey?.lastBackup = wallet.lastBackupTime
        realmKey?.setAuthLevel(wallet.authLevel)
        // TODO
        realmKey?.keyModulus = ""
        r.insertOrUpdate(realmKey)
    }

    private fun storeWalletData(
        wallet: Wallet,
        r: Realm,
    ) {
        var item =
            r
                .where(RealmWalletData::class.java)
                .equalTo("address", wallet.address, Case.INSENSITIVE)
                .findFirst()

        if (item == null) {
            item = r.createObject(RealmWalletData::class.java, wallet.address)
        }

        item?.name = (wallet.name)
        item?.ENSName = (wallet.ENSname)
        item?.balance = (wallet.balance)
        item?.ENSAvatar = (wallet.ENSAvatar)
        r.insertOrUpdate(item)
    }

    private fun testLegacyCipher(
        wallet: Wallet,
        service: KeyService,
    ): Boolean {
        val address = wallet.address ?: return false
        return try {
            val result = service.testCipher(address, KeyService.LEGACY_CIPHER_ALGORITHM)
            result.first == KeyService.KeyExceptionType.SUCCESSFUL_DECODE
        } catch (e: Exception) {
            Timber.e(e, "Legacy cipher test failed for address: $address")
            false
        }
    }

    private fun migrateWalletTypeData(
        walletList: Map<String, Wallet>,
        service: KeyService,
    ) {
        val walletTypeData = mutableMapOf<String, Wallet>()

        realmManager.walletTypeRealmInstance.use { realm ->
            val rr = realm.where(RealmKeyType::class.java).findAll()

            for (rk in rr) {
                val w = composeKeyType(rk)
                if (w != null) {
                    walletTypeData[w.address!!.lowercase()] = w
                    if (w.type == WalletType.KEYSTORE_LEGACY && !testLegacyCipher(w, service)) {
                        w.type = WalletType.KEYSTORE
                    }
                }
            }
        }

        // 复制结果回来
        if (walletTypeData.isNotEmpty()) {
            realmManager.walletDataRealmInstance.use { realm ->
                realm.executeTransaction { r ->
                    // 首先从 TypeRealm 导入数据
                    for (w in walletList.values) {
                        val data =
                            r
                                .where(RealmKeyType::class.java)
                                .equalTo("address", w.address, Case.INSENSITIVE)
                                .findFirst()

                        if (data == null) continue

                        val walletFromTypeRealm = walletTypeData[w.address!!.lowercase()]
                        if (walletFromTypeRealm != null) {
                            if (walletFromTypeRealm.walletCreationTime != 0L &&
                                walletFromTypeRealm.walletCreationTime < w.walletCreationTime
                            ) {
                                data.dateAdded = walletFromTypeRealm.walletCreationTime
                            }
                            if (walletFromTypeRealm.lastBackupTime != 0L && w.lastBackupTime == 0L) {
                                data.lastBackup = walletFromTypeRealm.lastBackupTime
                            }
                            r.insertOrUpdate(data)
                        }
                    }

                    // 现在复制其他记录
                    for (w in walletTypeData.values) {
                        if (walletList[w.address!!.lowercase()] == null) {
                            // 重新引入这个钱包
                            storeKeyData(w, r)
                        }
                    }
                }
            }

            deleteWalletTypeData()
        }
    }

    private fun deleteWalletTypeData() {
        // 现在进程已完成，删除记录
        realmManager.walletTypeRealmInstance.use { realm ->
            val rr = realm.where(RealmKeyType::class.java).findAll()
            realm.executeTransaction { rr.deleteAllFromRealm() } // 现在我们已经提取了数据，删除数据库
        }
    }
}
