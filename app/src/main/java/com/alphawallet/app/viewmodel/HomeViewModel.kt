package com.alphawallet.app.viewmodel

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.alphawallet.app.BuildConfig
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.entity.AnalyticsProperties
import com.alphawallet.app.entity.CryptoFunctions
import com.alphawallet.app.entity.EIP681Type
import com.alphawallet.app.entity.FragmentMessenger
import com.alphawallet.app.entity.GitHubRelease
import com.alphawallet.app.entity.NetworkInfo
import com.alphawallet.app.entity.QRResult
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.Version
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.analytics.QrScanResultType
import com.alphawallet.app.entity.attestation.ImportAttestation
import com.alphawallet.app.interact.FetchWalletsInteract
import com.alphawallet.app.interact.GenericWalletInteract
import com.alphawallet.app.repository.CurrencyRepositoryType
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.repository.EthereumNetworkRepositoryType
import com.alphawallet.app.repository.LocaleRepositoryType
import com.alphawallet.app.repository.PreferenceRepositoryType
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.app.router.ExternalBrowserRouter
import com.alphawallet.app.router.ImportTokenRouter
import com.alphawallet.app.router.MyAddressRouter
import com.alphawallet.app.service.AlphaWalletNotificationService
import com.alphawallet.app.service.AnalyticsServiceType
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.service.GasService
import com.alphawallet.app.service.RealmManager
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.service.TransactionsService
import com.alphawallet.app.ui.AddTokenActivity
import com.alphawallet.app.ui.HomeActivity
import com.alphawallet.app.ui.HomeActivityKt
import com.alphawallet.app.ui.ImportWalletActivity
import com.alphawallet.app.ui.SendActivity
import com.alphawallet.app.ui.WalletConnectV2Activity
import com.alphawallet.app.util.QRParser
import com.alphawallet.app.util.RateApp
import com.alphawallet.app.util.Utils
import com.alphawallet.app.util.ens.AWEnsResolver
import com.alphawallet.app.widget.EmailPromptView
import com.alphawallet.app.widget.QRCodeActionsView
import com.alphawallet.app.widget.WhatsNewView
import com.alphawallet.ethereum.EthereumNetworkBase
import com.alphawallet.token.entity.ContractInfo
import com.alphawallet.token.entity.MagicLinkData
import com.alphawallet.token.tools.ParseMagicLink
import com.alphawallet.token.tools.TokenDefinition
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.web3j.utils.Numeric
import timber.log.Timber
import wallet.core.jni.Hash
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

/**
 * HomeViewModel - AlphaWallet主页视图模型
 *
 * 这是AlphaWallet应用主页的核心ViewModel，负责管理主页的所有业务逻辑和数据状态。
 * 主要功能包括：
 * - 钱包管理：默认钱包设置、钱包名称显示、ENS域名解析
 * - 二维码处理：扫描各种类型的二维码（地址、支付、WalletConnect等）
 * - 交易管理：交易引擎控制、交易状态监控
 * - 用户界面：弹窗管理、导航控制、用户提示
 * - 应用更新：版本检查、更新提醒
 * - 通知管理：推送通知订阅和管理
 * - 文件导入：TokenScript文件导入和处理
 * - 数据同步：货币设置、本地化配置
 *
 * 使用Hilt进行依赖注入，继承自BaseViewModel提供基础功能。
 * 采用协程进行异步操作，确保UI线程不被阻塞。
 */
