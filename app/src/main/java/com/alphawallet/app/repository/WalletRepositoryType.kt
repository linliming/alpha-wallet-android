package com.alphawallet.app.repository

import com.alphawallet.app.entity.Wallet
import io.realm.Realm
import kotlinx.coroutines.flow.Flow

/**
 * Kotlin 版本的 WalletRepositoryType 接口
 * 将 RxJava 替换为协程，提供更好的异步操作支持
 */
interface WalletRepositoryType {
    // ==================== 钱包操作 ====================

    /**
     * 获取所有钱包
     * 转换前: Single<Wallet[]>
     * 转换后: suspend fun(): Array<Wallet>
     */
    suspend fun fetchWallets(): Array<Wallet>

    /**
     * 根据地址查找钱包
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     */
    suspend fun findWallet(address: String): Wallet

    /**
     * 创建新钱包
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     */
    suspend fun createWallet(password: String): Wallet

    /**
     * 导入 Keystore 到钱包
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     */
    suspend fun importKeystoreToWallet(
        store: String,
        password: String,
        newPassword: String,
    ): Wallet

    /**
     * 导入私钥到钱包
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     */
    suspend fun importPrivateKeyToWallet(
        privateKey: String,
        newPassword: String,
    ): Wallet

    /**
     * 导出钱包
     * 转换前: Single<String>
     * 转换后: suspend fun(): String
     */
    suspend fun exportWallet(
        wallet: Wallet,
        password: String,
        newPassword: String,
    ): String

    /**
     * 删除钱包
     * 转换前: Completable
     * 转换后: suspend fun()
     */
    suspend fun deleteWallet(
        address: String,
        password: String,
    )

    /**
     * 从 Realm 删除钱包
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     */
    suspend fun deleteWalletFromRealm(wallet: Wallet): Wallet

    /**
     * 设置默认钱包
     * 转换前: Completable
     * 转换后: suspend fun()
     */
    suspend fun setDefaultWallet(wallet: Wallet)

    /**
     * 获取默认钱包
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     */
    suspend fun getDefaultWallet(): Wallet

    // ==================== 存储操作 ====================

    /**
     * 存储多个钱包
     * 转换前: Single<Wallet[]>
     * 转换后: suspend fun(): Array<Wallet>
     */
    suspend fun storeWallets(wallets: Array<Wallet>): Array<Wallet>

    /**
     * 存储单个钱包
     * 转换前: Single<Wallet>
     * 转换后: suspend fun(): Wallet
     */
    suspend fun storeWallet(wallet: Wallet): Wallet

    /**
     * 更新钱包数据
     * 转换前: void
     * 转换后: suspend fun()
     */
    suspend fun updateWalletData(
        wallet: Wallet,
        onSuccess: Realm.Transaction.OnSuccess,
    )

    /**
     * 更新钱包项目
     * 转换前: void
     * 转换后: suspend fun()
     */
    suspend fun updateWalletItem(
        wallet: Wallet,
        item: WalletItem,
        onSuccess: Realm.Transaction.OnSuccess,
    )

    // ==================== 钱包信息 ====================

    /**
     * 获取钱包名称
     * 转换前: Single<String>
     * 转换后: suspend fun(): String
     */
    suspend fun getName(address: String): String

    /**
     * 更新备份时间
     * 转换前: void
     * 转换后: suspend fun()
     */
    suspend fun updateBackupTime(walletAddr: String)

    /**
     * 更新警告时间
     * 转换前: void
     * 转换后: suspend fun()
     */
    suspend fun updateWarningTime(walletAddr: String)

    /**
     * 获取钱包备份警告
     * 转换前: Single<Boolean>
     * 转换后: suspend fun(): Boolean
     */
    suspend fun getWalletBackupWarning(walletAddr: String): Boolean

    /**
     * 获取需要备份的钱包
     * 转换前: Single<String>
     * 转换后: suspend fun(): String
     */
    suspend fun getWalletRequiresBackup(walletAddr: String): String

    /**
     * 设置是否已忽略
     * 转换前: void
     * 转换后: suspend fun()
     */
    suspend fun setIsDismissed(
        walletAddr: String,
        isDismissed: Boolean,
    )

    // ==================== 工具方法 ====================

    /**
     * 检查 Keystore 是否存在
     * 转换前: boolean
     * 转换后: suspend fun(): Boolean
     */
    suspend fun keystoreExists(address: String): Boolean

    /**
     * 获取钱包 Realm
     * 转换前: Realm
     * 转换后: suspend fun(): Realm
     */
    suspend fun getWalletRealm(): Realm

    // ==================== Flow 支持 (可选) ====================

    /**
     * 获取钱包列表的 Flow
     * 新增: 提供响应式数据流
     */
    fun getWalletsFlow(): Flow<Array<Wallet>>

    /**
     * 获取默认钱包的 Flow
     * 新增: 提供响应式数据流
     */
    fun getDefaultWalletFlow(): Flow<Wallet?>

    /**
     * 监听钱包变化
     * 新增: 提供实时更新
     */
    fun observeWalletChanges(address: String): Flow<Wallet?>
}
