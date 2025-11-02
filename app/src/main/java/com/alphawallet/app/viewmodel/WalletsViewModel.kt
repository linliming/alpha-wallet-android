package com.alphawallet.app.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Pair
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.alphawallet.app.C
import com.alphawallet.app.entity.CreateWalletCallbackInterface
import com.alphawallet.app.entity.CryptoFunctions
import com.alphawallet.app.entity.ErrorEnvelope
import com.alphawallet.app.entity.NetworkInfo
import com.alphawallet.app.entity.Operation
import com.alphawallet.app.entity.ServiceSyncCallback
import com.alphawallet.app.entity.SyncCallback
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.entity.tokendata.TokenUpdateType
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.interact.FetchWalletsInteract
import com.alphawallet.app.interact.FindDefaultNetworkInteract
import com.alphawallet.app.interact.GenericWalletInteract
import com.alphawallet.app.interact.ImportWalletInteract
import com.alphawallet.app.interact.SetDefaultWalletInteract
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.EthereumNetworkRepositoryType
import com.alphawallet.app.repository.PreferenceRepositoryType
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.app.repository.TokenRepositoryType
import com.alphawallet.app.router.HomeRouter
import com.alphawallet.app.router.ImportWalletRouter
import com.alphawallet.app.service.AlphaWalletNotificationService
import com.alphawallet.app.service.AnalyticsService
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.service.KeyService
import com.alphawallet.app.service.OpenSeaService
import com.alphawallet.app.service.TickerService
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.util.ens.AWEnsResolver
import com.alphawallet.hardware.SignatureFromKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import timber.log.Timber
import java.security.SignatureException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * WalletsViewModel - 钱包管理视图模型类
 *
 * 这个类是AlphaWallet中管理钱包的核心ViewModel，负责处理所有与钱包相关的业务逻辑。
 * 主要功能包括：
 * 1. 钱包创建、导入和管理
 * 2. 默认钱包设置和切换
 * 3. 钱包余额同步和更新
 * 4. ENS名称解析
 * 5. 硬件钱包支持
 * 6. 钱包数据同步
 * 7. 通知订阅管理
 *
 * @author AlphaWallet Team
 * @since 2024
 */
