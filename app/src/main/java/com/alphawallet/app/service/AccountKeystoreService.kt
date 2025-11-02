package com.alphawallet.app.service

import com.alphawallet.app.entity.Wallet
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.token.entity.Signable
import org.web3j.crypto.RawTransaction
import java.math.BigInteger

/**
 * Kotlin 版本的 AccountKeystoreService 接口
 * 将 RxJava 替换为协程，提供更好的异步操作支持
 */
interface AccountKeystoreService {
    // ==================== 账户操作 ====================

    /**
     * 在 keystore 中创建账户
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     *
     * @param password 账户密码
     * @return 新的钱包
     */
    suspend fun createAccount(password: String): Wallet

    /**
     * 导入现有的 keystore
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     *
     * @param store 要导入的 keystore
     * @param password keystore 密码
     * @param newPassword 新密码
     * @return 导入的钱包（如果成功）
     */
    suspend fun importKeystore(
        store: String,
        password: String,
        newPassword: String,
    ): Wallet

    /**
     * 导入私钥
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     *
     * @param privateKey 私钥
     * @param newPassword 新密码
     * @return 导入的钱包
     */
    suspend fun importPrivateKey(
        privateKey: String,
        newPassword: String,
    ): Wallet

    /**
     * 导出钱包到 keystore
     * 转换前: Single<String>
     * 转换后: suspend fun(): String
     *
     * @param wallet 要导出的钱包
     * @param password 钱包密码
     * @param newPassword 存储的新密码
     * @return keystore 数据
     */
    suspend fun exportAccount(
        wallet: Wallet,
        password: String,
        newPassword: String,
    ): String

    /**
     * 从 keystore 删除账户
     * 转换前: Completable
     * 转换后: suspend fun()
     *
     * @param address 账户地址
     * @param password 账户密码
     */
    suspend fun deleteAccount(
        address: String,
        password: String,
    )

    // ==================== 签名操作 ====================

    /**
     * 签名交易
     * 转换前: Single<SignatureFromKey>
     * 转换后: suspend fun(): SignatureFromKey
     *
     * @param signer 签名者钱包
     * @param toAddress 交易目标地址
     * @param amount 金额
     * @param gasPrice 燃气价格
     * @param gasLimit 燃气限制
     * @param nonce 随机数
     * @param data 数据
     * @param chainId 链 ID
     * @return 签名数据
     */
    suspend fun signTransaction(
        signer: Wallet,
        toAddress: String,
        amount: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        nonce: Long,
        data: ByteArray,
        chainId: Long,
    ): SignatureFromKey

    /**
     * 签名交易（使用 RawTransaction）
     * 转换前: Single<SignatureFromKey>
     * 转换后: suspend fun(): SignatureFromKey
     *
     * @param signer 签名者钱包
     * @param chainId 链 ID
     * @param rtx 原始交易
     * @return 签名数据
     */
    suspend fun signTransaction(
        signer: Wallet,
        chainId: Long,
        rtx: RawTransaction,
    ): SignatureFromKey

    /**
     * 签名消息
     * 转换前: Single<SignatureFromKey>
     * 转换后: suspend fun(): SignatureFromKey
     *
     * @param signer 签名者钱包
     * @param message 可签名消息
     * @return 签名数据
     */
    suspend fun signMessage(
        signer: Wallet,
        message: Signable,
    ): SignatureFromKey

    /**
     * 快速签名消息
     * 转换前: Single<ByteArray>
     * 转换后: suspend fun(): ByteArray
     *
     * @param signer 签名者钱包
     * @param password 密码
     * @param message 消息
     * @return 签名数据
     */
    suspend fun signMessageFast(
        signer: Wallet,
        password: String,
        message: ByteArray,
    ): ByteArray

    // ==================== 查询操作 ====================

    /**
     * 检查 keystore 中是否存在指定地址
     * 转换前: boolean
     * 转换后: suspend fun(): Boolean
     *
     * @param address 钱包地址
     * @return 是否存在
     */
    suspend fun hasAccount(address: String): Boolean

    /**
     * 从 keystore 返回所有钱包
     * 转换前: Single<Wallet[]>
     * 转换后: suspend fun(): Array<Wallet>
     *
     * @return 钱包数组
     */
    suspend fun fetchAccounts(): Array<Wallet>

    // ==================== 扩展方法 ====================

    /**
     * 安全的账户操作包装器
     * 新增: 提供统一的错误处理
     *
     * @param operation 要执行的操作
     * @return 操作结果
     */
    suspend fun <T> safeAccountOperation(operation: suspend () -> T): Result<T> =
        try {
            Result.success(operation())
        } catch (e: Exception) {
            Result.failure(e)
        }

    /**
     * 批量创建账户
     * 新增: 支持批量操作
     *
     * @param passwords 密码列表
     * @return 创建的钱包列表
     */
    suspend fun createMultipleAccounts(passwords: List<String>): Result<Array<Wallet>> =
        safeAccountOperation {
            val wallets =
                passwords.map { password ->
                    createAccount(password)
                }
            wallets.toTypedArray()
        }

    /**
     * 验证钱包地址
     * 新增: 地址验证功能
     *
     * @param address 要验证的地址
     * @return 是否有效
     */
    suspend fun validateAddress(address: String): Boolean =
        try {
            // 实现地址验证逻辑
            address.matches(Regex("^0x[a-fA-F0-9]{40}$"))
        } catch (e: Exception) {
            false
        }

    /**
     * 获取账户信息
     * 新增: 获取账户详细信息
     *
     * @param address 账户地址
     * @return 账户信息
     */
    suspend fun getAccountInfo(address: String): AccountInfo? =
        try {
            if (hasAccount(address)) {
                AccountInfo(
                    address = address,
                    exists = true,
                    isValid = validateAddress(address),
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
}

/**
 * 账户信息数据类
 * 新增: 用于返回账户详细信息
 */
data class AccountInfo(
    val address: String,
    val exists: Boolean,
    val isValid: Boolean,
    val creationTime: Long = System.currentTimeMillis(),
)
