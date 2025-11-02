package com.alphawallet.app.interact

import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.repository.WalletRepositoryType
import com.alphawallet.app.service.KeyService
import com.alphawallet.app.util.ens.AWEnsResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * 钱包导入交互类 - Kotlin协程版本
 *
 * 负责处理各种钱包导入操作的业务逻辑，包括：
 * - Keystore文件导入
 * - 私钥导入
 * - 硬件钱包存储
 * - 观察钱包创建
 * - HD钱包存储
 * - Keystore钱包存储
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
class ImportWalletInteract @Inject constructor(
    private val walletRepository: WalletRepositoryType
) {

    companion object {
        private const val TAG = "ImportWalletInteract"
        
        // 常量定义
        private const val HARDWARE_WALLET_BACKUP_TIME = -1L // 硬件钱包不需要备份
    }

    /**
     * 导入Keystore文件到钱包
     *
     * 通过提供的Keystore JSON字符串、原密码和新密码来导入钱包。
     * 此方法会验证Keystore格式和密码的正确性。
     *
     * @param keystore Keystore JSON字符串
     * @param password 原密码，用于解密Keystore
     * @param newPassword 新密码，用于重新加密钱包
     * @return 导入成功的钱包对象
     * @throws Exception 当Keystore格式无效或密码错误时抛出异常
     */
    suspend fun importKeystore(
        keystore: String,
        password: String,
        newPassword: String
    ): Wallet {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: 开始导入Keystore钱包")
                val wallet = walletRepository.importKeystoreToWallet(keystore, password, newPassword)
                Timber.d("$TAG: Keystore钱包导入成功，地址: ${wallet.address}")
                wallet
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Keystore钱包导入失败")
                throw e
            }
        }
    }

    /**
     * 导入私钥到钱包
     *
     * 通过提供的私钥字符串和新密码来创建钱包。
     * 此方法会验证私钥格式的正确性。
     *
     * @param privateKey 私钥字符串（十六进制格式）
     * @param newPassword 新密码，用于加密钱包
     * @return 导入成功的钱包对象
     * @throws Exception 当私钥格式无效时抛出异常
     */
    suspend fun importPrivateKey(
        privateKey: String,
        newPassword: String
    ): Wallet {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: 开始导入私钥钱包")
                val wallet = walletRepository.importPrivateKeyToWallet(privateKey, newPassword)
                Timber.d("$TAG: 私钥钱包导入成功，地址: ${wallet.address}")
                wallet
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 私钥钱包导入失败")
                throw e
            }
        }
    }

    /**
     * 存储HD钱包
     *
     * 创建并存储一个HD（分层确定性）钱包。HD钱包支持从单个种子生成多个地址。
     *
     * @param walletAddress 钱包地址
     * @param authLevel 认证级别，决定钱包的安全等级
     * @param ensResolver ENS解析器，用于处理以太坊域名服务
     * @return 创建成功的HD钱包对象
     * @throws Exception 当钱包创建或存储失败时抛出异常
     */
    suspend fun storeHDWallet(
        walletAddress: String,
        authLevel: KeyService.AuthenticationLevel,
        ensResolver: AWEnsResolver
    ): Wallet {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: 开始存储HD钱包，地址: $walletAddress")
                
                val wallet = Wallet(walletAddress).apply {
                    type = WalletType.HDKEY
                    this.authLevel = authLevel
                    lastBackupTime = System.currentTimeMillis()
                }
                
                val storedWallet = walletRepository.storeWallet(wallet)
                Timber.d("$TAG: HD钱包存储成功，地址: ${storedWallet.address}")
                storedWallet
            } catch (e: Exception) {
                Timber.e(e, "$TAG: HD钱包存储失败")
                throw e
            }
        }
    }

    /**
     * 存储观察钱包
     *
     * 创建一个只读的观察钱包，用户可以查看余额和交易历史，但无法进行交易。
     * 观察钱包不包含私钥，因此是安全的查看方式。
     *
     * @param address 要观察的钱包地址
     * @param ensResolver ENS解析器，用于处理以太坊域名服务
     * @return 创建成功的观察钱包对象
     * @throws Exception 当钱包创建或存储失败时抛出异常
     */
    suspend fun storeWatchWallet(
        address: String,
        ensResolver: AWEnsResolver
    ): Wallet {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: 开始存储观察钱包，地址: $address")
                
                val wallet = Wallet(address).apply {
                    type = WalletType.WATCH
                    lastBackupTime = System.currentTimeMillis()
                }
                
                val storedWallet = walletRepository.storeWallet(wallet)
                Timber.d("$TAG: 观察钱包存储成功，地址: ${storedWallet.address}")
                
                // 注意：原代码中有ENS解析的注释代码，这里保留注释以供参考
                // 如果需要ENS解析功能，可以在这里添加：
                // val ensName = ensResolver.resolveEnsName(wallet.address)
                // wallet.ENSname = ensName
                
                storedWallet
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 观察钱包存储失败")
                throw e
            }
        }
    }

    /**
     * 存储硬件钱包
     *
     * 创建并存储硬件钱包的引用。硬件钱包的私钥存储在硬件设备中，
     * 提供了最高级别的安全性。
     *
     * @param address 硬件钱包地址
     * @return 创建成功的硬件钱包对象
     * @throws Exception 当钱包创建或存储失败时抛出异常
     */
    suspend fun storeHardwareWallet(address: String): Wallet {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: 开始存储硬件钱包，地址: $address")
                
                val wallet = Wallet(address).apply {
                    type = WalletType.HARDWARE
                    lastBackupTime = HARDWARE_WALLET_BACKUP_TIME // 硬件钱包不需要备份
                }
                
                val storedWallet = walletRepository.storeWallet(wallet)
                Timber.d("$TAG: 硬件钱包存储成功，地址: ${storedWallet.address}")
                storedWallet
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 硬件钱包存储失败")
                throw e
            }
        }
    }

    /**
     * 存储Keystore钱包
     *
     * 存储已经创建的Keystore钱包，设置其认证级别和类型。
     * 这通常用于已经通过其他方式创建的钱包的最终存储步骤。
     *
     * @param wallet 要存储的钱包对象
     * @param level 认证级别，决定钱包的安全等级
     * @param ensResolver ENS解析器，用于处理以太坊域名服务
     * @return 存储成功的钱包对象
     * @throws Exception 当钱包存储失败时抛出异常
     */
    suspend fun storeKeystoreWallet(
        wallet: Wallet,
        level: KeyService.AuthenticationLevel,
        ensResolver: AWEnsResolver
    ): Wallet {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: 开始存储Keystore钱包，地址: ${wallet.address}")
                
                wallet.apply {
                    authLevel = level
                    type = WalletType.KEYSTORE
                    lastBackupTime = System.currentTimeMillis()
                }
                
                val storedWallet = walletRepository.storeWallet(wallet)
                Timber.d("$TAG: Keystore钱包存储成功，地址: ${storedWallet.address}")
                
                // 注意：原代码中有ENS解析的注释代码，这里保留注释以供参考
                // 如果需要ENS解析功能，可以在这里添加：
                // val ensName = ensResolver.resolveEnsName(wallet.address)
                // wallet.ENSname = ensName
                
                storedWallet
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Keystore钱包存储失败")
                throw e
            }
        }
    }

    /**
     * 检查Keystore是否存在
     *
     * 验证指定地址的Keystore文件是否已经存在于系统中。
     * 这可以用于防止重复导入相同的钱包。
     *
     * @param address 要检查的钱包地址
     * @return true表示Keystore存在，false表示不存在
     */
    suspend fun keyStoreExists(address: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val exists = walletRepository.keystoreExists(address)
                Timber.d("$TAG: 检查Keystore存在性，地址: $address, 结果: $exists")
                exists
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 检查Keystore存在性失败，地址: $address")
                false // 发生异常时返回false，表示不存在
            }
        }
    }

    /**
     * 验证钱包地址格式
     *
     * 检查提供的地址是否符合以太坊地址格式要求。
     * 这是一个辅助方法，用于在导入前进行基本验证。
     *
     * @param address 要验证的地址字符串
     * @return true表示地址格式有效，false表示无效
     */
    fun isValidAddress(address: String): Boolean {
        return try {
            // 基本的以太坊地址格式检查
            address.isNotBlank() && 
            address.startsWith("0x") && 
            address.length == 42 &&
            address.substring(2).all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: 地址格式验证异常: $address")
            false
        }
    }

    /**
     * 验证私钥格式
     *
     * 检查提供的私钥是否符合格式要求。
     * 这是一个辅助方法，用于在导入前进行基本验证。
     *
     * @param privateKey 要验证的私钥字符串
     * @return true表示私钥格式有效，false表示无效
     */
    fun isValidPrivateKey(privateKey: String): Boolean {
        return try {
            // 基本的私钥格式检查
            val cleanKey = if (privateKey.startsWith("0x")) {
                privateKey.substring(2)
            } else {
                privateKey
            }
            
            cleanKey.isNotBlank() && 
            cleanKey.length == 64 &&
            cleanKey.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: 私钥格式验证异常")
            false
        }
    }
}