@HiltViewModel
class WalletsViewModel @Inject constructor(
    private val alphaWalletNotificationService: AlphaWalletNotificationService,
    private val setDefaultWalletInteract: SetDefaultWalletInteract,
    private val fetchWalletsInteract: FetchWalletsInteract,
    private val genericWalletInteract: GenericWalletInteract,
    private val importWalletInteract: ImportWalletInteract,
    private val importWalletRouter: ImportWalletRouter,
    private val homeRouter: HomeRouter,
    private val findDefaultNetworkInteract: FindDefaultNetworkInteract,
    private val keyService: KeyService,
    private val ethereumNetworkRepository: EthereumNetworkRepositoryType,
    private val tokenRepository: TokenRepositoryType,
    private val tickerService: TickerService,
    private val assetService: AssetDefinitionService,
    private val preferenceRepository: PreferenceRepositoryType,
    @ApplicationContext private val context: Context
) : BaseViewModel(), ServiceSyncCallback {

    companion object {
        private const val TAG = "WalletsViewModel"
        
        // 常量定义
        private const val BALANCE_CHECK_INTERVAL_SECONDS = 30L
        private const val INITIAL_DELAY_SECONDS = 1L
        private const val MAX_CONCURRENT_WALLET_UPDATES = 4
        private const val SYNC_COMPLETION_COUNT = 2
        private const val SYNC_UPDATE_COUNT = 1
        
        // 测试字符串用于硬件钱包
        val TEST_STRING = "EncodedUUID to determine Public Key${UUID.randomUUID()}"
    }

    // 协程作业管理
    private val coroutineJobs = mutableListOf<Job>()
    private var balanceUpdateJob: Job? = null
    private var walletBalanceUpdateJob: Job? = null
    private var ensCheckJob: Job? = null
    private var ensWrappingCheckJob: Job? = null

    // LiveData 状态管理
    private val _wallets = MutableLiveData<Array<Wallet>>()
    private val _setupWallet = MutableLiveData<Wallet>()
    private val _defaultWallet = MutableLiveData<Pair<Wallet, Boolean>>()
    private val _changeDefaultWallet = MutableLiveData<Wallet>()
    private val _newWalletCreated = MutableLiveData<Wallet>()
    private val _createWalletError = MutableLiveData<ErrorEnvelope>()
    private val _noWalletsError = MutableLiveData<Boolean>()
    private val _baseTokens = MutableLiveData<Map<String, Array<Token>>>()
    private val _getPublicKey = MutableLiveData<String>()

    // 状态Flow (Kotlin协程推荐)
    private val _uiState = MutableStateFlow<WalletsUiState>(WalletsUiState.Idle)
    val uiState: StateFlow<WalletsUiState> = _uiState.asStateFlow()

    // 钱包状态管理
    private var currentNetwork: NetworkInfo? = null
    private val walletBalances = mutableMapOf<String, Wallet>()
    private val walletServices = ConcurrentHashMap<String, TokensService>()
    private val walletUpdate = ConcurrentHashMap<String, Wallet>()
    private val currentWalletUpdates = ConcurrentHashMap<String, Job>()
    private var syncCallback: SyncCallback? = null

    // 初始化TokensService
    private val tokensService = TokensService(
        ethereumNetworkRepository, 
        tokenRepository, 
        tickerService, 
        OpenSeaService(), 
        AnalyticsService(context, preferenceRepository), 
        OkHttpClient()
    )

    // 初始化ENS解析器
    private val ensResolver = AWEnsResolver(
        TokenRepository.getWeb3jService(com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID), 
        context
    )

    // 公开的LiveData接口
    val wallets: LiveData<Array<Wallet>> = _wallets
    val setupWallet: LiveData<Wallet> = _setupWallet
    val newWalletCreated: LiveData<Wallet> = _newWalletCreated
    val createWalletError: LiveData<ErrorEnvelope> = _createWalletError
    val noWalletsError: LiveData<Boolean> = _noWalletsError
    val baseTokens: LiveData<Map<String, Array<Token>>> = _baseTokens
    val changeDefaultWallet: LiveData<Wallet> = _changeDefaultWallet
    val getPublicKey: LiveData<String> = _getPublicKey

    /**
     * 设置默认钱包
     */
    fun setDefaultWallet(wallet: Wallet) {
        preferenceRepository.setNewWallet(wallet.address, false)
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            withIO {
                setDefaultWalletInteract.set(wallet)
            }
            onDefaultWallet(wallet)
        }
    }

    /**
     * 更改默认钱包
     */
    fun changeDefaultWallet(wallet: Wallet) {
        preferenceRepository.isWatchOnly = wallet.watchOnly()
        preferenceRepository.setNewWallet(wallet.address, false)
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            withIO {
                setDefaultWalletInteract.set(wallet)
            }
            _changeDefaultWallet.postValue(wallet)
        }
    }

    /**
     * 订阅通知
     */
    fun subscribeToNotifications() {
        launchSafely(
            onError = { throwable -> Timber.e(throwable) }
        ) {
            val result = withIO {
                alphaWalletNotificationService.subscribe(com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID)
            }
            Timber.d("subscribe result => %s", result)
        }
    }

    /**
     * 准备钱包更新
     */
    fun onPrepare(chainId: Long, cb: SyncCallback) {
        syncCallback = cb
        currentNetwork = findDefaultNetworkInteract.getNetworkInfo(chainId)
        startWalletUpdate()
    }

    /**
     * 开始钱包更新
     */
    private fun startWalletUpdate() {
        walletBalances.clear()
        progress.postValue(true)

        launchSafely(
            onError = { throwable -> _noWalletsError.postValue(true) }
        ) {
            val wallet = withIO {
                genericWalletInteract.find()
            }
            onDefaultWallet(wallet)
        }
    }

    /**
     * 处理默认钱包
     */
    private fun onDefaultWallet(wallet: Wallet) {
        _setupWallet.postValue(wallet)
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            val wallets = withIO {
                fetchWalletsInteract.fetch()
            }
            onWallets(wallets)
        }
    }

    /**
     * 处理钱包列表
     */
    private fun onWallets(items: Array<Wallet>) {
        progress.postValue(false)

        // 更新钱包余额和符号
        for (w in items) {
            w.balanceSymbol = currentNetwork?.symbol ?: ""
            val mapW = walletBalances[w.address?.lowercase()]
            if (mapW != null) {
                w.balance = mapW.balance
            }
        }
        _wallets.postValue(items)

        // 同步非观察钱包
        for (w in items) {
            if (w.type == WalletType.WATCH) continue
            syncFromDBOnly(w, true)
        }

        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            val wallets = withIO {
                fetchWalletsInteract.fetch()
            }
            startBalanceUpdateTimer(wallets)
        }
    }

    /**
     * 开始钱包同步进程
     */
    private fun startWalletSyncProcess(w: Wallet): Job {
        walletUpdate.remove(w.address)
        return viewModelScope.launch {
            try {
                val wallet = withContext(Dispatchers.IO) {
                    startWalletSync(w)
                }
                sendUnsyncedValue(wallet)
            } catch (e: Exception) {
                // 忽略同步错误
            }
        }
    }

    /**
     * 开始完整钱包同步
     */
    private fun startFullWalletSync(items: Array<Wallet>) {
        walletUpdate.clear()
        for (w in items) {
            if (w.type != WalletType.WATCH) {
                w.address?.let { address ->
                    walletUpdate[address] = w
                    syncCallback?.syncStarted(address, null)
                }
            }
        }

        var counter = 0
        for (w in items) {
            if (w.type == WalletType.WATCH) continue
            if (counter++ >= MAX_CONCURRENT_WALLET_UPDATES) break
            w.address?.let { address ->
                currentWalletUpdates[address] = startWalletSyncProcess(w)
            }
        }
    }

    /**
     * 仅从数据库同步
     */
    private fun syncFromDBOnly(wallet: Wallet, complete: Boolean) {
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            val value = withIO {
                tokenRepository.getTotalValue(
                    wallet.address?.lowercase(),
                    EthereumNetworkBase.getAllMainNetworks()
                )
            }
            
            if (complete) {
                syncCallback?.syncCompleted(wallet.address?.lowercase(), value)
            } else {
                syncCallback?.syncStarted(wallet.address?.lowercase(), value)
            }
        }
    }

    /**
     * 发送未同步的值
     */
    private fun sendUnsyncedValue(wallet: Wallet) {
        val service = walletServices[wallet.address?.lowercase()]
        service?.let { svs ->
            launchSafely(
                onError = { throwable -> handleError(throwable) }
            ) {
                val value = withIO {
                    svs.getFiatValuePair()
                }
                syncCallback?.syncStarted(svs.getCurrentAddress()?.lowercase(), value)
            }
        }
    }

    /**
     * 开始钱包同步
     */
    private suspend fun startWalletSync(wallet: Wallet): Wallet {
        return withContext(Dispatchers.IO) {
            val svs = TokensService(
            ethereumNetworkRepository, 
            tokenRepository, 
            tickerService, 
            OpenSeaService(), // openseaService
            AnalyticsService(context, preferenceRepository), // analyticsService  
            OkHttpClient()  // httpClient
        )
        wallet.address?.let { address ->
            svs.setCurrentAddress(address.lowercase())
        }
            svs.startUpdateCycle()
            svs.setCompletionCallback(this@WalletsViewModel, SYNC_COMPLETION_COUNT)
            wallet.address?.let { address ->
                walletServices[address.lowercase()] = svs
            }
            wallet
        }
    }

    /**
     * 同步完成回调
     */
    override fun syncComplete(svs: TokensService, syncCount: Int) {
        if (syncCount == SYNC_COMPLETION_COUNT) {
            svs.setCompletionCallback(this, SYNC_UPDATE_COUNT)
        } else {
            svs.stopUpdateCycle()
            walletServices.remove(svs.getCurrentAddress()?.lowercase())
            currentWalletUpdates.remove(svs.getCurrentAddress()?.lowercase())
            updateNextWallet()
        }

        syncCallback?.let { callback ->
            launchSafely(
                onError = { throwable -> handleError(throwable) }
            ) {
                val value = withIO {
                    svs.getFiatValuePair()
                }
                
                if (syncCount == SYNC_UPDATE_COUNT) {
                    callback.syncCompleted(svs.getCurrentAddress()?.lowercase(), value)
                } else {
                    callback.syncUpdate(svs.getCurrentAddress()?.lowercase(), value)
                }
            }
        }
    }

    /**
     * 更新下一个钱包
     */
    private fun updateNextWallet() {
        val nextWalletToCheck = walletUpdate.keys.firstOrNull()
        
        nextWalletToCheck?.let { address ->
            val w = walletUpdate[address]
            w?.let { wallet ->
                currentWalletUpdates[address] = startWalletSyncProcess(wallet)
            }
        }
    }

    /**
     * 下拉刷新钱包
     */
    fun swipeRefreshWallets() {
        // 检查ENS名称更新
        ensWrappingCheckJob = launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            val wallets = withIO {
                fetchWalletsInteract.fetch()
            }
            
            wallets.forEach { wallet ->
                ensCheckJob = launchSafely(
                    onError = { throwable -> 
                        wallet.ENSname = wallet.ENSname ?: ""
                        handleError(throwable)
                    }
                ) {
                    val ensName = withIO {
                        ensResolver.reverseResolveEns(wallet.address ?: "")
                    }
                    wallet.ENSname = ensName?.takeIf { it.isNotEmpty() } ?: (wallet.ENSname ?: "")
                    
                    withIO {
                        fetchWalletsInteract.updateWalletData(wallet) { }
                    }
                }
            }
        }

        // 从数据库加载当前钱包
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            val wallets = withIO {
                fetchWalletsInteract.fetch()
            }
            startFullWalletSync(wallets)
        }
    }

    /**
     * 获取钱包列表
     */
    fun fetchWallets() {
        progress.postValue(true)
        startWalletUpdate()
    }

    /**
     * 创建新钱包
     */
    fun newWallet(ctx: Activity, createCallback: CreateWalletCallbackInterface) {
        launchIO(
            onError = { throwable -> onCreateWalletError(throwable) }
        ) {
            keyService.createNewHDKey(ctx, createCallback)
        }
    }

    /**
     * 设置新钱包
     */
    fun setNewWallet(wallet: Wallet) {
        preferenceRepository.setNewWallet(wallet.address, true)
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            withIO {
                setDefaultWalletInteract.set(wallet)
            }
            _newWalletCreated.postValue(wallet)
        }
    }

    /**
     * 开始余额更新定时器
     */
    private fun startBalanceUpdateTimer(wallets: Array<Wallet>) {
        balanceUpdateJob?.cancel()
        balanceUpdateJob = viewModelScope.launch {
            delay(INITIAL_DELAY_SECONDS * 1000) // 初始延迟1秒让视图稳定
            while (isActive) {
                getWalletsBalance(wallets)
                delay(BALANCE_CHECK_INTERVAL_SECONDS * 1000)
            }
        }
    }

    /**
     * 获取钱包余额
     */
    private fun getWalletsBalance(wallets: Array<Wallet>) {
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            wallets.forEach { wallet ->
                walletBalanceUpdateJob = launchSafely(
                    onError = { throwable -> 
                        // 忽略余额更新错误
                    }
                ) {
                    val newBalance = withIO {
                        tokensService.getChainBalance(
                            wallet.address?.lowercase() ?: "",
                            currentNetwork?.chainId ?: 0L
                        )
                    }
                    withIO {
                        genericWalletInteract.updateBalanceIfRequired(wallet, newBalance)
                    }
                }
            }
            progress.postValue(false)
        }
    }

    /**
     * 更新所有钱包
     */
    private fun updateAllWallets(wallets: Array<Wallet>, updateType: TokenUpdateType) {
        launchSafely(
            onError = { throwable -> 
                // 忽略更新错误
            }
        ) {
            val walletTokenMap = withIO {
                val map = mutableMapOf<String, Array<Token>>()
                for (wallet in wallets) {
                    val walletTokens = tokensService.syncChainBalances(
                        wallet.address?.lowercase() ?: "", 
                        updateType
                    )
                    if (walletTokens.isNotEmpty()) {
                        val firstToken = walletTokens[0]
                        val walletAddress = firstToken?.getWallet()
                        if (!walletAddress.isNullOrEmpty()) {
                            map[walletAddress] = walletTokens.filterNotNull().toTypedArray()
                        }
                    }
                }
                map
            }
            _baseTokens.postValue(walletTokenMap)
        }
    }

    /**
     * 创建钱包错误处理
     */
    private fun onCreateWalletError(throwable: Throwable) {
        progress.postValue(false)
        _createWalletError.postValue(ErrorEnvelope(C.ErrorCode.UNKNOWN, throwable.message))
    }

    /**
     * 导入钱包
     */
    fun importWallet(activity: Activity) {
        importWalletRouter.openForResult(activity, C.IMPORT_REQUEST_CODE, false)
    }

    /**
     * 显示主页
     */
    fun showHome(context: Context) {
        homeRouter.open(context, true)
    }

    /**
     * 获取网络信息
     */
    fun getNetwork(): NetworkInfo? = currentNetwork

    /**
     * 观察钱包
     */
    fun watchWallet(activity: Activity) {
        importWalletRouter.openWatchCreate(activity, C.IMPORT_REQUEST_CODE)
    }

    /**
     * 完成认证
     */
    fun completeAuthentication(taskCode: Operation) {
        keyService.completeAuthentication(taskCode)
    }

    /**
     * 认证失败
     */
    fun failedAuthentication(taskCode: Operation) {
        keyService.failedAuthentication(taskCode)
    }

    /**
     * 暂停时处理
     */
    fun onPause() {
        balanceUpdateJob?.cancel()
        balanceUpdateJob = null
    }

    /**
     * 获取钱包交互器
     */
    fun getWalletInteract(): GenericWalletInteract = genericWalletInteract

    /**
     * 停止更新
     */
    fun stopUpdates() {
        assetService.stopEventListener()
    }

    /**
     * 销毁时清理
     */
    fun onDestroy() {
        walletServices.values.forEach { svs ->
            svs.stopUpdateCycle()
        }

        currentWalletUpdates.values.forEach { job ->
            job.cancel()
        }

        walletServices.clear()
        currentWalletUpdates.clear()
    }

    /**
     * 存储HD钱包
     */
    fun storeHDWallet(address: String, authLevel: KeyService.AuthenticationLevel) {
        if (address != com.alphawallet.app.entity.tokenscript.TokenscriptFunction.ZERO_ADDRESS) {
            val wallet = Wallet(address).apply {
                type = WalletType.HDKEY
                this.authLevel = authLevel
            }
            
            launchSafely(
                onError = { throwable -> onCreateWalletError(throwable) }
            ) {
                withIO {
                    fetchWalletsInteract.storeWallet(wallet)
                }
                setNewWallet(wallet)
            }
        }
    }

    /**
     * 存储钱包
     */
    fun storeWallet(wallet: Wallet, type: WalletType) {
        // 实现钱包存储逻辑
    }

    /**
     * 存储硬件钱包
     */
    @Throws(SignatureException::class)
    fun storeHardwareWallet(returnSig: SignatureFromKey) {
        val sigData = CryptoFunctions.sigFromByteArray(returnSig.signature)
        val recoveredKey = Sign.signedMessageToKey(TEST_STRING.toByteArray(), sigData)
        val address = Numeric.prependHexPrefix(Keys.getAddress(recoveredKey))

        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            val wallets = withIO {
                fetchWalletsInteract.fetch()
            }
            importOrSetActive(address, wallets)
        }
    }

    /**
     * 导入或设置活跃钱包
     */
    private fun importOrSetActive(addressHex: String, wallets: Array<Wallet>) {
        val existingWallet = findWallet(wallets, addressHex)
        if (existingWallet != null) {
            changeDefaultWallet(existingWallet)
        } else {
            storeHardwareWallet(addressHex)
        }
    }

    /**
     * 查找钱包
     */
    private fun findWallet(wallets: Array<Wallet>, address: String): Wallet? {
        return wallets.find { it.address.equals(address, ignoreCase = true) }
    }

    /**
     * 存储硬件钱包
     */
    private fun storeHardwareWallet(address: String) {
        launchSafely(
            onError = { throwable -> onCreateWalletError(throwable) }
        ) {
            val wallet = withIO {
                importWalletInteract.storeHardwareWallet(address)
            }
            setNewWallet(wallet)
        }
    }

    /**
     * 登录
     */
    fun logIn(address: String) {
        preferenceRepository.logIn(address)
    }

    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        coroutineJobs.forEach { it.cancel() }
        coroutineJobs.clear()
        balanceUpdateJob?.cancel()
        walletBalanceUpdateJob?.cancel()
        ensCheckJob?.cancel()
        ensWrappingCheckJob?.cancel()
    }

    /**
     * UI状态密封类
     */
    sealed class WalletsUiState {
        object Idle : WalletsUiState()
        object Loading : WalletsUiState()
        data class Success(val wallets: Array<Wallet>) : WalletsUiState()
        data class Error(val message: String) : WalletsUiState()
    }
}
