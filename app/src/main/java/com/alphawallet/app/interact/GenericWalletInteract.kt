package com.alphawallet.app.interact

import com.alphawallet.app.C.ETHER_DECIMALS
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Token.Companion.TOKEN_BALANCE_PRECISION
import com.alphawallet.app.repository.WalletItem
import com.alphawallet.app.repository.WalletRepositoryType
import com.alphawallet.app.util.BalanceUtils
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal

/**
 * Kotlin 版本的 GenericWalletInteract
 * 使用协程替代 RxJava，提供更好的异步操作支持
 */
class GenericWalletInteract(
    private val walletRepository: WalletRepositoryType,
) {
    companion object {
        private const val TAG = "GenericWalletInteract"
    }

    // ==================== 钱包查找操作 ====================

    /**
     * 查找默认钱包
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     */
    suspend fun find(): Wallet =
        withContext(Dispatchers.IO) {
            try {
                walletRepository.findWallet("") // 假设空字符串表示默认钱包
            } catch (e: Exception) {
                Timber.e(e, "Error finding default wallet")
                throw e
            }
        }

    /**
     * 查找指定账户的钱包
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     *
     * @param account 账户地址
     * @return 钱包对象
     */
    suspend fun findWallet(account: String): Wallet =
        withContext(Dispatchers.IO) {
            try {
                walletRepository.findWallet(account)
            } catch (e: Exception) {
                Timber.e(e, "Error finding wallet for account: $account")
                throw e
            }
        }

    // ==================== 钱包备份操作 ====================

    /**
     * 更新钱包备份时间
     * 转换前: void
     * 转换后: suspend fun()
     *
     * @param walletAddr 钱包地址
     */
    suspend fun updateBackupTime(walletAddr: String) {
        withContext(Dispatchers.IO) {
            try {
                walletRepository.updateBackupTime(walletAddr)
            } catch (e: Exception) {
                Timber.e(e, "Error updating backup time for: $walletAddr")
                throw e
            }
        }
    }

    /**
     * 更新钱包警告时间
     * 转换前: void
     * 转换后: suspend fun()
     *
     * @param walletAddr 钱包地址
     */
    suspend fun updateWarningTime(walletAddr: String) {
        withContext(Dispatchers.IO) {
            try {
                walletRepository.updateWarningTime(walletAddr)
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
     *
     * @param walletAddr 钱包地址
     * @return 需要备份的钱包地址，如果不需要备份则返回空字符串
     */
    suspend fun getWalletNeedsBackup(walletAddr: String): String =
        withContext(Dispatchers.IO) {
            try {
                walletRepository.getWalletRequiresBackup(walletAddr)
            } catch (e: Exception) {
                Timber.e(e, "Error getting wallet needs backup for: $walletAddr")
                ""
            }
        }

    /**
     * 设置是否已忽略
     * 转换前: void
     * 转换后: suspend fun()
     *
     * @param walletAddr 钱包地址
     * @param isDismissed 是否已忽略
     */
    suspend fun setIsDismissed(
        walletAddr: String,
        isDismissed: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            try {
                walletRepository.setIsDismissed(walletAddr, isDismissed)
            } catch (e: Exception) {
                Timber.e(e, "Error setting is dismissed for: $walletAddr")
                throw e
            }
        }
    }

    /**
     * 获取备份警告状态
     * 转换前: Single<Boolean>
     * 转换后: suspend fun(): Boolean
     *
     * @param walletAddr 钱包地址
     * @return 是否需要备份警告
     */
    suspend fun getBackupWarning(walletAddr: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                walletRepository.getWalletBackupWarning(walletAddr)
            } catch (e: Exception) {
                Timber.e(e, "Error getting backup warning for: $walletAddr")
                false
            }
        }

    // ==================== 钱包更新操作 ====================

    /**
     * 更新钱包项目
     * 转换前: void
     * 转换后: suspend fun()
     *
     * @param wallet 钱包对象
     * @param item 要更新的项目
     * @param onSuccess 成功回调
     */
    suspend fun updateWalletItem(
        wallet: Wallet,
        item: WalletItem,
        onSuccess: Realm.Transaction.OnSuccess,
    ) {
        withContext(Dispatchers.IO) {
            try {
                walletRepository.updateWalletItem(wallet, item, onSuccess)
            } catch (e: Exception) {
                Timber.e(e, "Error updating wallet item")
                onSuccess.onSuccess() // 即使出错也调用成功回调
            }
        }
    }

    /**
     * 如果需要则更新余额
     * 转换前: void
     * 转换后: suspend fun()
     *
     * @param wallet 钱包对象
     * @param newBalance 新余额
     */
    suspend fun updateBalanceIfRequired(
        wallet: Wallet,
        newBalance: BigDecimal,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val newBalanceStr = BalanceUtils.getScaledValueFixed(newBalance, ETHER_DECIMALS.toLong(), TOKEN_BALANCE_PRECISION)

                if (!newBalance.equals(BigDecimal.valueOf(-1)) && wallet.balance != newBalanceStr) {
                    wallet.balance = newBalanceStr
                    walletRepository.updateWalletItem(wallet, WalletItem.BALANCE) {
                        Timber.tag(TAG).d("Updated balance")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating balance")
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
                walletRepository.getWalletRealm()
            } catch (e: Exception) {
                Timber.e(e, "Error getting wallet realm")
                throw e
            }
        }

    // ==================== 扩展方法 ====================

    /**
     * 安全的钱包操作包装器
     * 新增: 提供统一的错误处理
     *
     * @param operation 要执行的操作
     * @return 操作结果
     */
    suspend fun <T> safeWalletOperation(operation: suspend () -> T): Result<T> =
        try {
            Result.success(operation())
        } catch (e: Exception) {
            Timber.e(e, "Wallet operation failed")
            Result.failure(e)
        }

    /**
     * 批量更新钱包备份时间
     * 新增: 支持批量操作
     *
     * @param walletAddresses 钱包地址列表
     * @return 更新结果
     */
    suspend fun updateMultipleBackupTimes(walletAddresses: List<String>): Result<Int> =
        safeWalletOperation {
            var successCount = 0
            for (address in walletAddresses) {
                try {
                    updateBackupTime(address)
                    successCount++
                } catch (e: Exception) {
                    Timber.e(e, "Failed to update backup time for: $address")
                }
            }
            successCount
        }

    /**
     * 检查钱包备份状态
     * 新增: 获取详细的备份状态信息
     *
     * @param walletAddr 钱包地址
     * @return 备份状态信息
     */
    suspend fun getWalletBackupStatus(walletAddr: String): WalletBackupStatus =
        safeWalletOperation {
            val needsBackup = getWalletNeedsBackup(walletAddr)
            val backupWarning = getBackupWarning(walletAddr)

            WalletBackupStatus(
                address = walletAddr,
                needsBackup = needsBackup.isNotEmpty(),
                hasBackupWarning = backupWarning,
                backupLevel =
                    when {
                        needsBackup.isEmpty() -> BackupLevel.BACKUP_NOT_REQUIRED
                        backupWarning -> BackupLevel.WALLET_HAS_HIGH_VALUE
                        else -> BackupLevel.WALLET_HAS_LOW_VALUE
                    },
            )
        }.getOrElse {
            WalletBackupStatus(
                address = walletAddr,
                needsBackup = false,
                hasBackupWarning = false,
                backupLevel = BackupLevel.BACKUP_NOT_REQUIRED,
            )
        }

    /**
     * 验证钱包地址
     * 新增: 地址验证功能
     *
     * @param address 要验证的地址
     * @return 是否有效
     */
    suspend fun validateWalletAddress(address: String): Boolean =
        try {
            address.matches(Regex("^0x[a-fA-F0-9]{40}$"))
        } catch (e: Exception) {
            false
        }

    /**
     * 获取钱包信息摘要
     * 新增: 获取钱包的摘要信息
     *
     * @param walletAddr 钱包地址
     * @return 钱包信息摘要
     */
    suspend fun getWalletSummary(walletAddr: String): WalletSummary? =
        safeWalletOperation {
            val wallet = findWallet(walletAddr)
            val backupStatus = getWalletBackupStatus(walletAddr)

            WalletSummary(
                address = walletAddr,
                name = wallet.name,
                balance = wallet.balance,
                ensName = wallet.ENSname,
                backupStatus = backupStatus,
            )
        }.getOrNull()
}

/**
 * 备份级别枚举
 * 转换前: enum BackupLevel
 * 转换后: enum class BackupLevel
 */
enum class BackupLevel {
    BACKUP_NOT_REQUIRED,
    WALLET_HAS_LOW_VALUE,
    WALLET_HAS_HIGH_VALUE,
}

/**
 * 钱包备份状态数据类
 * 新增: 用于返回详细的备份状态信息
 */
data class WalletBackupStatus(
    val address: String,
    val needsBackup: Boolean,
    val hasBackupWarning: Boolean,
    val backupLevel: BackupLevel,
    val lastBackupTime: Long = 0L,
    val lastWarningTime: Long = 0L,
)

/**
 * 钱包摘要数据类
 * 包含钱包的基本信息和备份状态
 */
data class WalletSummary(
    val address: String,
    val name: String?,
    val balance: String?,
    val ensName: String?,
    val backupStatus: WalletBackupStatus,
    val creationTime: Long = System.currentTimeMillis(),
)
