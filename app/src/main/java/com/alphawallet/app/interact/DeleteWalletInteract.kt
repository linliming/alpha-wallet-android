package com.alphawallet.app.interact

import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.repository.WalletRepositoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * 钱包删除交互类 - Kotlin协程版本
 *
 * 负责处理钱包删除的业务逻辑，包括：
 * - 从Realm数据库中删除钱包
 * - 从存储中删除钱包
 * - 获取更新后的钱包列表
 *
 * 技术特点：
 * - 使用Kotlin协程替代RxJava，提供更好的异步操作支持
 * - 支持依赖注入
 * - 统一的错误处理机制
 * - 线程安全的操作
 *
 * @param walletRepository 钱包仓库接口，用于执行钱包相关的数据操作
 *
 * @author AlphaWallet Team
 * @since 2024
 */
class DeleteWalletInteract @Inject constructor(
    private val walletRepository: WalletRepositoryType
) {
    companion object {
        private const val TAG = "DeleteWalletInteract"
    }

    /**
     * 删除钱包
     *
     * 该方法执行以下操作：
     * 1. 从Realm数据库中删除钱包
     * 2. 从存储中删除钱包
     * 3. 获取更新后的钱包列表
     *
     * @param wallet 要删除的钱包对象
     * @return 删除操作完成后的钱包数组
     * @throws Exception 当删除操作失败时抛出异常
     */
    suspend fun delete(wallet: Wallet): Array<Wallet> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: 开始删除钱包，地址: ${wallet.address}")
                
                // 1. 从Realm数据库中删除钱包
                val deletedWallet = walletRepository.deleteWalletFromRealm(wallet)
                
                // 2. 从存储中删除钱包
                deletedWallet.address?.let { walletRepository.deleteWallet(it, "") }
                
                // 3. 获取更新后的钱包列表
                val wallets = walletRepository.fetchWallets()
                Timber.d("$TAG: 钱包删除成功，剩余钱包数量: ${wallets.size}")
                
                wallets
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 钱包删除失败")
                throw e
            }
        }
    }
}
