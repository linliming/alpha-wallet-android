package com.alphawallet.app.interact

import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.repository.WalletRepositoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * 设置默认钱包交互类
 *
 * 负责管理默认钱包的设置操作，将原本基于 RxJava 的 Completable 异步操作
 * 转换为基于 Kotlin 协程的 suspend function。
 *
 * 主要功能：
 * - 设置指定钱包为默认钱包
 * - 提供协程友好的异步操作接口
 * - 统一的错误处理机制
 *
 * 技术特点：
 * - 使用 Hilt 进行依赖注入
 * - 基于 Kotlin 协程的异步操作
 * - 使用 Dispatchers.IO 进行后台线程操作
 * - 完整的错误处理和日志记录
 *
 * @property walletRepository 钱包仓库接口，提供钱包相关的数据操作
 *
 * 转换说明：
 * - 原 Java 版本使用 RxJava Completable 和 AndroidSchedulers.mainThread()
 * - Kotlin 版本使用 suspend fun 和 Dispatchers.IO
 * - 调用方需要在协程作用域中调用或使用 runBlocking
 *
 * 使用示例：
 * ```kotlin
 * // 在 ViewModel 中使用
 * viewModelScope.launch {
 *     try {
 *         setDefaultWalletInteract.set(wallet)
 *         // 成功处理
 *     } catch (e: Exception) {
 *         // 错误处理
 *     }
 * }
 * ```
 *
 * @since 1.0
 * @author AlphaWallet Team
 */
class SetDefaultWalletInteract @Inject constructor(
    private val walletRepository: WalletRepositoryType
) {

    /**
     * 设置默认钱包
     *
     * 将指定的钱包设置为系统默认钱包。此操作会：
     * 1. 在后台线程中执行钱包设置操作
     * 2. 更新钱包仓库中的默认钱包配置
     * 3. 确保操作的原子性和数据一致性
     *
     * @param wallet 要设置为默认的钱包对象，不能为空
     *
     * @throws IllegalArgumentException 当钱包参数无效时
     * @throws Exception 当钱包设置操作失败时
     *
     * 转换对比：
     * ```
     * // 原 Java/RxJava 版本
     * public Completable set(Wallet wallet) {
     *     return accountRepository
     *             .setDefaultWallet(wallet)
     *             .observeOn(AndroidSchedulers.mainThread());
     * }
     *
     * // 新 Kotlin/协程 版本
     * suspend fun set(wallet: Wallet) {
     *     withContext(Dispatchers.IO) {
     *         walletRepository.setDefaultWallet(wallet)
     *     }
     * }
     * ```
     *
     * 使用示例：
     * ```kotlin
     * // 在协程作用域中调用
     * launch {
     *     try {
     *         setDefaultWalletInteract.set(selectedWallet)
     *         updateUI() // 更新UI状态
     *     } catch (e: Exception) {
     *         showError(e.message)
     *     }
     * }
     * ```
     */
    suspend fun set(wallet: Wallet) {
        wallet.address?.let {
            require(it.isNotEmpty()) {
                "钱包地址不能为空"
            }
        }
        
        try {
            Timber.d("开始设置默认钱包: ${wallet.address}")
            
            withContext(Dispatchers.IO) {
                walletRepository.setDefaultWallet(wallet)
            }
            
            Timber.d("成功设置默认钱包: ${wallet.address}")
            
        } catch (e: Exception) {
            Timber.e(e, "设置默认钱包失败: ${wallet.address}")
            throw e
        }
    }
}
