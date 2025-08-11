package com.alphawallet.app.interact

import android.text.TextUtils
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.repository.WalletItem
import com.alphawallet.app.repository.WalletRepositoryType
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Kotlin 版本的 FetchWalletsInteract
 * 使用协程替代 RxJava，提供更好的异步操作支持
 */
class FetchWalletsInteract(
    private val accountRepository: WalletRepositoryType,
) {
    companion object {
        private const val TAG = "FetchWalletsInteract"
    }

    // ==================== 钱包获取操作 ====================

    /**
     * 获取所有钱包
     * 转换前: Single<Wallet[]>
     * 转换后: suspend fun(): Array<Wallet>
     */
    suspend fun fetch(): Array<Wallet> =
        withContext(Dispatchers.IO) {
            try {
                accountRepository.fetchWallets()
            } catch (e: Exception) {
                Timber.e(e, "Error fetching wallets")
                throw e
            }
        }

    /**
     * 根据地址获取钱包
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     *
     * @param keyAddress 钱包地址
     * @return 钱包对象
     */
    suspend fun getWallet(keyAddress: String): Wallet =
        withContext(Dispatchers.IO) {
            try {
                accountRepository.findWallet(keyAddress)
            } catch (e: Exception) {
                Timber.e(e, "Error getting wallet for address: $keyAddress")
                throw e
            }
        }

    /**
     * 存储钱包
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     *
     * @param wallet 要存储的钱包
     * @return 存储的钱包
     */
    suspend fun storeWallet(wallet: Wallet): Wallet =
        withContext(Dispatchers.IO) {
            try {
                accountRepository.storeWallet(wallet)
            } catch (e: Exception) {
                Timber.e(e, "Error storing wallet: ${wallet.address}")
                throw e
            }
        }

    // ==================== 钱包更新操作 ====================

    /**
     * 更新钱包数据
     * 转换前: void
     * 转换后: suspend fun()
     *
     * @param wallet 要更新的钱包
     * @param onSuccess 成功回调
     */
    suspend fun updateWalletData(
        wallet: Wallet,
        onSuccess: Realm.Transaction.OnSuccess,
    ) {
        withContext(Dispatchers.IO) {
            try {
                accountRepository.updateWalletData(wallet, onSuccess)
            } catch (e: Exception) {
                Timber.e(e, "Error updating wallet data for: ${wallet.address}")
                onSuccess.onSuccess() // 即使出错也调用成功回调
            }
        }
    }

    /**
     * 更新钱包项目
     * 转换前: void
     * 转换后: suspend fun()
     *
     * @param wallet 要更新的钱包
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
                accountRepository.updateWalletItem(wallet, item, onSuccess)
            } catch (e: Exception) {
                Timber.e(e, "Error updating wallet item for: ${wallet.address}")
                onSuccess.onSuccess() // 即使出错也调用成功回调
            }
        }
    }

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
                accountRepository.updateBackupTime(walletAddr)
            } catch (e: Exception) {
                Timber.e(e, "Error updating backup time for: $walletAddr")
                throw e
            }
        }
    }

    /**
     * 更新 ENS
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     *
     * @param wallet 要更新 ENS 的钱包
     * @return 更新后的钱包
     */
    suspend fun updateENS(wallet: Wallet): Wallet =
        withContext(Dispatchers.IO) {
            try {
                if (TextUtils.isEmpty(wallet.ENSname)) {
                    wallet
                } else {
                    storeWallet(wallet)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating ENS for wallet: ${wallet.address}")
                throw e
            }
        }

    /**
     * 更新 ENS (挂起函数版本)
     * 新增: 为 HomeViewModel 提供的方法
     *
     * @param wallet 要更新 ENS 的钱包
     * @return 更新后的钱包
     */
    suspend fun updateENSSuspend(wallet: Wallet): Wallet =
        withContext(Dispatchers.IO) {
            try {
                if (TextUtils.isEmpty(wallet.ENSname)) {
                    wallet
                } else {
                    storeWallet(wallet)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating ENS for wallet: ${wallet.address}")
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
     * 批量获取钱包
     * 新增: 支持批量操作
     *
     * @param addresses 钱包地址列表
     * @return 钱包列表
     */
    suspend fun fetchWalletsByAddresses(addresses: List<String>): Result<List<Wallet>> =
        safeWalletOperation {
            val wallets = mutableListOf<Wallet>()
            for (address in addresses) {
                try {
                    val wallet = getWallet(address)
                    wallets.add(wallet)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to fetch wallet for address: $address")
                }
            }
            wallets
        }

    /**
     * 批量存储钱包
     * 新增: 支持批量存储
     *
     * @param wallets 要存储的钱包列表
     * @return 存储结果
     */
    suspend fun storeMultipleWallets(wallets: List<Wallet>): Result<Int> =
        safeWalletOperation {
            var successCount = 0
            for (wallet in wallets) {
                try {
                    storeWallet(wallet)
                    successCount++
                } catch (e: Exception) {
                    Timber.e(e, "Failed to store wallet: ${wallet.address}")
                }
            }
            successCount
        }

    /**
     * 批量更新备份时间
     * 新增: 支持批量更新
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
     * 获取钱包统计信息
     * 新增: 获取钱包统计
     *
     * @return 钱包统计信息
     */
    suspend fun getWalletStatistics(): WalletStatistics =
        safeWalletOperation {
            val allWallets = fetch()
            val totalWallets = allWallets.size
            val walletsWithENS = allWallets.count { !TextUtils.isEmpty(it.ENSname) }
            val walletsWithBalance = allWallets.count { !TextUtils.isEmpty(it.balance) && it.balance != "0" }

            WalletStatistics(
                totalWallets = totalWallets,
                walletsWithENS = walletsWithENS,
                walletsWithBalance = walletsWithBalance,
                walletsWithoutENS = totalWallets - walletsWithENS,
                walletsWithoutBalance = totalWallets - walletsWithBalance,
            )
        }.getOrElse {
            WalletStatistics(
                totalWallets = 0,
                walletsWithENS = 0,
                walletsWithBalance = 0,
                walletsWithoutENS = 0,
                walletsWithoutBalance = 0,
            )
        }

    /**
     * 搜索钱包
     * 新增: 钱包搜索功能
     *
     * @param query 搜索查询
     * @return 匹配的钱包列表
     */
    suspend fun searchWallets(query: String): Result<List<Wallet>> =
        safeWalletOperation {
            val allWallets = fetch()
            allWallets
                .filter { wallet ->
                    wallet.address?.contains(query, ignoreCase = true) == true ||
                        wallet.name?.contains(query, ignoreCase = true) == true ||
                        wallet.ENSname?.contains(query, ignoreCase = true) == true
                }.toList()
        }

    /**
     * 获取钱包信息摘要
     * 新增: 获取钱包的摘要信息
     *
     * @param address 钱包地址
     * @return 钱包信息摘要
     */
    suspend fun getWalletSummary(address: String): WalletStatisticsSummary? =
        safeWalletOperation {
            val wallet = getWallet(address)
            val statistics = getWalletStatistics()

            WalletStatisticsSummary(
                address = address,
                name = wallet.name,
                balance = wallet.balance,
                ensName = wallet.ENSname,
                hasENS = !TextUtils.isEmpty(wallet.ENSname),
                hasBalance = !TextUtils.isEmpty(wallet.balance) && wallet.balance != "0",
                totalWallets = statistics.totalWallets,
            )
        }.getOrNull()
}

/**
 * 钱包统计信息数据类
 * 新增: 用于返回钱包统计信息
 */
data class WalletStatistics(
    val totalWallets: Int,
    val walletsWithENS: Int,
    val walletsWithBalance: Int,
    val walletsWithoutENS: Int,
    val walletsWithoutBalance: Int,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * 钱包统计摘要数据类
 * 新增: 用于返回钱包的摘要信息
 */
data class WalletStatisticsSummary(
    val address: String,
    val name: String?,
    val balance: String?,
    val ensName: String?,
    val hasENS: Boolean,
    val hasBalance: Boolean,
    val totalWallets: Int,
    val creationTime: Long = System.currentTimeMillis(),
)
