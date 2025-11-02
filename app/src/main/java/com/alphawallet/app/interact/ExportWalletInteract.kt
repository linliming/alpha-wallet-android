package com.alphawallet.app.interact

import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.repository.WalletRepositoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * 钱包导出交互类 - Kotlin协程版本
 *
 * 负责处理钱包导出的业务逻辑，包括：
 * - 导出钱包到Keystore文件
 * - 使用密码保护导出的钱包
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
class ExportWalletInteract @Inject constructor(
    private val walletRepository: WalletRepositoryType
) {
    companion object {
        private const val TAG = "ExportWalletInteract"
    }

    /**
     * 导出钱包
     *
     * 该方法将钱包导出为Keystore格式，并使用新密码进行保护。
     * 导出的Keystore可用于备份或迁移钱包。
     *
     * @param wallet 要导出的钱包对象
     * @param keystorePassword 当前钱包的密码，用于解锁钱包
     * @param backupPassword 备份文件的新密码，用于保护导出的Keystore
     * @return 导出的Keystore字符串
     * @throws Exception 当导出操作失败时抛出异常
     */
    suspend fun export(wallet: Wallet, keystorePassword: String, backupPassword: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Timber.tag("RealmDebug").d("export + %s", wallet.address)
                Timber.d("$TAG: 开始导出钱包，地址: ${wallet.address}")
                
                val keystore = walletRepository.exportWallet(wallet, keystorePassword, backupPassword)
                Timber.d("$TAG: 钱包导出成功")
                
                keystore
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 钱包导出失败")
                throw e
            }
        }
    }
}