@HiltViewModel
class HomeViewModel
    @Inject
    internal constructor(
        preferenceRepository: PreferenceRepositoryType,
        localeRepository: LocaleRepositoryType,
        importTokenRouter: ImportTokenRouter,
        assetDefinitionService: AssetDefinitionService,
        genericWalletInteract: GenericWalletInteract,
        fetchWalletsInteract: FetchWalletsInteract,
        currencyRepository: CurrencyRepositoryType,
        ethereumNetworkRepository: EthereumNetworkRepositoryType,
        myAddressRouter: MyAddressRouter,
        transactionsService: TransactionsService,
        analyticsService: AnalyticsServiceType<AnalyticsProperties>?,
        externalBrowserRouter: ExternalBrowserRouter,
        httpClient: OkHttpClient,
        realmManager: RealmManager,
        tokensService: TokensService,
        alphaWalletNotificationService: AlphaWalletNotificationService,
        gasService: GasService,
    ) : BaseViewModel() {
        // LiveData 数据状态管理
        /** 交易数据的LiveData，用于观察交易列表变化 */
        private val transactions: MutableLiveData<Array<Transaction>> =
            MutableLiveData<Array<Transaction>>()

        /** 钱包备份提示消息的LiveData */
        private val backUpMessage: MutableLiveData<String> = MutableLiveData<String>()

        /** 钱包名称的LiveData，支持ENS域名显示 */
        private val walletName: MutableLiveData<String?> = MutableLiveData<String?>()

        /** 默认钱包的LiveData，存储当前选中的钱包 */
        private val defaultWallet: MutableLiveData<Wallet> = MutableLiveData<Wallet>()

        /** 启动页面状态的LiveData，用于控制是否显示启动页 */
        private val splashActivity: MutableLiveData<Boolean> = MutableLiveData<Boolean>()

        /** 应用更新可用状态的LiveData */
        private val updateAvailable: MutableLiveData<String> = MutableLiveData<String>()

        // 依赖注入的服务和仓库

        /** 用户偏好设置仓库 */
        private val preferenceRepository: PreferenceRepositoryType = preferenceRepository

        /** 代币导入路由器 */
        private val importTokenRouter: ImportTokenRouter

        /** 本地化设置仓库 */
        private val localeRepository: LocaleRepositoryType

        /** 资产定义服务，处理TokenScript */
        private val assetDefinitionService: AssetDefinitionService

        /** 通用钱包交互服务 */
        private val genericWalletInteract: GenericWalletInteract

        /** 钱包获取交互服务 */
        private val fetchWalletsInteract: FetchWalletsInteract

        /** 货币设置仓库 */
        private val currencyRepository: CurrencyRepositoryType

        /** 以太坊网络仓库 */
        private val ethereumNetworkRepository: EthereumNetworkRepositoryType

        /** 交易服务，管理交易状态和更新 */
        private val transactionsService: TransactionsService

        /** 我的地址页面路由器 */
        private val myAddressRouter: MyAddressRouter

        /** 外部浏览器路由器 */
        private val externalBrowserRouter: ExternalBrowserRouter

        /** HTTP客户端，用于网络请求 */
        private val httpClient: OkHttpClient

        /** Realm数据库管理器 */
        private val realmManager: RealmManager

        /** 代币服务 */
        private val tokensService: TokensService

        /** Gas费用服务 */
        private val gasService: GasService

        /** AlphaWallet通知服务 */
        private val alphaWalletNotificationService: AlphaWalletNotificationService

        // 临时状态变量

        /** 加密功能工具类，用于MagicLink解析 */
        private var cryptoFunctions: CryptoFunctions? = null

        /** MagicLink解析器 */
        private var parser: ParseMagicLink? = null

        /** 底部弹窗对话框 */
        private var dialog: BottomSheetDialog? = null

        /** 二维码读取时间戳，用于防止重复扫描 */
        private var qrReadTime = Long.MAX_VALUE

        /**
         * 初始化块
         * 设置所有依赖注入的服务和仓库，并进行必要的初始化操作
         */
        init {
            this.importTokenRouter = importTokenRouter
            this.localeRepository = localeRepository
            this.assetDefinitionService = assetDefinitionService
            this.genericWalletInteract = genericWalletInteract
            this.fetchWalletsInteract = fetchWalletsInteract
            this.currencyRepository = currencyRepository
            this.ethereumNetworkRepository = ethereumNetworkRepository
            this.myAddressRouter = myAddressRouter
            this.transactionsService = transactionsService
            this.externalBrowserRouter = externalBrowserRouter
            this.httpClient = httpClient
            this.realmManager = realmManager
            this.alphaWalletNotificationService = alphaWalletNotificationService
            // 设置分析服务
            setAnalyticsService(analyticsService)
            // 增加应用启动次数计数
            this.preferenceRepository.incrementLaunchCount()
            this.tokensService = tokensService
            this.gasService = gasService
        }

        /**
         * ViewModel清理方法
         * 当ViewModel被销毁时调用，协程会自动取消
         */
        override fun onCleared() {
            super.onCleared()
            // Coroutines are automatically cancelled when ViewModel is cleared
        }

        /**
         * 获取交易数据的LiveData
         * @return 交易数组的LiveData，用于观察交易列表变化
         */
        fun transactions(): LiveData<Array<Transaction>> = transactions

        /**
         * 获取钱包备份消息的LiveData
         * @return 备份消息的LiveData，用于显示备份提醒
         */
        fun backUpMessage(): LiveData<String> = backUpMessage

        /**
         * 获取启动页重置状态的LiveData
         * @return 启动页状态的LiveData，用于控制是否显示启动页
         */
        fun splashReset(): LiveData<Boolean> = splashActivity

        /**
         * 获取应用更新可用状态的LiveData
         * @return 更新版本信息的LiveData
         */
        fun updateAvailable(): LiveData<String> = updateAvailable

        /**
         * 获取默认钱包的LiveData
         * @return 默认钱包的LiveData，用于观察当前选中的钱包
         */
        fun defaultWallet(): LiveData<Wallet> = defaultWallet

        /**
         * 获取Gas服务实例
         * @return GasService实例，用于Gas费用计算和管理
         */
        fun getGasService(): GasService = gasService

        /**
         * 准备方法 - 初始化ViewModel的核心数据
         * 异步获取默认钱包并设置相关状态
         */
        fun prepare() {
            progress.postValue(false)
            launchSafely(
                onError = { throwable: Throwable? -> this.walletError(throwable) },
            ) {
                val wallet = genericWalletInteract.find()
                withMain {
                    this.onDefaultWallet(wallet)
                }
            }
        }

        /**
         * 清理方法 - 当前为空实现
         * 可用于清理临时数据或重置状态
         */
        fun onClean() {
        }

        /**
         * 设置默认钱包
         * @param wallet 要设置为默认的钱包对象
         */
        private fun onDefaultWallet(wallet: Wallet) {
            preferenceRepository.isWatchOnly = wallet.watchOnly()
            defaultWallet.setValue(wallet)
        }

        /**
         * 显示导入链接功能
         * 检查导入数据是否与当前钱包不同，如果不同则进行导入
         * @param activity 当前活动上下文
         * @param importData 要导入的链接数据
         */
        fun showImportLink(
            activity: Activity,
            importData: String?,
        ) {
            launchSafely(
                onError = { throwable: Throwable? -> this.walletError(throwable) },
            ) {
                val wallet = genericWalletInteract.find()
                if (checkWalletNotEqual(wallet, importData)) {
                    withMain {
                        importLink(activity, importData)
                    }
                }
            }
        }

        /**
         * 检查钱包是否与导入数据不同
         * 解析MagicLink数据，比较钱包地址是否一致
         * @param wallet 当前钱包
         * @param importData 导入数据
         * @return true如果钱包不同，false如果相同或解析失败
         */
        private fun checkWalletNotEqual(
            wallet: Wallet,
            importData: String?,
        ): Boolean {
            var filterPass = false

            try {
                if (cryptoFunctions == null) {
                    cryptoFunctions = CryptoFunctions()
                }
                if (parser == null) {
                    parser = ParseMagicLink(cryptoFunctions, EthereumNetworkRepository.extraChainsCompat())
                }

                val data: MagicLinkData = parser!!.parseUniversalLink(importData)
                val linkAddress: String = parser!!.getOwnerKey(data)

                if (Utils.isAddressValid(data.contractAddress)) {
                    filterPass = wallet.address != linkAddress
                }
            } catch (e: Exception) {
                Timber.e(e)
            }

            return filterPass
        }

        /**
         * 执行导入链接操作
         * 使用导入代币路由器打开导入界面
         * @param activity 当前活动上下文
         * @param importData 导入数据
         */
        private fun importLink(
            activity: Activity,
            importData: String?,
        ) {
            importTokenRouter.open(activity, importData)
        }

        /**
         * 重启主页活动
         * 清除任务栈并重新启动HomeActivity
         * @param context 上下文
         */
        fun restartHomeActivity(context: Context) {
            // restart activity
            val intent: Intent = Intent(context, HomeActivity::class.java)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }

        /**
         * 获取钱包名称
         * 异步获取当前钱包并更新钱包标题显示
         * @param context 上下文，用于ENS解析
         */
        fun getWalletName(context: Context) {
            launchSafely(
                onError = { throwable: Throwable? -> this.walletError(throwable) },
            ) {
                val walletAddress = preferenceRepository.currentWalletAddress
                if (!walletAddress.isNullOrEmpty()) {
                    val wallet = fetchWalletsInteract.getWallet(walletAddress)
                    updateWalletTitle(context, wallet)
                } else {
                    // 如果没有当前钱包地址，使用默认钱包
                    val wallet = genericWalletInteract.find()
                    updateWalletTitle(context, wallet)
                }
            }
        }

        /**
         * 钱包错误处理
         * 当没有钱包或发生错误时，显示启动页
         * @param throwable 异常信息
         */
        private fun walletError(throwable: Throwable?) {
            // no wallets
            splashActivity.postValue(true)
        }

        /**
         * 更新钱包标题显示
         * 根据钱包名称、ENS名称的优先级更新UI显示，如果都没有则进行ENS反向解析
         * @param context 上下文，用于ENS解析
         * @param wallet 要更新标题的钱包对象
         */
        private fun updateWalletTitle(
            context: Context,
            wallet: Wallet,
        ) {
            transactionsService.changeWallet(wallet)
            val usingDefaultName = Utils.isDefaultName(wallet.name, context)
            if (!TextUtils.isEmpty(wallet.name) && !usingDefaultName) {
                walletName.postValue(wallet.name)
            } else if (!TextUtils.isEmpty(wallet.ENSname)) {
                walletName.postValue(wallet.ENSname)
            } else {
                walletName.postValue("")
                // check for ENS name
                // 使用launchSafely优化ENS反向解析流程，提供统一的错误处理
                launchSafely(
                    onError = { throwable -> onENSError(throwable) },
                ) {
                    // 1. 创建AWEnsResolver实例，用于ENS反向解析
                    val ensResolver =
                        AWEnsResolver(
                            TokenRepository.getWeb3jService(EthereumNetworkBase.MAINNET_ID),
                            context,
                        )
                    // 2. 进行ENS反向解析，获取ENS名称（挂起函数）
                    val ensName =
                        withContext(Dispatchers.IO) {
                            wallet.address?.let {
                                ensResolver.reverseResolveEnsSuspend(it)
                            }
                        }
                    // 3. 更新wallet对象的ENSname属性
                    wallet.ENSname = ensName
                    // 4. 将ENS名称存储到本地数据库
                    val updatedWallet =
                        withContext(Dispatchers.IO) {
                            fetchWalletsInteract.updateENSSuspend(wallet)
                        }
                    // 5. 在主线程更新LiveData，优先显示ENS名称，否则显示格式化地址
                    val name =
                        if (!TextUtils.isEmpty(updatedWallet.ENSname)) {
                            updatedWallet.ENSname
                        } else {
                            Utils.formatAddress(wallet.address)
                        }
                    walletName.postValue(name)
                }
            }
        }

        /**
         * 获取钱包名称的LiveData
         * @return 钱包名称的MutableLiveData，用于观察钱包名称变化
         */
        fun walletName(): MutableLiveData<String?> = walletName

        /**
         * 检查钱包是否已备份
         * 异步检查指定地址的钱包备份状态，并更新备份消息
         * @param walletAddress 要检查的钱包地址
         */
        fun checkIsBackedUp(walletAddress: String) {
            launchSafely(
                onError = { throwable ->
                    Timber.e(throwable, "Failed to check wallet backup status")
                    backUpMessage.postValue("")
                },
            ) {
                val value =
                    withContext(Dispatchers.IO) {
                        genericWalletInteract.getWalletNeedsBackup(walletAddress)
                    }
                backUpMessage.postValue(value)
            }
        }

        /**
         * 查找钱包地址对话框显示状态
         * 用于控制是否已显示过查找钱包地址的对话框
         */
        var isFindWalletAddressDialogShown: Boolean
            get() = preferenceRepository.isFindWalletAddressDialogShown
            set(isShown) {
                preferenceRepository.isFindWalletAddressDialogShown = isShown
            }

        /**
         * ENS解析错误处理
         * 当ENS反向解析失败时记录错误日志
         * @param throwable 异常信息
         */
        private fun onENSError(throwable: Throwable) {
            Timber.e(throwable)
        }

        /**
         * 设置错误回调
         * 为资产定义服务设置错误消息回调处理器
         * @param callback Fragment消息传递器，用于处理错误消息
         */
        fun setErrorCallback(callback: FragmentMessenger) {
            assetDefinitionService.setErrorCallback(callback)
        }

        /**
         * 处理QR码扫描结果
         * 解析QR码内容并根据类型执行相应操作（地址、支付、WalletConnect等）
         * @param activity 主页活动上下文
         * @param qrCode 扫描到的QR码字符串
         */
        fun handleQRCode(
            activity: HomeActivityKt,
            qrCode: String?,
        ) {
            var qrCode = qrCode
            try {
                val props: AnalyticsProperties = AnalyticsProperties()
                val parser: QRParser =
                    QRParser.getInstance(
                        com.alphawallet.app.repository.EthereumNetworkBase
                            .extraChains(),
                    )
                val qrResult: QRResult = parser.parse(qrCode)
                when (qrResult.type) {
                    EIP681Type.ATTESTATION, EIP681Type.EAS_ATTESTATION ->
                        handleImportAttestation(
                            activity,
                            qrResult,
                        )

                    EIP681Type.ADDRESS -> {
                        props.put(QrScanResultType.KEY, QrScanResultType.ADDRESS.getValue())
                        track(Analytics.Action.SCAN_QR_CODE_SUCCESS, props)

                        // showSend(activity, qrResult); //For now, direct an ETH address to send screen
                        // TODO: Issue #1504: bottom-screen popup to choose between: Add to Address book, Sent to Address, or Watch Wallet
                        showActionSheet(activity, qrResult)
                    }

                    EIP681Type.PAYMENT, EIP681Type.TRANSFER -> {
                        props.put(QrScanResultType.KEY, QrScanResultType.ADDRESS_OR_EIP_681.getValue())
                        track(Analytics.Action.SCAN_QR_CODE_SUCCESS, props)

                        showSend(activity, qrResult)
                    }

                    EIP681Type.FUNCTION_CALL -> {
                        props.put(QrScanResultType.KEY, QrScanResultType.ADDRESS_OR_EIP_681.getValue())
                        track(Analytics.Action.SCAN_QR_CODE_SUCCESS, props)
                    }

                    EIP681Type.URL -> {
                        props.put(QrScanResultType.KEY, QrScanResultType.URL.getValue())
                        track(Analytics.Action.SCAN_QR_CODE_SUCCESS, props)
                        if (qrCode != null) {
                            activity.onBrowserWithURL(qrCode)
                        }
                    }

                    EIP681Type.MAGIC_LINK -> showImportLink(activity, qrCode)
                    EIP681Type.OTHER -> qrCode = null
                    EIP681Type.OTHER_PROTOCOL -> {}
                    EIP681Type.WALLET_CONNECT -> startWalletConnect(activity, qrCode)
                }
            } catch (e: Exception) {
                Timber.e(e)
                qrCode = null
            }

            if (qrCode == null) {
                Toast.makeText(activity, R.string.toast_invalid_code, Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * 检查QR码是否需要处理
         * 防止重复扫描同一个QR码（回声检测）
         * @param qrCode QR码字符串
         * @return true如果需要处理，false如果是重复扫描
         */
        fun requiresProcessing(qrCode: String?): Boolean {
            // prevent echoes
            val currentTime = System.currentTimeMillis()
            if (qrCode == null || ((qrReadTime + ECHO_MAX_MILLIS) > currentTime)) {
                Timber.d("QR Read: %s", (qrReadTime - currentTime))
                return false
            }

            qrReadTime = currentTime
            return true
        }

        /**
         * 启动WalletConnect连接
         * 打开WalletConnectV2Activity处理连接请求
         * @param activity 当前活动上下文
         * @param qrCode WalletConnect的QR码字符串
         */
        private fun startWalletConnect(
            activity: Activity,
            qrCode: String?,
        ) {
            val intent: Intent = Intent(activity, WalletConnectV2Activity::class.java)
            intent.putExtra("url", qrCode)

            activity.startActivity(intent)
        }

        /**
         * 显示QR码操作选择表
         * 根据QR码解析结果显示可执行的操作选项
         * @param activity 当前活动上下文
         * @param qrResult QR码解析结果
         */
        private fun showActionSheet(
            activity: Activity,
            qrResult: QRResult,
        ) {
            val listener =
                View.OnClickListener { v: View ->
                    if (v.id == R.id.send_to_this_address_action) {
                        showSend(activity, qrResult)
                    } else if (v.id == R.id.add_custom_token_action) {
                        val intent: Intent = Intent(activity, AddTokenActivity::class.java)
                        intent.putExtra(C.EXTRA_QR_CODE, qrResult.getAddress())
                        activity.startActivityForResult(intent, C.ADDED_TOKEN_RETURN)
                    } else if (v.id == R.id.watch_account_action) {
                        val intent: Intent = Intent(activity, ImportWalletActivity::class.java)
                        intent.putExtra(C.EXTRA_QR_CODE, qrResult.getAddress())
                        intent.putExtra(C.EXTRA_STATE, "watch")
                        activity.startActivity(intent)
                    } else if (v.id == R.id.open_in_etherscan_action) {
                        val info: NetworkInfo =
                            ethereumNetworkRepository.getNetworkByChain(qrResult.chainId)
                                ?: return@OnClickListener

                        val blockChainInfoUrl = info.getEtherscanAddressUri(qrResult.getAddress())

                        if (blockChainInfoUrl !== Uri.EMPTY) {
                            externalBrowserRouter.open(activity, blockChainInfoUrl)
                        }
                    } else if (v.id == R.id.close_action) {
                        // NOP
                    }
                    dialog!!.dismiss()
                }

            val contentView: QRCodeActionsView = QRCodeActionsView(activity)

            contentView.setOnSendToAddressClickListener(listener)
            contentView.setOnAddCustonTokenClickListener(listener)

            contentView.setOnWatchWalletClickListener(listener)

            contentView.setOnOpenInEtherscanClickListener(listener)

            contentView.setOnCloseActionListener(listener)

            dialog = BottomSheetDialog(activity)
            dialog!!.setContentView(contentView)
            dialog!!.setCancelable(true)
            dialog!!.setCanceledOnTouchOutside(true)
            val behavior: BottomSheetBehavior<View> =
                BottomSheetBehavior.from<View>(contentView.getParent() as View)
            dialog!!.setOnShowListener(
                DialogInterface.OnShowListener { dialog: DialogInterface? ->
                    behavior.setPeekHeight(
                        contentView.getHeight(),
                    )
                },
            )
            dialog!!.show()
        }

        /**
         * 显示发送界面（私有方法）
         * 根据QR码解析结果打开发送交易界面
         * @param ctx 活动上下文
         * @param result QR码解析结果，包含交易详情
         */
        private fun showSend(
            ctx: Activity,
            result: QRResult,
        ) {
            val intent: Intent = Intent(ctx, SendActivity::class.java)
            val sendingTokens = (result.getFunction() != null && result.getFunction().isNotEmpty())
            defaultWallet.value?.let { wallet ->

                intent.putExtra(C.EXTRA_SENDING_TOKENS, sendingTokens)
                intent.putExtra(C.EXTRA_CONTRACT_ADDRESS, wallet.address)
                intent.putExtra(C.EXTRA_NETWORKID, result.chainId)
                intent.putExtra(
                    C.EXTRA_SYMBOL,
                    ethereumNetworkRepository.getNetworkByChain(result.chainId).symbol,
                )
                intent.putExtra(C.EXTRA_DECIMALS, DEFAULT_DECIMALS)
                intent.putExtra(C.Key.WALLET, wallet)
                intent.putExtra(C.EXTRA_AMOUNT, result)
                intent.setFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                ctx.startActivity(intent)
            }
        }

        /**
         * 显示我的地址界面
         * 打开地址显示页面，展示当前钱包地址和二维码
         * @param activity 当前活动上下文
         */
        fun showMyAddress(activity: Activity?) {
            activity?.let {
                myAddressRouter.open(it, defaultWallet.getValue())
            }
        }

        /**
         * 设备身份识别
         * 为设备创建唯一ID并存储在偏好设置中
         * 如果用户重新安装应用或清除存储，此ID会发生变化
         */
        fun identify() {
            var uuid: String? = preferenceRepository.uniqueId

            if (uuid.isNullOrEmpty()) {
                uuid = UUID.randomUUID().toString()
            }

            preferenceRepository.uniqueId = uuid

            super.identify(uuid)
        }

        /**
         * 检查交易引擎状态
         * 恢复交易服务的焦点状态，开始监控交易
         */
        fun checkTransactionEngine() {
            transactionsService.resumeFocus()
        }

        /**
         * 停止交易更新
         * 停止交易服务，不再监控新的交易
         */
        fun stopTransactionUpdate() {
            transactionsService.stopService()
        }

        /**
         * 应用失去焦点
         * 当应用进入后台时调用，暂停交易监控
         */
        fun outOfFocus() {
            transactionsService.lostFocus()
        }

        /**
         * 检查是否选择全屏模式
         * @return true如果启用全屏，false如果未启用
         */
        fun fullScreenSelected(): Boolean = preferenceRepository.fullScreenState

        /**
         * 尝试显示应用评分对话框
         * 根据使用情况和偏好设置决定是否显示评分提示
         * 仅在从PlayStore安装时显示（在showRateTheApp方法内检查）
         * @param context 当前活动上下文
         */
        fun tryToShowRateAppDialog(context: Activity?) {
            // only if installed from PlayStore (checked within the showRateTheApp method)
            RateApp.showRateTheApp(context, preferenceRepository, false)
        }

        /**
         * 获取更新警告次数
         * @return 已显示的更新警告次数
         */
        val updateWarnings: Int
            get() = preferenceRepository.updateWarningCount

        /**
         * 设置更新警告次数
         * @param warns 警告次数
         */
        fun setUpdateWarningCount(warns: Int) {
            preferenceRepository.updateWarningCount = warns
        }

        /**
         * 设置应用安装时间
         * @param time 安装时间戳（秒），会转换为毫秒存储
         */
        fun setInstallTime(time: Int) {
            preferenceRepository.installTime = time.toLong()
        }

        /**
         * 重启代币服务
         * 重新启动交易服务以刷新代币数据
         */
        fun restartTokensService() {
            transactionsService.restartService()
        }

        /**
         * 存储当前Fragment ID
         * 记录用户最后访问的页面，用于应用重启时恢复
         * @param ordinal Fragment的序号
         */
        fun storeCurrentFragmentId(ordinal: Int) {
            preferenceRepository.storeLastFragmentPage(ordinal)
        }

        /**
         * 获取最后访问的Fragment ID
         * @return 最后访问的Fragment序号
         */
        val lastFragmentId: Int
            get() = preferenceRepository.lastFragmentPage

        /**
         * 尝试显示邮件提示对话框
         * 在用户第4次启动应用时显示邮件收集提示，用于后续通知和更新
         * @param context 上下文
         * @param successOverlay 成功提示覆盖层视图
         * @param handler 处理器，用于延迟执行
         * @param onSuccessRunnable 成功后执行的任务
         */
        fun tryToShowEmailPrompt(
            context: Context,
            successOverlay: View?,
            handler: Handler?,
            onSuccessRunnable: Runnable?,
        ) {
            if (preferenceRepository.launchCount == 4) {
                val emailPromptView: EmailPromptView =
                    EmailPromptView(context, successOverlay, handler, onSuccessRunnable)
                val emailPromptDialog: BottomSheetDialog = BottomSheetDialog(context)
                emailPromptDialog.setContentView(emailPromptView)
                emailPromptDialog.setCancelable(true)
                emailPromptDialog.setCanceledOnTouchOutside(true)
                emailPromptView.setParentDialog(emailPromptDialog)
                val behavior: BottomSheetBehavior<View> =
                    BottomSheetBehavior.from<View>(emailPromptView.getParent() as View)
                emailPromptDialog.setOnShowListener(
                    DialogInterface.OnShowListener { dialog: DialogInterface? ->
                        behavior.setPeekHeight(
                            emailPromptView.getHeight(),
                        )
                    },
                )
                emailPromptDialog.show()
            }
        }

        /**
         * 尝试显示新功能对话框
         * 检查应用版本更新，如果有新版本则从GitHub获取发布信息并显示新功能介绍
         * @param context 上下文
         */
        fun tryToShowWhatsNewDialog(context: Context) {
            val packageInfo: PackageInfo
            try {
                packageInfo =
                    context.packageManager
                        .getPackageInfo(context.packageName, 0)

                val versionCode = packageInfo.versionCode
                if (preferenceRepository.getLastVersionCode(versionCode) < versionCode) {
                    launchSafely(
                        onError = { t: Throwable -> Timber.w(t) },
                    ) {
                        val releases =
                            withContext(Dispatchers.IO) {
                                getGitHubReleases(request)
                            }

                        if (releases.isNotEmpty()) {
                            doShowWhatsNewDialog(context, releases)
                            preferenceRepository.setLastVersionCode(versionCode)
                        }
                    }
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Timber.e(e)
            }
        }

        /**
         * 显示新功能对话框（私有方法）
         * 创建并显示包含GitHub发布信息的底部弹窗对话框
         * @param context 上下文
         * @param releases GitHub发布信息列表
         */
        private fun doShowWhatsNewDialog(
            context: Context,
            releases: List<GitHubRelease>,
        ) {
            val dialog: BottomSheetDialog = BottomSheetDialog(context)
            val view: WhatsNewView =
                WhatsNewView(
                    context,
                    releases,
                    View.OnClickListener { v: View? -> dialog.dismiss() },
                    true,
                )
            dialog.setContentView(view)
            dialog.setCancelable(true)
            dialog.setCanceledOnTouchOutside(true)
            val behavior: BottomSheetBehavior<View> =
                BottomSheetBehavior.from<View>(view.getParent() as View)
            dialog.setOnShowListener(DialogInterface.OnShowListener { d: DialogInterface? -> behavior.setPeekHeight(view.getHeight()) })
            dialog.show()
        }

        /**
         * 获取GitHub API请求对象
         * 构建用于获取AlphaWallet Android版本发布信息的HTTP请求
         * @return 配置好的Request对象
         */
        private val request: Request
            get() =
                Request
                    .Builder()
                    .header("Accept", "application/vnd.github.v3+json")
                    .url("https://api.github.com/repos/alphawallet/alpha-wallet-android/releases")
                    .get()
                    .build()

        /**
         * 解析TokenScript文件
         * 从输入流中解析TokenScript XML文件，创建TokenDefinition对象
         * @param ctx 上下文，用于获取本地化信息
         * @param xmlInputStream TokenScript XML文件的输入流
         * @return 解析后的TokenDefinition对象
         * @throws Exception 解析过程中的异常
         */
        @Throws(Exception::class)
        private fun parseFile(
            ctx: Context,
            xmlInputStream: InputStream?,
        ): TokenDefinition {
            val locale = ctx.resources.configuration.locales[0]
            return TokenDefinition(xmlInputStream, locale, null)
        }

        /**
         * 导入TokenScript文件
         * 从Intent中获取文件URI，解析并导入TokenScript文件到应用中
         * @param ctx HomeActivity上下文
         * @param startIntent 包含文件URI的Intent
         */
        fun importScriptFile(
            ctx: HomeActivityKt,
            startIntent: Intent,
        ) {
            val uri: Uri? = startIntent.data
            val contentResolver: ContentResolver = ctx.getContentResolver()
            try {
                if (uri == null) return
                var iStream: InputStream? = contentResolver.openInputStream(uri)
                val td: TokenDefinition = parseFile(ctx, iStream)
                // tokenscript with no holding token is currently meaningless. Is this always the case?
                if (td.holdingToken == null || td.holdingToken?.length == 0) return

                // determine type of holding token
                var newFileName: String
                val info: ContractInfo? = td.contracts.get(td.holdingToken)
                if (info == null) {
                    ctx.tokenScriptError("Contract info not found for holding token")
                    return
                }
                val preHash: ByteArray? = td.attestationCollectionPreHash
                newFileName =
                    if (preHash != null) {
                        (
                            Numeric.toHexString(Hash.keccak256(preHash)) + "-" +
                                info.addresses.keys
                                    .iterator()
                                    .next()
                        )
                    } else {
                        // calculate using formula: "{address.lowercased}-{chainId}"
                        (
                            info.addresses.values
                                .iterator()
                                .next()
                                .iterator()
                                .next() + "-" +
                                info.addresses.keys
                                    .iterator()
                                    .next()
                        )
                    }

                newFileName = assetDefinitionService.getDebugPath("$newFileName.tsml")

                // 使用use扩展函数确保资源正确关闭
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(newFileName).use { fos ->
                        inputStream.copyTo(fos)
                    }
                }

                // register new file
                assetDefinitionService.notifyNewScriptLoaded(newFileName)

                launchSafely {
                    withContext(Dispatchers.IO) {
                        try {
                            // Convert RxJava Single to suspend function using blockingGet in IO context
//                        assetDefinitionService.resetAttributes(td)
                            assetDefinitionService.resetAttributesAsync(td)
                            assetDefinitionService.updateAttestations(td)
                        } catch (e: Exception) {
                            Timber.e(e, "Error resetting attributes")
                        }
                    }
                }
            } catch (e: Exception) {
                // import error
                e.message?.let { ctx.tokenScriptError(it) }
            }
        }

        /**
         * 设置钱包启动状态
         * 通知TokensService钱包正在启动
         */
        fun setWalletStartup() {
            tokensService.setWalletStartup()
        }

        /**
         * 设置货币和本地化配置
         * 初始化用户的语言偏好和默认货币设置
         * @param context 上下文，用于本地化设置
         */
        fun setCurrencyAndLocale(context: Context?) {
            if (TextUtils.isEmpty(localeRepository.getUserPreferenceLocale())) {
                localeRepository.setLocale(context, localeRepository.getActiveLocale())
            }
            currencyRepository.setDefaultCurrency(preferenceRepository.defaultCurrency)
        }

        /**
         * 检查是否为新钱包
         * @param address 钱包地址
         * @return true如果是新钱包，false如果是已存在的钱包
         */
        fun checkNewWallet(address: String?): Boolean = preferenceRepository.isNewWallet(address)

        /**
         * 设置钱包的新建状态
         * @param address 钱包地址
         * @param isNewWallet 是否为新钱包
         */
        fun setNewWallet(
            address: String?,
            isNewWallet: Boolean,
        ) {
            preferenceRepository.setNewWallet(address, isNewWallet)
        }

        /**
         * 检查GitHub最新发布版本
         * 异步获取GitHub上的最新版本信息，与当前安装版本比较，如果有更新则通知UI
         */
        fun checkLatestGithubRelease() {
            launchSafely(
                onError = { t: Throwable -> Timber.w(t) },
            ) {
                val releases = getGitHubReleases(request)

                if (releases.isNotEmpty()) {
                    val latestRelease: GitHubRelease = releases[0]
                    var latestTag: String = latestRelease.tagName
                    if (latestRelease.tagName[0] == 'v') {
                        latestTag = latestTag.substring(1)
                    }
                    val latest = Version(latestTag)
                    val installed = Version(BuildConfig.VERSION_NAME)

                    if (latest.compareTo(installed) > 0) {
                        updateAvailable.postValue(latest.get())
                    }
                }
            }
        }

        /**
         * 获取GitHub发布信息（私有挂起函数）
         * 执行HTTP请求获取GitHub API的发布信息，解析JSON响应
         * @param request HTTP请求对象
         * @return GitHub发布信息列表，失败时返回空列表
         */
        private suspend fun getGitHubReleases(request: Request): List<GitHubRelease> {
            return withContext(Dispatchers.IO) {
                try {
                    httpClient
                        .newCall(request)
                        .execute()
                        .use { response ->
                            if (response.code / 100 == 2) {
                                return@withContext Gson().fromJson<List<GitHubRelease>>(
                                    response.body?.string(),
                                    object : TypeToken<List<GitHubRelease?>?>() {
                                    }.type,
                                )
                            } else {
                                return@withContext ArrayList<GitHubRelease>()
                            }
                        }
                } catch (e: Exception) {
                    Timber.e(e)
                    return@withContext ArrayList<GitHubRelease>()
                }
            }
        }

        /**
         * 订阅推送通知
         * 为主网订阅AlphaWallet的推送通知服务
         */
        suspend fun subscribeToNotifications() {
            alphaWalletNotificationService.subscribe(EthereumNetworkBase.MAINNET_ID)
        }

        /**
         * 取消订阅推送通知
         * 取消主网的AlphaWallet推送通知订阅
         */
        fun unsubscribeToNotifications() {
            alphaWalletNotificationService.unsubscribeToTopic(EthereumNetworkBase.MAINNET_ID)
        }

        /**
         * 设置通知权限请求状态
         * 标记指定钱包地址已请求过通知权限
         * @param address 钱包地址
         */
        fun setPostNotificationsPermissionRequested(address: String?) {
            preferenceRepository.setPostNotificationsPermissionRequested(address, true)
        }

        /**
         * 检查是否已请求通知权限
         * @param address 钱包地址
         * @return true如果已请求过通知权限，false如果未请求过
         */
        fun isPostNotificationsPermissionRequested(address: String?): Boolean = preferenceRepository.isPostNotificationsPermissionRequested(address)

        /**
         * 检查是否为只读钱包
         * @return true如果当前钱包为只读模式，false如果为完整钱包
         */
        val isWatchOnlyWallet: Boolean
            get() = preferenceRepository.isWatchOnly

        /**
         * 获取当前钱包（私有挂起函数）
         * 优先返回已缓存的默认钱包，否则从数据库查找
         * @return 当前钱包对象
         */
        private suspend fun getCurrentWallet(): Wallet =
            withContext(Dispatchers.IO) {
                if (defaultWallet.value != null) {
                    defaultWallet.value!!
                } else {
                    genericWalletInteract.find()
                }
            }

        /**
         * 处理导入证明（私有方法）
         * 异步获取当前钱包并完成证明导入流程
         * @param activity HomeActivity上下文
         * @param qrResult QR码解析结果，包含证明信息
         */
        private fun handleImportAttestation(
            activity: HomeActivityKt,
            qrResult: QRResult,
        ) {
            launchSafely {
                val wallet = getCurrentWallet()
                completeImport(activity, wallet, qrResult)
            }
        }

        /**
         * 完成证明导入（私有方法）
         * 检查钱包状态并执行证明导入，只读钱包无法导入证明
         * @param activity HomeActivity上下文
         * @param wallet 目标钱包，可能为null
         * @param qrResult QR码解析结果，包含证明信息
         */
        private fun completeImport(
            activity: HomeActivityKt,
            wallet: Wallet?,
            qrResult: QRResult,
        ) {
            if (wallet == null || wallet?.watchOnly() == true) {
                activity.importError(activity.getString(R.string.watch_wallet))
            } else {
                val attnImport: ImportAttestation =
                    ImportAttestation(
                        assetDefinitionService,
                        tokensService,
                        activity,
                        wallet,
                        realmManager,
                        httpClient,
                    )

                // attempt to import the wallet
                attnImport.importAttestation(qrResult)
            }
        }

        /**
         * 处理SmartPass链接
         * 解析SmartPass URL并尝试导入EAS证明
         * @param homeActivity HomeActivity上下文
         * @param smartPassCandidate 候选的SmartPass链接字符串
         * @return true如果成功处理SmartPass，false如果不是有效的SmartPass链接
         */
        fun handleSmartPass(
            homeActivity: HomeActivityKt,
            smartPassCandidate: String,
        ): Boolean {
            var candidate = smartPassCandidate
            if (candidate.startsWith(ImportAttestation.SMART_PASS_URL)) {
                candidate =
                    candidate.substring(ImportAttestation.SMART_PASS_URL.length) // chop off leading URL
                val result: QRResult = QRResult(candidate)
                result.type = EIP681Type.EAS_ATTESTATION
                val taglessAttestation = Utils.parseEASAttestation(candidate)
                result.functionDetail = Utils.toAttestationJson(taglessAttestation)

                if (!TextUtils.isEmpty(result.functionDetail)) {
                    handleImportAttestation(homeActivity, result)
                    return true
                }
            }

            return false
        }

        companion object {
            const val ALPHAWALLET_DIR: String = "AlphaWallet"
            private const val ECHO_MAX_MILLIS: Long = 250 // if second QRCODE read comes before 250 millis, reject
            private const val DEFAULT_DECIMALS = 18 // 默认以太坊代币精度
        }
    }
