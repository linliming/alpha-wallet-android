package com.alphawallet.app.viewmodel

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.alphawallet.app.C
import com.alphawallet.app.entity.AnalyticsProperties
import com.alphawallet.app.entity.ErrorEnvelope
import com.alphawallet.app.entity.ImportWalletCallback
import com.alphawallet.app.entity.Operation
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.analytics.ImportWalletType
import com.alphawallet.app.interact.ImportWalletInteract
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.app.service.AnalyticsServiceType
import com.alphawallet.app.service.KeyService
import com.alphawallet.app.ui.widget.OnSetWatchWalletListener
import com.alphawallet.app.util.ens.AWEnsResolver
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import com.fasterxml.jackson.databind.ObjectMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.crypto.Keys
import org.web3j.crypto.WalletFile
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

/**
 * 钱包导入视图模型类 - Kotlin协程版本
 *
 * 负责处理钱包导入相关的所有业务逻辑和UI状态管理，包括：
 * - Keystore文件导入
 * - 私钥导入
 * - 助记词（种子）导入
 * - 观察钱包创建
 * - 钱包密码验证
 * - 导入进度管理
 * - 错误处理
 *
 * 技术特点：
 * - 使用Kotlin协程替代RxJava，提供更好的异步操作支持
 * - 使用Hilt进行依赖注入
 * - 继承自BaseViewModel提供基础功能
 * - 实现OnSetWatchWalletListener接口处理观察钱包设置
 * - 统一的错误处理和进度管理
 *
 * @param importWalletInteract 钱包导入交互类，处理具体的导入逻辑
 * @param keyService 密钥服务，处理密钥相关操作
 * @param analyticsService 分析服务，用于统计和分析
 *
 * @author AlphaWallet Team
 * @since 2024
 */
