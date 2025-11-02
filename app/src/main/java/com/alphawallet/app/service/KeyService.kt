package com.alphawallet.app.service

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.VibrationEffect.DEFAULT_AMPLITUDE
import android.os.Vibrator
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.UserNotAuthenticatedException
import android.util.Pair
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.alphawallet.app.BuildConfig
import com.alphawallet.app.R
import com.alphawallet.app.entity.*
import com.alphawallet.app.entity.Operation.*
import com.alphawallet.app.entity.cryptokeys.KeyEncodingType
import com.alphawallet.app.entity.cryptokeys.KeyServiceException
import com.alphawallet.app.entity.tokenscript.TokenscriptFunction.Companion.ZERO_ADDRESS
import com.alphawallet.app.service.KeyService.AuthenticationLevel.*
import com.alphawallet.app.service.KeystoreAccountService.KEYSTORE_FOLDER
import com.alphawallet.app.service.KeystoreAccountService.bytesFromSignature
import com.alphawallet.app.service.LegacyKeystore.getLegacyPassword
import com.alphawallet.app.util.Utils
import com.alphawallet.app.widget.AWalletAlertDialog
import com.alphawallet.app.widget.SignTransactionDialog
import com.alphawallet.hardware.HardwareCallback
import com.alphawallet.hardware.HardwareDevice
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.hardware.SignatureReturnType
import kotlinx.coroutines.*
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import timber.log.Timber
import wallet.core.jni.*
import java.io.*
import java.security.*
import java.security.cert.CertificateException
import java.security.spec.AlgorithmParameterSpec
import java.util.*
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec

/**
 * KeyService - 密钥管理服务类
 *
 * 这是AlphaWallet中最重要的安全组件，负责管理所有与密钥相关的操作。
 * 主要功能包括：
 * 1. HD钱包密钥生成与管理 - 支持助记词生成、导入和恢复
 * 2. 密钥加密存储 - 使用Android Keystore安全存储密钥
 * 3. 数字签名服务 - 为交易和消息提供数字签名
 * 4. 身份验证管理 - 生物识别、PIN码等安全验证
 * 5. 硬件钱包支持 - 集成外部硬件钱包设备
 * 6. 安全等级管理 - 支持TEE和StrongBox硬件安全模块
 * 7. 密钥升级和迁移 - 支持密钥安全等级的升级
 *
 * 该服务采用Android Keystore作为底层安全存储，支持多种加密算法和安全等级。
 *
 * @param context Android上下文
 * @param analyticsService 分析服务，用于记录关键事件
 *
 * @author AlphaWallet Team
 * @since 2024
 */
