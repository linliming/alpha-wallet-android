package com.alphawallet.app.repository

import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.service.AccountKeystoreService
import com.alphawallet.app.service.KeyService
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal

/**
 * Kotlin 版本的 WalletRepository
 * 使用协程替代 RxJava，提供更好的异步操作支持
 */
class WalletRepository(
    private val preferenceRepositoryType: PreferenceRepositoryType,
    private val accountKeystoreService: AccountKeystoreService,
    private val networkRepository: EthereumNetworkRepositoryType,
    private val walletDataRealmSource: WalletDataRealmSource,
    private val keyService: KeyService,
) : WalletRepositoryType {
    // ==================== 钱包操作 ====================

    override suspend fun fetchWallets(): Array<Wallet> =
        withContext(Dispatchers.IO) {
            try {
                val wallets = accountKeystoreService.fetchAccounts()
                val populatedWallets = walletDataRealmSource.populateWalletData(wallets, keyService)

                // 设置默认钱包
                if (preferenceRepositoryType.currentWalletAddress == null && populatedWallets.isNotEmpty()) {
                    preferenceRepositoryType.currentWalletAddress = populatedWallets[0].address
                }

                populatedWallets
            } catch (e: Exception) {
                Timber.e(e, "Error fetching wallets")
                throw e
            }
        }

    override suspend fun findWallet(address: String): Wallet {
        return withContext(Dispatchers.IO) {
            try {
                val wallets = fetchWallets()
                if (wallets.isEmpty()) {
                    throw NoWallets("No wallets")
                }

                // 如果地址为空，返回第一个钱包
                if (address.isNullOrEmpty()) {
                    return@withContext wallets[0]
                }

                // 查找指定地址的钱包
                wallets.find { it.sameAddress(address) }
                    ?: wallets[0] // 如果没找到，返回第一个钱包
            } catch (e: Exception) {
                Timber.e(e, "Error finding wallet: $address")
                throw e
            }
        }
    }

    override suspend fun createWallet(password: String): Wallet =
        withContext(Dispatchers.IO) {
            try {
                accountKeystoreService.createAccount(password)
            } catch (e: Exception) {
                Timber.e(e, "Error creating wallet")
                throw e
            }
        }

    override suspend fun importKeystoreToWallet(
        store: String,
        password: String,
        newPassword: String,
    ): Wallet =
        withContext(Dispatchers.IO) {
            try {
                accountKeystoreService.importKeystore(store, password, newPassword)
            } catch (e: Exception) {
                Timber.e(e, "Error importing keystore")
                throw e
            }
        }

    override suspend fun importPrivateKeyToWallet(
        privateKey: String,
        newPassword: String,
    ): Wallet =
        withContext(Dispatchers.IO) {
            try {
                accountKeystoreService.importPrivateKey(privateKey, newPassword)
            } catch (e: Exception) {
                Timber.e(e, "Error importing private key")
                throw e
            }
        }

    override suspend fun exportWallet(
        wallet: Wallet,
        password: String,
        newPassword: String,
    ): String =
        withContext(Dispatchers.IO) {
            try {
                accountKeystoreService.exportAccount(wallet, password, newPassword)
            } catch (e: Exception) {
                Timber.e(e, "Error exporting wallet")
                throw e
            }
        }

    override suspend fun deleteWallet(
        address: String,
        password: String,
    ) {
        withContext(Dispatchers.IO) {
            try {
                accountKeystoreService.deleteAccount(address, password)
            } catch (e: Exception) {
                Timber.e(e, "Error deleting wallet")
                throw e
            }
        }
    }

    override suspend fun deleteWalletFromRealm(wallet: Wallet): Wallet =
        withContext(Dispatchers.IO) {
            try {
                walletDataRealmSource.deleteWallet(wallet)
            } catch (e: Exception) {
                Timber.e(e, "Error deleting wallet from realm")
                throw e
            }
        }

    override suspend fun setDefaultWallet(wallet: Wallet) {
        withContext(Dispatchers.IO) {
            try {
                preferenceRepositoryType.currentWalletAddress = wallet.address
            } catch (e: Exception) {
                Timber.e(e, "Error setting default wallet")
                throw e
            }
        }
    }

    override suspend fun getDefaultWallet(): Wallet =
        withContext(Dispatchers.IO) {
            try {
                val currentAddress = preferenceRepositoryType.currentWalletAddress ?: ""
                findWallet(currentAddress)
            } catch (e: Exception) {
                Timber.e(e, "Error getting default wallet")
                throw e
            }
        }

    // ==================== 存储操作 ====================

    override suspend fun storeWallets(wallets: Array<Wallet>): Array<Wallet> =
        withContext(Dispatchers.IO) {
            try {
                walletDataRealmSource.storeWallets(wallets)
            } catch (e: Exception) {
                Timber.e(e, "Error storing wallets")
                throw e
            }
        }

    override suspend fun storeWallet(wallet: Wallet): Wallet =
        withContext(Dispatchers.IO) {
            try {
                walletDataRealmSource.storeWallet(wallet)
            } catch (e: Exception) {
                Timber.e(e, "Error storing wallet")
                throw e
            }
        }

    override suspend fun updateWalletData(
        wallet: Wallet,
        onSuccess: Realm.Transaction.OnSuccess,
    ) {
        withContext(Dispatchers.IO) {
            try {
                walletDataRealmSource.updateWalletData(wallet, onSuccess)
            } catch (e: Exception) {
                Timber.e(e, "Error updating wallet data")
                throw e
            }
        }
    }

    override suspend fun updateWalletItem(
        wallet: Wallet,
        item: WalletItem,
        onSuccess: Realm.Transaction.OnSuccess,
    ) {
        withContext(Dispatchers.IO) {
            try {
                walletDataRealmSource.updateWalletItem(wallet, item, onSuccess)
            } catch (e: Exception) {
                Timber.e(e, "Error updating wallet item")
                throw e
            }
        }
    }

    // ==================== 钱包信息 ====================

    override suspend fun getName(address: String): String =
        withContext(Dispatchers.IO) {
            try {
                walletDataRealmSource.getName(address)
            } catch (e: Exception) {
                Timber.e(e, "Error getting name for address: $address")
                throw e
            }
        }

    override suspend fun updateBackupTime(walletAddr: String) {
        withContext(Dispatchers.IO) {
            try {
                walletDataRealmSource.updateBackupTime(walletAddr)
            } catch (e: Exception) {
                Timber.e(e, "Error updating backup time for: $walletAddr")
                throw e
            }
        }
    }

    override suspend fun updateWarningTime(walletAddr: String) {
        withContext(Dispatchers.IO) {
            try {
                walletDataRealmSource.updateWarningTime(walletAddr)
            } catch (e: Exception) {
                Timber.e(e, "Error updating warning time for: $walletAddr")
                throw e
            }
        }
    }

    override suspend fun getWalletBackupWarning(walletAddr: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                walletDataRealmSource.getWalletBackupWarning(walletAddr)
            } catch (e: Exception) {
                Timber.e(e, "Error getting wallet backup warning for: $walletAddr")
                throw e
            }
        }

    override suspend fun getWalletRequiresBackup(walletAddr: String): String =
        withContext(Dispatchers.IO) {
            try {
                walletDataRealmSource.getWalletRequiresBackup(walletAddr)
            } catch (e: Exception) {
                Timber.e(e, "Error getting wallet requires backup for: $walletAddr")
                throw e
            }
        }

    override suspend fun setIsDismissed(
        walletAddr: String,
        isDismissed: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            try {
                walletDataRealmSource.setIsDismissed(walletAddr, isDismissed)
            } catch (e: Exception) {
                Timber.e(e, "Error setting is dismissed for: $walletAddr")
                throw e
            }
        }
    }

    // ==================== 工具方法 ====================

    override suspend fun keystoreExists(address: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val wallets = fetchWallets()
                wallets.any { it.sameAddress(address) }
            } catch (e: Exception) {
                Timber.e(e, "Error checking keystore exists for: $address")
                false
            }
        }

    override suspend fun getWalletRealm(): Realm =
        withContext(Dispatchers.IO) {
            try {
                walletDataRealmSource.getWalletRealm()
            } catch (e: Exception) {
                Timber.e(e, "Error getting wallet realm")
                throw e
            }
        }

    // ==================== Flow 支持 ====================

    override fun getWalletsFlow(): Flow<Array<Wallet>> =
        flow {
            try {
                val wallets = fetchWallets()
                emit(wallets)
            } catch (e: Exception) {
                Timber.e(e, "Error in wallets flow")
                emit(emptyArray())
            }
        }

    override fun getDefaultWalletFlow(): Flow<Wallet?> =
        flow {
            try {
                val defaultWallet = getDefaultWallet()
                emit(defaultWallet)
            } catch (e: Exception) {
                Timber.e(e, "Error in default wallet flow")
                emit(null)
            }
        }

    override fun observeWalletChanges(address: String): Flow<Wallet?> =
        flow {
            try {
                val wallet = findWallet(address)
                emit(wallet)
            } catch (e: Exception) {
                Timber.e(e, "Error observing wallet changes for: $address")
                emit(null)
            }
        }

    // ==================== 扩展方法 ====================

    /**
     * 安全的钱包操作包装器
     */
    suspend fun <T> safeWalletOperation(operation: suspend () -> T): Result<T> =
        try {
            Result.success(operation())
        } catch (e: Exception) {
            Timber.e(e, "Wallet operation failed")
            Result.failure(e)
        }

    /**
     * 批量导入钱包
     */
    suspend fun importMultipleWallets(walletDataList: List<WalletImportData>): Result<Array<Wallet>> =
        safeWalletOperation {
            val importedWallets =
                walletDataList.map { walletData ->
                    when (walletData.type) {
                        WalletImportType.KEYSTORE ->
                            importKeystoreToWallet(
                                walletData.data,
                                walletData.password,
                                walletData.newPassword,
                            )
                        WalletImportType.PRIVATE_KEY ->
                            importPrivateKeyToWallet(
                                walletData.data,
                                walletData.newPassword,
                            )
                    }
                }

            storeWallets(importedWallets.toTypedArray())
        }

    /**
     * 获取钱包余额
     */
    suspend fun getWalletBalance(address: String): BigDecimal =
        withContext(Dispatchers.IO) {
            try {
                // 这里需要实现具体的余额获取逻辑
                // 可能需要调用 Web3j 或其他服务
                BigDecimal.ZERO // 临时返回
            } catch (e: Exception) {
                Timber.e(e, "Error getting wallet balance for: $address")
                BigDecimal.ZERO
            }
        }

    /**
     * 验证钱包地址
     */
    suspend fun validateWalletAddress(address: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // 实现地址验证逻辑
                address.matches(Regex("^0x[a-fA-F0-9]{40}$"))
            } catch (e: Exception) {
                Timber.e(e, "Error validating wallet address: $address")
                false
            }
        }
}

/**
 * 钱包导入数据类型
 */
data class WalletImportData(
    val type: WalletImportType,
    val data: String,
    val password: String = "",
    val newPassword: String,
)

/**
 * 钱包导入类型
 */
enum class WalletImportType {
    KEYSTORE,
    PRIVATE_KEY,
}

/**
 * 无钱包异常
 */
class NoWallets(
    message: String,
) : Exception(message)