@HiltViewModel
class ImportWalletViewModel @Inject constructor(
    private val importWalletInteract: ImportWalletInteract,
    private val keyService: KeyService,
    analyticsService: AnalyticsServiceType<AnalyticsProperties>
) : BaseViewModel(), OnSetWatchWalletListener {

    companion object {
        private const val TAG = "ImportWalletViewModel"
    }

    // ENS解析器，用于处理以太坊域名服务
    private val ensResolver: AWEnsResolver = AWEnsResolver(
        TokenRepository.getWeb3jService(MAINNET_ID),
        keyService.context
    )

    // LiveData 数据状态管理
    private val _wallet = MutableLiveData<Pair<Wallet, ImportWalletType>>()
    private val _badSeed = MutableLiveData<Boolean>()
    private val _watchExists = MutableLiveData<String>()

    /**
     * 初始化块
     * 设置分析服务
     */
    init {
        setAnalyticsService(analyticsService)
        Timber.d("$TAG: ImportWalletViewModel 初始化完成")
    }

    /**
     * 获取钱包导入结果的LiveData
     * @return 包含钱包对象和导入类型的Pair的LiveData
     */
    fun wallet(): LiveData<Pair<Wallet, ImportWalletType>> = _wallet

    /**
     * 获取种子错误状态的LiveData
     * @return 表示种子是否有错误的Boolean LiveData
     */
    fun badSeed(): LiveData<Boolean> = _badSeed

    /**
     * 获取观察钱包已存在状态的LiveData
     * @return 已存在的钱包地址的String LiveData
     */
    fun watchExists(): LiveData<String> = _watchExists

    /**
     * 处理Keystore文件导入
     *
     * 导入Keystore JSON文件，验证密码并创建新的钱包密码。
     *
     * @param keystore Keystore JSON字符串
     * @param password 原密码
     * @param newPassword 新密码
     * @param level 认证级别
     */
    fun onKeystore(
        keystore: String,
        password: String,
        newPassword: String,
        level: KeyService.AuthenticationLevel
    ) {
        Timber.d("$TAG: 开始处理Keystore导入")
        progress.postValue(true)

        launchSafely(
            onError = { throwable ->
                Timber.e(throwable, "$TAG: Keystore导入失败")
                onError(throwable)
            }
        ) {
            try {
                // 导入Keystore
                val importedWallet = importWalletInteract.importKeystore(keystore, password, newPassword)
                
                // 存储为Keystore钱包
                val storedWallet = importWalletInteract.storeKeystoreWallet(importedWallet, level, ensResolver)
                
                // 通知UI导入成功
                onWallet(storedWallet, ImportWalletType.KEYSTORE)
                Timber.d("$TAG: Keystore导入成功，地址: ${storedWallet.address}")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Keystore导入过程中发生异常")
                throw e
            }
        }
    }

    /**
     * 处理私钥导入
     *
     * 通过私钥字符串创建钱包并设置新密码。
     *
     * @param privateKey 私钥字符串
     * @param newPassword 新密码
     * @param level 认证级别
     */
    fun onPrivateKey(
        privateKey: String,
        newPassword: String,
        level: KeyService.AuthenticationLevel
    ) {
        Timber.d("$TAG: 开始处理私钥导入")
        progress.postValue(true)

        launchSafely(
            onError = { throwable ->
                Timber.e(throwable, "$TAG: 私钥导入失败")
                onError(throwable)
            }
        ) {
            try {
                // 导入私钥
                val importedWallet = importWalletInteract.importPrivateKey(privateKey, newPassword)
                
                // 存储为Keystore钱包
                val storedWallet = importWalletInteract.storeKeystoreWallet(importedWallet, level, ensResolver)
                
                // 通知UI导入成功
                onWallet(storedWallet, ImportWalletType.PRIVATE_KEY)
                Timber.d("$TAG: 私钥导入成功，地址: ${storedWallet.address}")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 私钥导入过程中发生异常")
                throw e
            }
        }
    }

    /**
     * 处理种子（助记词）导入
     *
     * 通过钱包地址创建HD钱包。通常在用户输入助记词后调用。
     *
     * @param walletAddress 从助记词生成的钱包地址
     * @param level 认证级别
     */
    fun onSeed(walletAddress: String?, level: KeyService.AuthenticationLevel) {
        if (walletAddress == null) {
            Timber.e("$TAG: 钱包地址为空")
            progress.postValue(false)
            _badSeed.postValue(true)
            return
        }

        Timber.d("$TAG: 开始处理种子导入，地址: $walletAddress")
        progress.postValue(true)

        launchSafely(
            onError = { throwable ->
                Timber.e(throwable, "$TAG: 种子导入失败")
                onError(throwable)
            }
        ) {
            try {
                // 存储HD钱包
                val storedWallet = importWalletInteract.storeHDWallet(walletAddress, level, ensResolver)
                
                // 通知UI导入成功
                onWallet(storedWallet, ImportWalletType.SEED_PHRASE)
                Timber.d("$TAG: 种子导入成功，地址: ${storedWallet.address}")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 种子导入过程中发生异常")
                throw e
            }
        }
    }

    /**
     * 处理观察钱包设置
     *
     * 创建一个只读的观察钱包，用户可以查看余额但无法进行交易。
     * 实现OnSetWatchWalletListener接口的方法。
     *
     * @param address 要观察的钱包地址
     */
    override fun onWatchWallet(address: String) {
        Timber.d("$TAG: 开始处理观察钱包设置，地址: $address")
        
        // 检查钱包是否已存在
        launchSafely(
            onError = { throwable ->
                Timber.e(throwable, "$TAG: 检查钱包存在性失败")
                onError(throwable)
            }
        ) {
            val exists = keystoreExists(address)
            if (exists) {
                Timber.w("$TAG: 观察钱包已存在，地址: $address")
                _watchExists.postValue(address)
                return@launchSafely
            }

            progress.postValue(true)
            
            try {
                // 存储观察钱包
                val storedWallet = importWalletInteract.storeWatchWallet(address, ensResolver)
                
                // 通知UI导入成功
                onWallet(storedWallet, ImportWalletType.WATCH)
                Timber.d("$TAG: 观察钱包创建成功，地址: ${storedWallet.address}")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 观察钱包创建过程中发生异常")
                throw e
            }
        }
    }

    /**
     * 导入HD钱包
     *
     * 通过KeyService处理助记词导入。
     *
     * @param seedPhrase 助记词字符串
     * @param activity 当前活动上下文
     * @param callback 导入回调接口
     */
    fun importHDWallet(seedPhrase: String, activity: Activity, callback: ImportWalletCallback) {
        Timber.d("$TAG: 开始HD钱包导入流程")
        keyService.importHDKey(seedPhrase, activity, callback)
    }

    /**
     * 导入Keystore钱包
     *
     * 通过KeyService处理Keystore密码创建。
     *
     * @param address 钱包地址
     * @param activity 当前活动上下文
     * @param callback 导入回调接口
     */
    fun importKeystoreWallet(address: String, activity: Activity, callback: ImportWalletCallback) {
        Timber.d("$TAG: 开始Keystore钱包导入流程，地址: $address")
        keyService.createKeystorePassword(address, activity, callback)
    }

    /**
     * 导入私钥钱包
     *
     * 通过KeyService处理私钥密码创建。
     *
     * @param address 钱包地址
     * @param activity 当前活动上下文
     * @param callback 导入回调接口
     */
    fun importPrivateKeyWallet(address: String, activity: Activity, callback: ImportWalletCallback) {
        Timber.d("$TAG: 开始私钥钱包导入流程，地址: $address")
        keyService.createPrivateKeyPassword(address, activity, callback)
    }

    /**
     * 检查Keystore是否存在
     *
     * @param address 钱包地址
     * @return true表示Keystore存在，false表示不存在
     */
    suspend fun keystoreExists(address: String): Boolean {
        return importWalletInteract.keyStoreExists(address)
    }

    /**
     * 检查Keystore密码是否正确
     *
     * 验证提供的密码是否能够正确解密Keystore文件。
     *
     * @param keystore Keystore JSON字符串
     * @param keystoreAddress Keystore中的地址
     * @param password 要验证的密码
     * @return true表示密码正确，false表示密码错误
     */
    suspend fun checkKeystorePassword(
        keystore: String,
        keystoreAddress: String,
        password: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("$TAG: 开始验证Keystore密码")
                
                val objectMapper = ObjectMapper()
                val walletFile = objectMapper.readValue(keystore, WalletFile::class.java)
                val keyPair = org.web3j.crypto.Wallet.decrypt(password, walletFile)
                val address = Numeric.prependHexPrefix(Keys.getAddress(keyPair))
                
                val isAddressValid = address.equals(keystoreAddress, ignoreCase = true)
                val isPublicKeyValid = keyPair.publicKey.compareTo(BigInteger.ZERO) != 0
                
                val isValid = isAddressValid && isPublicKeyValid
                Timber.d("$TAG: Keystore密码验证结果: $isValid")
                
                isValid
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Keystore密码验证失败")
                false
            }
        }
    }

    /**
     * 重置签名对话框
     */
    fun resetSignDialog() {
        Timber.d("$TAG: 重置签名对话框")
        keyService.resetSigningDialog()
    }

    /**
     * 完成认证
     *
     * @param taskCode 任务代码
     */
    fun completeAuthentication(taskCode: Operation) {
        Timber.d("$TAG: 完成认证，任务代码: $taskCode")
        keyService.completeAuthentication(taskCode)
    }

    /**
     * 认证失败
     *
     * @param taskCode 任务代码
     */
    fun failedAuthentication(taskCode: Operation) {
        Timber.d("$TAG: 认证失败，任务代码: $taskCode")
        keyService.failedAuthentication(taskCode)
    }

    /**
     * 处理钱包导入成功
     *
     * 私有方法，用于统一处理钱包导入成功的逻辑。
     *
     * @param wallet 导入成功的钱包对象
     * @param type 导入类型
     */
    private fun onWallet(wallet: Wallet, type: ImportWalletType) {
        progress.postValue(false)
        _wallet.postValue(Pair(wallet, type))
        Timber.d("$TAG: 钱包导入完成，类型: $type, 地址: ${wallet.address}")
    }

    /**
     * 处理错误
     *
     * 统一的错误处理方法，将异常转换为ErrorEnvelope并通知UI。
     *
     * @param throwable 发生的异常
     */
    private fun onError(throwable: Throwable) {
        progress.postValue(false)
        error.postValue(ErrorEnvelope(C.ErrorCode.UNKNOWN, throwable.message))
        Timber.e(throwable, "$TAG: 发生错误: ${throwable.message}")
    }

    /**
     * 清理资源
     *
     * 在ViewModel销毁时调用，取消所有正在进行的操作。
     */
    override fun onCleared() {
        super.onCleared()
        Timber.d("$TAG: ImportWalletViewModel 清理资源")
    }
}