class KeyService(
    val context: Context,
    private val analyticsService: AnalyticsServiceType<AnalyticsProperties>,
) : AuthenticationCallback,
    PinAuthenticationCallbackInterface,
    HardwareCallback {
    companion object {
        private const val TAG = "HDWallet"

        // 身份验证持续时间（秒）
        private const val AUTHENTICATION_DURATION_SECONDS = 30

        // 加密算法配置
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE

        // Android密钥存储相关常量
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val LEGACY_CIPHER_ALGORITHM = "AES/CBC/PKCS7Padding"
        const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"

        // 备份警告时间间隔：30天
        const val TIME_BETWEEN_BACKUP_WARNING_MILLIS = 1000L * 60L * 60L * 24L * 30L

        // 默认密钥强度
        private const val DEFAULT_KEY_STRENGTH = 128

        // 设备安全状态
        private var securityStatus = SecurityStatus.NOT_CHECKED

        /**
         * 检查设备是否支持StrongBox
         * @return 是否支持StrongBox硬件安全模块
         */
        fun hasStrongbox(): Boolean = securityStatus == SecurityStatus.HAS_STRONGBOX

        /**
         * 从文件读取字节数组
         * @param path 文件路径
         * @return 字节数组，失败返回null
         */
        fun readBytesFromFile(path: String): ByteArray? {
            val file = File(path)
            return try {
                FileInputStream(file).use { fin ->
                    readBytesFromStream(fin)
                }
            } catch (e: IOException) {
                Timber.e(e, "Error reading bytes from file: $path")
                null
            }
        }

        /**
         * 从输入流读取字节数组
         * @param inputStream 输入流
         * @return 字节数组
         */
        @Throws(IOException::class)
        fun readBytesFromStream(inputStream: InputStream): ByteArray {
            val byteBuffer = ByteArrayOutputStream()
            val bufferSize = 2048
            val buffer = ByteArray(bufferSize)

            var len: Int
            while (inputStream.read(buffer).also { len = it } != -1) {
                byteBuffer.write(buffer, 0, len)
            }

            byteBuffer.close()
            return byteBuffer.toByteArray()
        }

        /**
         * 获取文件路径，支持大小写不敏感的文件查找
         * @param context Android上下文
         * @param fileName 文件名
         * @return 文件绝对路径
         */
        @Synchronized
        fun getFilePath(
            context: Context,
            fileName: String,
        ): String {
            // 检查是否存在完全匹配的文件
            val check = File(context.filesDir, fileName)
            if (check.exists()) {
                return check.absolutePath
            } else {
                // 查找大小写不敏感的匹配文件
                val files = context.filesDir.listFiles()
                if (files != null) {
                    for (checkFile in files) {
                        if (checkFile.name.equals(fileName, ignoreCase = true)) {
                            return checkFile.absolutePath
                        }
                    }
                }
            }

            return check.absolutePath // 理论上不会到达这里
        }
    }

    /**
     * 身份验证等级枚举
     * 定义了不同的硬件安全等级
     */
    enum class AuthenticationLevel {
        /** 未设置安全等级 */
        NOT_SET,

        /** TEE无需身份验证 */
        TEE_NO_AUTHENTICATION,

        /** TEE需要身份验证 */
        TEE_AUTHENTICATION,

        /** StrongBox无需身份验证 */
        STRONGBOX_NO_AUTHENTICATION,

        /** StrongBox需要身份验证 */
        STRONGBOX_AUTHENTICATION,
    }

    /**
     * 密钥升级结果类型枚举
     */
    enum class UpgradeKeyResultType {
        /** 正在请求安全升级 */
        REQUESTING_SECURITY,

        /** 设备无屏幕锁 */
        NO_SCREENLOCK,

        /** 密钥已锁定 */
        ALREADY_LOCKED,

        /** 升级出错 */
        ERROR,

        /** 成功升级 */
        SUCCESSFULLY_UPGRADED,
    }

    /**
     * 密钥升级结果数据类
     * @param result 升级结果类型
     * @param message 结果消息
     */
    data class UpgradeKeyResult(
        val result: UpgradeKeyResultType,
        val message: String,
    )

    /**
     * 设备安全状态枚举
     * 用于在服务启动时检查API安全强度
     */
    private enum class SecurityStatus {
        /** 未检查 */
        NOT_CHECKED,

        /** 无TEE支持 */
        HAS_NO_TEE,

        /** 支持TEE */
        HAS_TEE,

        /** 支持StrongBox */
        HAS_STRONGBOX,
    }

    /**
     * 密钥异常类型枚举
     */
    enum class KeyExceptionType {
        UNKNOWN,
        REQUIRES_AUTH,
        INVALID_CIPHER,
        SUCCESSFUL_DECODE,
        IV_NOT_FOUND,
        ENCRYPTED_FILE_NOT_FOUND,
    }

    // 协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 硬件设备管理器
    private val hardwareDevice: HardwareDevice = HardwareDevice(this)

    // 当前活动和钱包实例
    private var activity: Activity? = null
    private var currentWallet: Wallet? = null

    // 身份验证相关状态
    private var authLevel: AuthenticationLevel = AuthenticationLevel.NOT_SET
    private var requireAuthentication = false

    // UI组件
    private var signDialog: SignTransactionDialog? = null
    private var alertDialog: AWalletAlertDialog? = null

    // 回调接口
    private var callbackInterface: CreateWalletCallbackInterface? = null
    private var importCallback: ImportWalletCallback? = null
    private var signCallback: SignAuthenticationCallback? = null

    init {
        // 加载TrustWallet核心库
        System.loadLibrary("TrustWalletCore")
        // 检查设备安全等级
        checkSecurity()
        Timber.tag(TAG).d("KeyService initialized with auth level: $authLevel")
    }

    /**
     * 创建新的HD密钥
     * 创建一个新的HD钱包，存储助记词并调用HDKeyCreated回调
     * 使用回调模式以支持带身份验证锁的密钥创建
     *
     * @param callingActivity 调用的Activity
     * @param callback 创建完成后的回调接口
     */
    fun createNewHDKey(
        callingActivity: Activity,
        callback: CreateWalletCallbackInterface,
    ) {
        activity = callingActivity
        callbackInterface = callback
        createHDKey()
        Timber.tag(TAG).d("Creating new HD key")
    }

    /**
     * 创建并加密/存储用于导入keystore的身份验证锁定密码
     * 导入私钥的流程几乎相同
     *
     * 流程如下：
     * 1. 获取身份验证事件 - 弹出解锁对话框
     * 2. 身份验证事件后，进入authenticatePass并切换到createPassword()
     * 3. 创建新的强keystore密码，存储密码
     * 4. 调用KeystoreValidated回调
     *
     * @param address 钱包地址
     * @param callingActivity 调用的Activity
     * @param callback 导入完成后的回调接口
     */
    fun createKeystorePassword(
        address: String,
        callingActivity: Activity,
        callback: ImportWalletCallback,
    ) {
        activity = callingActivity
        importCallback = callback
        currentWallet = Wallet(address)
        checkAuthentication(CREATE_KEYSTORE_KEY)
        Timber.tag(TAG).d("Creating keystore password for address: $address")
    }

    /**
     * 流程与createKeystorePassword相同，但在密钥生成成功完成后调用
     * importCallback.KeyValidated
     *
     * @param address 钱包地址
     * @param callingActivity 调用的Activity
     * @param callback 导入完成后的回调接口
     */
    fun createPrivateKeyPassword(
        address: String,
        callingActivity: Activity,
        callback: ImportWalletCallback,
    ) {
        activity = callingActivity
        importCallback = callback
        currentWallet = Wallet(address)
        checkAuthentication(CREATE_PRIVATE_KEY)
        Timber.tag(TAG).d("Creating private key password for address: $address")
    }

    /**
     * 加密并存储HDWallet的助记词
     *
     * 流程：
     * 1. 检查有效的种子短语，生成HDWallet并存储助记词（无身份验证锁）
     * 2. 获取身份验证事件
     * 3. 身份验证后通过authenticatePass切换到importHDKey()
     * 4. ImportHDKey()恢复助记词并用身份验证锁定密钥替换密钥
     * 5. KeyValidated回调将控制权传回viewModel
     *
     * @param seedPhrase 种子短语（助记词）
     * @param callingActivity 调用的Activity
     * @param callback 导入完成后的回调接口
     */
    fun importHDKey(
        seedPhrase: String,
        callingActivity: Activity,
        callback: ImportWalletCallback,
    ) {
        activity = callingActivity
        importCallback = callback

        // 粗略检查有效的密钥导入
        if (!Mnemonic.isValid(seedPhrase)) {
            callback.walletValidated(null, KeyEncodingType.SEED_PHRASE_KEY, AuthenticationLevel.NOT_SET)
            Timber.tag(TAG).w("Invalid seed phrase provided")
        } else {
            val newWallet = HDWallet(seedPhrase, "")
            storeHDKey(newWallet, false) // 存储加密字节以防重新进入
            checkAuthentication(IMPORT_HD_KEY)
            Timber.tag(TAG).d("Importing HD key with valid seed phrase")
        }
    }

    /**
     * 从存储中获取助记词
     *
     * 流程：
     * 1. 调用unpackMnemonic
     * 2. 如果需要身份验证，获取身份验证事件并调用unpackMnemonic
     * 3. 将助记词返回到FetchMnemonic回调
     *
     * @param wallet 钱包对象
     * @param callingActivity 调用的Activity
     * @param callback 获取完成后的回调接口
     */
    fun getMnemonic(
        wallet: Wallet,
        callingActivity: Activity,
        callback: CreateWalletCallbackInterface,
    ) {
        activity = callingActivity
        currentWallet = wallet
        callbackInterface = callback

        try {
            val mnemonic = unpackMnemonic()
            callback.fetchMnemonic(mnemonic)
            Timber.tag(TAG).d("Successfully retrieved mnemonic")
        } catch (e: KeyServiceException) {
            keyFailure(e.message)
        } catch (e: UserNotAuthenticatedException) {
            callingActivity.runOnUiThread {
                checkAuthentication(FETCH_MNEMONIC)
            }
        }
    }

    /**
     * 获取签名授权
     *
     * 流程：
     * 1. 如果需要，获取身份验证事件
     * 2. 在getAuthenticationForSignature恢复操作
     * 3. 获取助记词/密码
     * 4. 重建私钥
     * 5. 签名
     *
     * @param wallet 钱包对象
     * @param callingActivity 调用的Activity
     * @param callback 签名完成后的回调接口
     */
    fun getAuthenticationForSignature(
        wallet: Wallet,
        callingActivity: Activity,
        callback: SignAuthenticationCallback,
    ) {
        signCallback = callback
        activity = callingActivity
        currentWallet = wallet

        when (wallet.type) {
            WalletType.HARDWARE -> {
                // 硬件钱包绕过此步骤，因为我们同时获得身份验证和签名
                signCallback?.gotAuthorisation(true)
            }
            WalletType.KEYSTORE_LEGACY -> {
                // 传统密钥不需要身份验证
                signCallback?.gotAuthorisation(true)
            }
            WalletType.KEYSTORE, WalletType.HDKEY -> {
                checkAuthentication(Operation.CHECK_AUTHENTICATION)
            }
            WalletType.NOT_DEFINED, WalletType.TEXT_MARKER, WalletType.WATCH -> {
                signCallback?.gotAuthorisation(false)
            }

            else -> {}
        }

        Timber.tag(TAG).d("Getting authentication for signature, wallet type: ${wallet.type}")
    }

    /**
     * 设置需要身份验证标志
     */
    fun setRequireAuthentication() {
        requireAuthentication = true
        Timber.tag(TAG).d("Authentication requirement set")
    }

    /**
     * 升级密钥安全等级
     *
     * 流程：
     * 1. 获取身份验证，然后从authenticatePass执行'upgradeKey()'
     * 2. 升级密钥读取助记词/密码，然后使用身份验证调用storeEncryptedBytes
     * 3. 通过signCallback.CreatedKey将结果和流程返回给调用者
     *
     * @param wallet 钱包对象
     * @param callingActivity 调用的Activity
     * @return 升级结果
     */
    fun upgradeKeySecurity(
        wallet: Wallet,
        callingActivity: Activity,
    ): UpgradeKeyResult {
        signCallback = null
        activity = callingActivity
        currentWallet = wallet

        // 首先检查我们是否有生成密钥的能力
        if (!deviceIsLocked()) {
            return UpgradeKeyResult(UpgradeKeyResultType.NO_SCREENLOCK, "Device is not locked")
        }

        val result = upgradeKey()
        Timber.tag(TAG).d("Key security upgrade result: ${result.result}")
        return result
    }

    /**
     * 签名数据
     *
     * 此函数的流程必须更简单 - 此函数从无法访问Activity的代码调用，因此无法创建
     * 任何签名对话框。必须在进入签名流程之前生成身份验证事件。
     *
     * 如果是HDWallet - 解密助记词，重新生成私钥，生成摘要，使用Trezor库签名摘要
     * 如果是Keystore - 获取keystore JSON文件，解密keystore密码，重新生成Web3j凭据并签名
     *
     * @param wallet 钱包对象
     * @param tbsData 待签名数据
     * @return 签名结果
     */
    @Synchronized
    fun signData(
        wallet: Wallet,
        tbsData: ByteArray,
    ): SignatureFromKey {
        val returnSig = SignatureFromKey()
        currentWallet = wallet

        when (wallet.type) {
            WalletType.KEYSTORE_LEGACY, WalletType.KEYSTORE -> {
                return signWithKeystore(tbsData)
            }
            WalletType.HDKEY -> {
                try {
                    val mnemonic = unpackMnemonic()
                    val newWallet = HDWallet(mnemonic, "")
                    val pk = newWallet.getKeyForCoin(CoinType.ETHEREUM)
                    val digest = Hash.keccak256(tbsData)
                    returnSig.signature = pk.sign(digest, Curve.SECP256K1)
                    returnSig.sigType = SignatureReturnType.SIGNATURE_GENERATED
                    Timber.tag(TAG).d("HD wallet signature generated successfully")
                } catch (e: KeyServiceException) {
                    returnSig.failMessage = e.message
                } catch (e: UserNotAuthenticatedException) {
                    returnSig.failMessage = e.message
                }
            }
            WalletType.WATCH -> {
                returnSig.failMessage = context.getString(R.string.action_watch_account)
            }
            WalletType.HARDWARE -> {
                activity?.let { act ->
                    hardwareDevice.activateReader(act)
                    hardwareDevice.setSigningData(
                        org.web3j.crypto.Hash
                            .sha3(tbsData),
                    )
                }
                returnSig.sigType = SignatureReturnType.SIGNING_POSTPONED
            }
            WalletType.NOT_DEFINED, WalletType.TEXT_MARKER -> {
                returnSig.failMessage = context.getString(R.string.no_key)
            }

            else -> {}
        }

        return returnSig
    }

    /**
     * 获取用于导出/备份keystore的密码
     *
     * @param wallet 钱包对象
     * @param callingActivity 调用的Activity
     * @param callback 获取完成后的回调接口
     */
    fun getPassword(
        wallet: Wallet,
        callingActivity: Activity,
        callback: CreateWalletCallbackInterface,
    ) {
        activity = callingActivity
        currentWallet = wallet
        callbackInterface = callback

        try {
            when (wallet.type) {
                WalletType.KEYSTORE -> {
                    val password = unpackMnemonic()
                    callback.fetchMnemonic(password)
                }
                WalletType.KEYSTORE_LEGACY -> {
                    val password = String(getLegacyPassword(context, wallet.address))
                    callback.fetchMnemonic(password)
                }
                else -> {
                    // 其他钱包类型不支持密码获取
                }
            }
            Timber.tag(TAG).d("Password retrieved for wallet type: ${wallet.type}")
        } catch (e: UserNotAuthenticatedException) {
            checkAuthentication(FETCH_MNEMONIC)
        } catch (e: ServiceErrorException) {
            // 传统keystore错误
            if (!BuildConfig.DEBUG) analyticsService.recordException(e)
            Timber.e(e, "Legacy keystore error")
        } catch (e: KeyServiceException) {
            keyFailure(e.message)
        } catch (e: Exception) {
            Timber.e(e, "Error getting password")
        }
    }

    /**
     * 重置签名对话框
     */
    fun resetSigningDialog() {
        signDialog?.close()
        signDialog = null
    }

    /**
     * 测试密码解密
     *
     * @param walletAddress 钱包地址
     * @param cipherAlgorithm 加密算法
     * @return 解密结果和密钥数据的配对
     */
    fun testCipher(
        walletAddress: String,
        cipherAlgorithm: String,
    ): Pair<KeyExceptionType, String?> {
        var retVal = KeyExceptionType.UNKNOWN
        var keyData: String? = null

        try {
            val encryptedDataFilePath = getFilePath(context, walletAddress)
            val keyIv = getFilePath(context, walletAddress + "iv")
            val ivExists = File(keyIv).exists()
            val aliasExists = File(encryptedDataFilePath).exists()

            if (!ivExists) {
                retVal = KeyExceptionType.IV_NOT_FOUND
                throw Exception("iv file doesn't exist")
            }
            if (!aliasExists) {
                retVal = KeyExceptionType.ENCRYPTED_FILE_NOT_FOUND
                throw Exception("Key file doesn't exist")
            }

            // 测试传统密钥
            val iv = readBytesFromFile(keyIv)

            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            val secretKey = keyStore.getKey(walletAddress, null) as SecretKey

            val outCipher = Cipher.getInstance(cipherAlgorithm)
            val spec: AlgorithmParameterSpec =
                if (cipherAlgorithm == CIPHER_ALGORITHM) {
                    GCMParameterSpec(128, iv)
                } else {
                    IvParameterSpec(iv)
                }
            outCipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val cipherInputStream = CipherInputStream(FileInputStream(encryptedDataFilePath), outCipher)
            val keyBytes = readBytesFromStream(cipherInputStream)
            keyData = String(keyBytes)
            retVal = KeyExceptionType.SUCCESSFUL_DECODE
        } catch (e: UserNotAuthenticatedException) {
            retVal = KeyExceptionType.REQUIRES_AUTH
        } catch (e: InvalidKeyException) {
            // 错误的规格
            retVal = KeyExceptionType.INVALID_CIPHER
        } catch (e: Exception) {
            Timber.e(e, "Error testing cipher")
        }

        return Pair(retVal, keyData)
    }

    /**
     * 检查钱包是否有keystore
     *
     * @param walletAddress 钱包地址
     * @return 是否存在keystore
     */
    fun hasKeystore(walletAddress: String): Boolean =
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            val matchingAddr = findMatchingAddrInKeyStore(walletAddress)
            keyStore.containsAlias(matchingAddr)
        } catch (e: Exception) {
            Timber.e(e, "Error checking keystore for address: $walletAddress")
            false
        }

    /**
     * 删除密钥的所有痕迹：Android keystore中的密钥、私有数据区域中的加密字节和iv文件
     *
     * @param keyAddress 密钥地址
     */
    @Synchronized
    fun deleteKey(keyAddress: String) {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            val matchingAddr = findMatchingAddrInKeyStore(keyAddress)
            if (keyStore.containsAlias(matchingAddr)) {
                keyStore.deleteEntry(matchingAddr)
            }
            val encryptedKeyBytes = File(getFilePath(context, matchingAddr))
            val encryptedBytesFileIV = File(getFilePath(context, matchingAddr + "iv"))
            if (encryptedKeyBytes.exists()) encryptedKeyBytes.delete()
            if (encryptedBytesFileIV.exists()) encryptedBytesFileIV.delete()
            deleteAccount(matchingAddr)
            Timber.tag(TAG).d("Key deleted for address: $keyAddress")
        } catch (e: Exception) {
            Timber.e(e, "Error deleting key for address: $keyAddress")
        }
    }

    /**
     * 删除账户相关的所有文件
     *
     * @param address 账户地址
     */
    @Throws(Exception::class)
    fun deleteAccount(address: String) {
        val cleanedAddr = Numeric.cleanHexPrefix(address).lowercase()
        deleteAccountFiles(cleanedAddr)

        // 现在删除数据库文件（即账户的代币、交易和Tokenscript数据）
        val contents = context.filesDir.listFiles()
        if (contents != null) {
            for (f in contents) {
                val fileName = f.name.lowercase()
                if (fileName.contains(cleanedAddr.lowercase())) {
                    deleteRecursive(f)
                }
            }
        }
        Timber.tag(TAG).d("Account deleted: $address")
    }

    // ==================== HardwareCallback接口实现 ====================

    override fun signedMessageFromHardware(returnSig: SignatureFromKey) {
        signCallback?.gotSignature(returnSig)
    }

    override fun onCardReadStart() {
        // TODO: 显示卡片读取进度
    }

    override fun hardwareCardError(message: String) {
        signCallback?.signingError(message)
    }

    // ==================== AuthenticationCallback接口实现 ====================

    override fun completeAuthentication(callbackId: Operation?) {
        authenticatePass(callbackId)
    }

    override fun failedAuthentication(taskCode: Operation?) {
        authenticateFail("Authentication fail", AuthenticationFailType.PIN_FAILED, taskCode)
    }

    override fun authenticatePass(operation: Operation?) {
        // 恢复密钥操作
        when (operation) {
            CREATE_HD_KEY -> {
                // 注意：当前未使用：如果我们创建带身份验证的HD密钥可能会使用
                createHDKey()
            }
            FETCH_MNEMONIC -> {
                try {
                    callbackInterface?.fetchMnemonic(unpackMnemonic())
                } catch (e: Exception) {
                    keyFailure(e.message)
                }
            }
            IMPORT_HD_KEY -> {
                importHDKey()
            }
            CHECK_AUTHENTICATION -> {
                signCallback?.gotAuthorisation(true)
            }
            UPGRADE_HD_KEY, UPGRADE_KEYSTORE_KEY -> {
                upgradeKey()
            }
            CREATE_KEYSTORE_KEY, CREATE_PRIVATE_KEY -> {
                createPassword(operation)
            }
            else -> {
                // 其他操作
            }
        }
    }

    override fun authenticateFail(
        fail: String?,
        failType: AuthenticationFailType?,
        callbackId: Operation?,
    ) {
        when (failType) {
            AuthenticationFailType.AUTHENTICATION_DIALOG_CANCELLED -> {
                // 用户对话框取消
                cancelAuthentication()
            }
            AuthenticationFailType.FINGERPRINT_ERROR_CANCELED -> {
                // 用户取消对话框时调用
                return
            }
            AuthenticationFailType.FINGERPRINT_NOT_VALIDATED -> {
                vibrate()
                activity?.runOnUiThread {
                    Toast.makeText(context, R.string.fingerprint_authentication_failed, Toast.LENGTH_SHORT).show()
                }
            }
            AuthenticationFailType.PIN_FAILED -> {
                vibrate()
            }
            AuthenticationFailType.DEVICE_NOT_SECURE -> {
                // 注意：允许用户创建无身份验证解锁的密钥确保我们永远不会到达这里
                // 处理用户到达这里的某种边缘情况
                showInsecure(callbackId)
            }

            else -> {}
        }

        if (callbackId == UPGRADE_HD_KEY) {
            signCallback?.gotAuthorisation(false)
        }

        if (activity?.isDestroyed == true) {
            cancelAuthentication()
        }
    }

    override fun legacyAuthRequired(
        callbackId: Operation?,
        dialogTitle: String?,
        desc: String?,
    ) {
        // 传统身份验证相关代码（已注释）
    }

    // ==================== 私有方法 ====================

    /**
     * 创建HD密钥
     */
    private fun createHDKey() {
        val newWallet = HDWallet(DEFAULT_KEY_STRENGTH, "")
        val success = storeHDKey(newWallet, false) // 初始创建无身份验证密钥
        callbackInterface?.HDKeyCreated(
            if (success) currentWallet?.address else null,
            context,
            authLevel,
        )
        Timber.tag(TAG).d("HD key creation result: $success")
    }

    /**
     * 在需要身份验证事件并且用户已完成身份验证事件后调用
     */
    private fun importHDKey() {
        val requiresAuthentication = !Utils.isRunningTest()

        // 首先从非身份验证锁定密钥恢复种子短语
        // 这消除了将种子短语作为堆上成员保留的需要 - 使密钥操作更安全
        try {
            val seedPhrase = unpackMnemonic()
            val newWallet = HDWallet(seedPhrase, "")
            val success = storeHDKey(newWallet, requiresAuthentication)
            val reportAddress = if (success) currentWallet?.address else null
            importCallback?.walletValidated(reportAddress, KeyEncodingType.SEED_PHRASE_KEY, authLevel)
            Timber.tag(TAG).d("HD key import result: $success")
        } catch (e: Exception) {
            when (e) {
                is UserNotAuthenticatedException, is KeyServiceException -> {
                    keyFailure(e.message)
                }
                else -> {
                    Timber.e(e, "Error importing HD key")
                    keyFailure(e.message)
                }
            }
        }
    }

    /**
     * 在提供身份验证后到达
     * @return 升级结果
     */
    private fun upgradeKey(): UpgradeKeyResult {
        try {
            var secretData: String? = null

            when (currentWallet?.type) {
                WalletType.HDKEY, WalletType.KEYSTORE -> {
                    secretData = unpackMnemonic()
                }
                WalletType.KEYSTORE_LEGACY -> {
                    secretData = String(getLegacyPassword(context, currentWallet?.address ?: ""))
                }
                else -> {
                    // 其他类型不支持
                }
            }

            if (secretData == null) {
                return UpgradeKeyResult(UpgradeKeyResultType.ERROR, context.getString(R.string.no_key_found))
            }

            val keyStored = storeEncryptedBytes(secretData.toByteArray(), true, currentWallet?.address ?: "")
            return if (keyStored) {
                UpgradeKeyResult(UpgradeKeyResultType.SUCCESSFULLY_UPGRADED, "")
            } else {
                UpgradeKeyResult(
                    UpgradeKeyResultType.ERROR,
                    context.getString(R.string.unable_store_key, currentWallet?.address),
                )
            }
        } catch (e: ServiceErrorException) {
            // 传统keystore错误
            if (!BuildConfig.DEBUG) analyticsService.recordException(e)
            Timber.e(e, "Service error during key upgrade")
            return UpgradeKeyResult(UpgradeKeyResultType.ERROR, e.localizedMessage ?: "Service error")
        } catch (e: Exception) {
            Timber.e(e, "Error upgrading key")
            return UpgradeKeyResult(UpgradeKeyResultType.ERROR, e.localizedMessage ?: "Unknown error")
        }
    }

    /**
     * 存储HD密钥
     *
     * @param newWallet 新钱包
     * @param keyRequiresAuthentication 是否需要身份验证
     * @return 是否成功存储
     */
    @Synchronized
    private fun storeHDKey(
        newWallet: HDWallet,
        keyRequiresAuthentication: Boolean,
    ): Boolean {
        val pk = newWallet.getKeyForCoin(CoinType.ETHEREUM)
        currentWallet = Wallet(CoinType.ETHEREUM.deriveAddress(pk))

        return storeEncryptedBytes(
            newWallet.mnemonic().toByteArray(),
            keyRequiresAuthentication,
            currentWallet?.address ?: "",
        )
    }

    /**
     * 存储加密字节
     *
     * @param data 要加密的数据
     * @param createAuthLocked 是否创建身份验证锁定
     * @param fileName 文件名
     * @return 是否成功存储
     */
    @Synchronized
    private fun storeEncryptedBytes(
        data: ByteArray,
        createAuthLocked: Boolean,
        fileName: String,
    ): Boolean {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)

            val encryptedHDKeyPath = getFilePath(context, fileName)
            val keyGenerator =
                getMaxSecurityKeyGenerator(fileName, createAuthLocked)
                    ?: return false

            val secretKey = keyGenerator.generateKey()
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ivPath = getFilePath(context, fileName + "iv")

            val success = writeBytesToFile(ivPath, iv)
            if (!success) {
                throw ServiceErrorException(
                    ServiceErrorException.ServiceErrorCode.FAIL_TO_SAVE_IV_FILE,
                    "Failed to create the iv file for: ${fileName}iv",
                )
            }

            try {
                CipherOutputStream(FileOutputStream(encryptedHDKeyPath), cipher).use { cipherOutputStream ->
                    cipherOutputStream.write(data)
                }
            } catch (ex: Exception) {
                throw ServiceErrorException(
                    ServiceErrorException.ServiceErrorCode.KEY_STORE_ERROR,
                    "Failed to create the file for: $fileName",
                )
            }

            Timber.tag(TAG).d("Successfully stored encrypted bytes for: $fileName")
            return true
        } catch (ex: Exception) {
            deleteKey(fileName)
            Timber.tag(TAG).e(ex, "Key store error for: $fileName")
        }

        return false
    }

    /**
     * 获取最大安全性密钥生成器
     *
     * @param keyAddress 密钥地址
     * @param useAuthentication 是否使用身份验证
     * @return 密钥生成器，失败返回null
     */
    private fun getMaxSecurityKeyGenerator(
        keyAddress: String,
        useAuthentication: Boolean,
    ): KeyGenerator? {
        return try {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)

            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && tryInitStrongBoxKey(keyGenerator, keyAddress, useAuthentication) -> {
                    authLevel = if (useAuthentication) AuthenticationLevel.STRONGBOX_AUTHENTICATION else STRONGBOX_NO_AUTHENTICATION
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && tryInitStrongBoxKey(keyGenerator, keyAddress, false) -> {
                    authLevel = STRONGBOX_NO_AUTHENTICATION
                }
                tryInitTEEKey(keyGenerator, keyAddress, useAuthentication) -> {
                    // 回退到非StrongBox
                    authLevel = if (useAuthentication) AuthenticationLevel.TEE_AUTHENTICATION else TEE_NO_AUTHENTICATION
                }
                tryInitTEEKey(keyGenerator, keyAddress, false) -> {
                    authLevel = TEE_NO_AUTHENTICATION
                }
                else -> {
                    authLevel = AuthenticationLevel.NOT_SET
                    return null
                }
            }

            keyGenerator
        } catch (e: NoSuchAlgorithmException) {
            Timber.e(e, "Algorithm not available")
            authLevel = AuthenticationLevel.NOT_SET
            null
        } catch (e: NoSuchProviderException) {
            Timber.e(e, "Provider not available")
            authLevel = AuthenticationLevel.NOT_SET
            null
        } catch (e: Exception) {
            Timber.e(e, "Error getting key generator")
            authLevel = AuthenticationLevel.NOT_SET
            null
        }
    }

    /**
     * 尝试初始化StrongBox密钥
     *
     * @param keyGenerator 密钥生成器
     * @param keyAddress 密钥地址
     * @param useAuthentication 是否使用身份验证
     * @return 是否成功初始化
     */
    @RequiresApi(api = Build.VERSION_CODES.P)
    private fun tryInitStrongBoxKey(
        keyGenerator: KeyGenerator,
        keyAddress: String,
        useAuthentication: Boolean,
    ): Boolean =
        try {
            keyGenerator.init(
                KeyGenParameterSpec
                    .Builder(
                        keyAddress,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(BLOCK_MODE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(useAuthentication)
                    .setIsStrongBoxBacked(true)
                    .setInvalidatedByBiometricEnrollment(false)
                    .setUserAuthenticationValidityDurationSeconds(AUTHENTICATION_DURATION_SECONDS)
                    .setRandomizedEncryptionRequired(true)
                    .setEncryptionPaddings(PADDING)
                    .build(),
            )

            keyGenerator.generateKey()
            true
        } catch (e: StrongBoxUnavailableException) {
            false
        } catch (e: InvalidAlgorithmParameterException) {
            false
        }

    /**
     * 尝试初始化TEE密钥
     *
     * @param keyGenerator 密钥生成器
     * @param keyAddress 密钥地址
     * @param useAuthentication 是否使用身份验证
     * @return 是否成功初始化
     */
    private fun tryInitTEEKey(
        keyGenerator: KeyGenerator,
        keyAddress: String,
        useAuthentication: Boolean,
    ): Boolean =
        try {
            keyGenerator.init(
                KeyGenParameterSpec
                    .Builder(
                        keyAddress,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(BLOCK_MODE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(useAuthentication)
                    .setInvalidatedByBiometricEnrollment(false)
                    .setUserAuthenticationValidityDurationSeconds(AUTHENTICATION_DURATION_SECONDS)
                    .setRandomizedEncryptionRequired(true)
                    .setEncryptionPaddings(PADDING)
                    .build(),
            )
            true
        } catch (e: IllegalStateException) {
            // 由于没有锁定而无法创建密钥
            false
        } catch (e: InvalidAlgorithmParameterException) {
            false
        }

    /**
     * 检查身份验证
     *
     * @param operation 操作类型
     */
    private fun checkAuthentication(operation: Operation) {
        if (Utils.isRunningTest()) {
            // 在调试构建模式下运行测试，我们不使用密钥解锁
            requireAuthentication = false
            authenticatePass(operation)
            return
        }

        val dialogTitle =
            when (operation) {
                UPGRADE_HD_KEY, UPGRADE_KEYSTORE_KEY, CREATE_PRIVATE_KEY, CREATE_KEYSTORE_KEY, IMPORT_HD_KEY, CREATE_HD_KEY -> {
                    // 这些条件总是解锁
                    context.getString(R.string.provide_authentication)
                }
                FETCH_MNEMONIC, CHECK_AUTHENTICATION, SIGN_DATA -> {
                    // 这里解锁可能是可选的
                    if (!requireAuthentication &&
                        (currentWallet?.authLevel == TEE_NO_AUTHENTICATION || currentWallet?.authLevel == STRONGBOX_NO_AUTHENTICATION) &&
                        !requiresUnlock() && signCallback != null
                    ) {
                        signCallback?.gotAuthorisation(true)
                        return
                    }
                    context.getString(R.string.unlock_private_key)
                }
                else -> {
                    context.getString(R.string.unlock_private_key)
                }
            }

        resetSigningDialog()
        activity?.let { act ->
            signDialog = SignTransactionDialog(context)
            signDialog?.getAuthentication(this, act, operation)
        }
        requireAuthentication = false
    }

    /**
     * 解包助记词
     *
     * @return 助记词字符串
     * @throws KeyServiceException 密钥服务异常
     * @throws UserNotAuthenticatedException 用户未认证异常
     */
    @Synchronized
    @Throws(KeyServiceException::class, UserNotAuthenticatedException::class)
    private fun unpackMnemonic(): String {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            val matchingAddr = findMatchingAddrInKeyStore(currentWallet?.address ?: "")
            if (!keyStore.containsAlias(matchingAddr)) {
                throw KeyServiceException("Key not found in keystore. Re-import key.")
            }

            // 创建到加密字节的流
            val encryptedHDKeyBytes = FileInputStream(getFilePath(context, matchingAddr))
            val secretKey = keyStore.getKey(matchingAddr, null) as SecretKey
            val ivExists = File(getFilePath(context, matchingAddr + "iv")).exists()

            val iv =
                if (ivExists) {
                    readBytesFromFile(getFilePath(context, matchingAddr + "iv"))
                } else {
                    null
                }

            if (iv == null || iv.isEmpty()) {
                throw KeyServiceException(context.getString(R.string.cannot_read_encrypt_file))
            }

            val outCipher = Cipher.getInstance(CIPHER_ALGORITHM)
            val spec = GCMParameterSpec(128, iv)
            outCipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val cipherInputStream = CipherInputStream(encryptedHDKeyBytes, outCipher)
            val mnemonicBytes = readBytesFromStream(cipherInputStream)
            return String(mnemonicBytes)
        } catch (e: InvalidKeyException) {
            if (e is UserNotAuthenticatedException) {
                throw UserNotAuthenticatedException(context.getString(R.string.authentication_error))
            } else {
                throw KeyServiceException(e.message ?: "Invalid key")
            }
        } catch (e: UnrecoverableKeyException) {
            throw KeyServiceException(context.getString(R.string.device_security_changed))
        } catch (e: Exception) {
            when (e) {
                is IOException, is CertificateException, is KeyStoreException,
                is NoSuchAlgorithmException, is NoSuchPaddingException,
                is InvalidAlgorithmParameterException,
                -> {
                    Timber.e(e, "Error unpacking mnemonic")
                    throw KeyServiceException(e.message ?: "Mnemonic unpacking error")
                }
                else -> {
                    throw KeyServiceException(e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * 使用keystore签名
     *
     * @param transactionBytes 交易字节
     * @return 签名结果
     */
    @Synchronized
    private fun signWithKeystore(transactionBytes: ByteArray): SignatureFromKey {
        // 1. 从存储获取密码
        // 2. 构造凭据
        // 3. 签名
        val returnSig = SignatureFromKey()

        try {
            val password =
                when (currentWallet?.type) {
                    WalletType.KEYSTORE -> unpackMnemonic()
                    WalletType.KEYSTORE_LEGACY -> String(getLegacyPassword(context, currentWallet?.address ?: ""))
                    else -> ""
                }

            val keyFolder = File(context.filesDir, KEYSTORE_FOLDER)
            val credentials = KeystoreAccountService.getCredentials(keyFolder, currentWallet?.address ?: "", password)
            val signatureData = Sign.signMessage(transactionBytes, credentials.ecKeyPair)
            returnSig.signature = bytesFromSignature(signatureData)
            returnSig.sigType = SignatureReturnType.SIGNATURE_GENERATED // 只有正确生成签名才到达这里
            Timber.tag(TAG).d("Keystore signature generated successfully")
        } catch (e: ServiceErrorException) {
            // 传统keystore错误
            if (!BuildConfig.DEBUG) analyticsService.recordException(e)
            returnSig.failMessage = e.message
            Timber.e(e, "Service error during keystore signing")
        } catch (e: Exception) {
            returnSig.failMessage = e.message
            Timber.e(e, "Error signing with keystore")
        }

        return returnSig
    }

    /**
     * 在keystore中查找匹配的地址（忽略大小写）
     *
     * @param keyAddress 密钥地址
     * @return 匹配的地址
     */
    private fun findMatchingAddrInKeyStore(keyAddress: String): String {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            val keys = keyStore.aliases()

            while (keys.hasMoreElements()) {
                val thisKey = keys.nextElement()
                if (keyAddress.equals(thisKey, ignoreCase = true)) {
                    return thisKey
                }
            }
            keyAddress
        } catch (e: Exception) {
            Timber.e(e, "Error finding matching address in keystore")
            keyAddress
        }
    }

    /**
     * 将字节写入文件
     *
     * @param path 文件路径
     * @param data 数据
     * @return 是否成功写入
     */
    private fun writeBytesToFile(
        path: String,
        data: ByteArray,
    ): Boolean {
        val file = File(path)
        return try {
            FileOutputStream(file).use { fos ->
                fos.write(data)
            }
            true
        } catch (e: IOException) {
            Timber.e(e, "Exception while writing file: $path")
            false
        }
    }

    /**
     * 删除账户文件
     *
     * @param address 地址
     */
    @Throws(Exception::class)
    private fun deleteAccountFiles(address: String) {
        val cleanedAddr = Numeric.cleanHexPrefix(address)
        val keyFolder = File(context.filesDir, KEYSTORE_FOLDER)
        val contents = keyFolder.listFiles()

        if (contents != null) {
            for (f in contents) {
                if (f.name.contains(cleanedAddr)) {
                    f.delete()
                }
            }
        }
    }

    /**
     * 递归删除文件或目录
     *
     * @param fp 文件或目录
     */
    private fun deleteRecursive(fp: File) {
        if (fp.isDirectory) {
            val contents = fp.listFiles()
            if (contents != null) {
                for (child in contents) {
                    deleteRecursive(child)
                }
            }
        }
        fp.delete()
    }

    /**
     * 检查设备安全等级
     */
    private fun checkSecurity() {
        if (securityStatus == SecurityStatus.NOT_CHECKED) {
            getMaxSecurityKeyGenerator(ZERO_ADDRESS, false)
            securityStatus =
                when (authLevel) {
                    AuthenticationLevel.NOT_SET -> SecurityStatus.HAS_NO_TEE
                    TEE_NO_AUTHENTICATION, AuthenticationLevel.TEE_AUTHENTICATION -> SecurityStatus.HAS_TEE
                    STRONGBOX_NO_AUTHENTICATION, AuthenticationLevel.STRONGBOX_AUTHENTICATION -> SecurityStatus.HAS_STRONGBOX
                }
            Timber.tag(TAG).d("Security check completed, status: $securityStatus, auth level: $authLevel")
        }
    }

    /**
     * 检查是否需要解锁
     *
     * @return 是否需要解锁
     */
    private fun requiresUnlock(): Boolean =
        try {
            unpackMnemonic()
            false
        } catch (e: Exception) {
            true
        }

    /**
     * 检查设备是否被锁定
     *
     * @return 设备是否安全锁定
     */
    private fun deviceIsLocked(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceSecure ?: false
    }

    /**
     * 振动反馈
     */
    private fun vibrate() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibe = VibrationEffect.createOneShot(200, DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibe)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        }
    }

    /**
     * 密钥操作失败处理
     *
     * @param message 失败消息
     */
    private fun keyFailure(message: String?) {
        if (message.isNullOrEmpty() || !authorisationFailMessage(message)) {
            when {
                callbackInterface != null -> callbackInterface?.keyFailure(message)
                signCallback != null -> signCallback?.gotAuthorisation(false)
                else -> authorisationFailMessage(message)
            }
        }
    }

    /**
     * 取消身份验证
     */
    private fun cancelAuthentication() {
        signCallback?.cancelAuthentication() ?: callbackInterface?.cancelAuthentication()
    }

    /**
     * 显示授权失败消息
     *
     * @param message 消息
     * @return 是否显示了消息
     */
    private fun authorisationFailMessage(message: String?): Boolean {
        if (alertDialog?.isShowing == true) {
            activity?.runOnUiThread { alertDialog?.dismiss() }
        }
        if (activity?.isDestroyed == true) {
            return false
        }

        activity?.runOnUiThread {
            alertDialog = AWalletAlertDialog(activity!!)
            alertDialog?.apply {
                setIcon(AWalletAlertDialog.ERROR)
                setTitle(R.string.key_error)
                setMessage(message ?: "Unknown error")
                setButtonText(R.string.action_continue)
                setCanceledOnTouchOutside(true)
                setButtonListener {
                    keyFailure("")
                    dismiss()
                }
                setOnCancelListener {
                    keyFailure("")
                    cancelAuthentication()
                }
                show()
            }
        }

        return true
    }

    /**
     * 显示设备不安全警告
     * 当前行为：允许用户创建不安全的密钥
     *
     * @param callbackId 回调ID
     */
    private fun showInsecure(callbackId: Operation?) {
        // 只在特定场合显示"不安全"消息，否则直接通过
        val shouldShowWarning =
            when (callbackId) {
                CREATE_HD_KEY, IMPORT_HD_KEY, CREATE_PRIVATE_KEY, CREATE_KEYSTORE_KEY,
                UPGRADE_KEYSTORE_KEY, UPGRADE_HD_KEY,
                -> true
                else -> {
                    // 继续使用密钥，不显示未锁定警告
                    authenticatePass(callbackId)
                    return
                }
            }

        if (shouldShowWarning) {
            val dialog = AWalletAlertDialog(activity!!)
            dialog.apply {
                setIcon(AWalletAlertDialog.ERROR)
                setTitle(R.string.device_insecure)
                setMessage(R.string.device_not_secure_warning)
                setButtonText(R.string.action_continue)
                setCanceledOnTouchOutside(false)
                setButtonListener {
                    // 继续操作
                    when (callbackId) {
                        UPGRADE_KEYSTORE_KEY, UPGRADE_HD_KEY -> {
                            // 关闭签名对话框并取消身份验证
                            cancelAuthentication()
                        }
                        else -> {
                            authenticatePass(callbackId)
                        }
                    }
                    dismiss()
                }
                show()
            }
        }
    }

    /**
     * 创建密码
     * 仅在身份验证事件后调用
     *
     * @param operation 操作类型
     */
    private fun createPassword(operation: Operation) {
        val requireAuthentication = !Utils.isRunningTest()

        // 生成密码
        val newPassword = ByteArray(256)
        val random =
            try {
                // 尝试使用优质随机源
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    SecureRandom.getInstanceStrong() // 这可能抛出NoSuchAlgorithmException
                } else {
                    SecureRandom()
                }
            } catch (e: NoSuchAlgorithmException) {
                SecureRandom()
            }

        random.nextBytes(newPassword)

        val success = storeEncryptedBytes(newPassword, requireAuthentication, currentWallet?.address ?: "")

        if (!success) {
            authorisationFailMessage(context.getString(R.string.please_enable_security))
        } else {
            when (operation) {
                CREATE_KEYSTORE_KEY -> {
                    importCallback?.walletValidated(String(newPassword), KeyEncodingType.KEYSTORE_KEY, authLevel)
                }
                CREATE_PRIVATE_KEY -> {
                    importCallback?.walletValidated(String(newPassword), KeyEncodingType.RAW_HEX_KEY, authLevel)
                }
                else -> {
                    // 其他操作
                }
            }
        }
    }

    /**
     * 销毁服务时清理资源
     */
    fun destroy() {
        serviceScope.cancel()
        resetSigningDialog()
        alertDialog?.dismiss()
        Timber.tag(TAG).d("KeyService destroyed")
    }
}
