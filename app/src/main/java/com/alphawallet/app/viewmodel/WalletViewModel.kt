package com.alphawallet.app.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Pair
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.alphawallet.app.C
import com.alphawallet.app.entity.AnalyticsProperties
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.WalletType
import com.alphawallet.app.entity.analytics.QrScanSource
import com.alphawallet.app.entity.attestation.ImportAttestation
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.entity.tokens.Attestation
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.entity.walletconnect.WalletConnectSessionItem
import com.alphawallet.app.interact.BackupLevel
import com.alphawallet.app.interact.ChangeTokenEnableInteract
import com.alphawallet.app.interact.FetchTokensInteract
import com.alphawallet.app.interact.GenericWalletInteract
import com.alphawallet.app.repository.OnRampRepositoryType
import com.alphawallet.app.repository.PreferenceRepositoryType
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.app.repository.WalletItem
import com.alphawallet.app.repository.entity.RealmAttestation
import com.alphawallet.app.repository.entity.RealmToken
import com.alphawallet.app.router.CoinbasePayRouter
import com.alphawallet.app.router.ManageWalletsRouter
import com.alphawallet.app.router.MyAddressRouter
import com.alphawallet.app.router.TokenDetailRouter
import com.alphawallet.app.service.AnalyticsServiceType
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.service.RealmManager
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.ui.QRScanning.QRScannerActivity
import com.alphawallet.app.walletconnect.AWWalletConnectClient
import com.alphawallet.token.entity.ContractAddress
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import io.realm.Realm
import io.realm.RealmResults

/**
 * 钱包视图模型类
 *
 * 负责管理钱包相关的所有业务逻辑和数据状态，包括：
 * - 钱包管理和切换
 * - 代币数据获取和更新
 * - 备份警告管理
 * - QR码扫描处理
 * - 钱包连接会话管理
 *
 * 技术特点：
 * - 使用 Hilt 进行依赖注入
 * - 继承自 BaseViewModel 提供基础功能
 * - 使用协程处理异步操作
 * - 支持 LiveData 数据绑定
 */
@HiltViewModel
class WalletViewModel @Inject constructor(
    // 依赖注入的服务和仓库
    private val fetchTokensInteract: FetchTokensInteract,
    private val tokenDetailRouter: TokenDetailRouter,
    private val genericWalletInteract: GenericWalletInteract,
    private val assetDefinitionService: AssetDefinitionService,
    private val tokensService: TokensService,
    private val changeTokenEnableInteract: ChangeTokenEnableInteract,
    private val myAddressRouter: MyAddressRouter,
    private val coinbasePayRouter: CoinbasePayRouter,
    private val manageWalletsRouter: ManageWalletsRouter,
    private val preferenceRepository: PreferenceRepositoryType,
    private val realmManager: RealmManager,
    private val onRampRepository: OnRampRepositoryType,
    private val analyticsService: AnalyticsServiceType<AnalyticsProperties>,
    private val awWalletConnectClient: AWWalletConnectClient
) : BaseViewModel() {

    // LiveData 数据状态管理
    private val _tokens = MutableLiveData<Array<TokenCardMeta>>()
    private val _updatedTokens = MutableLiveData<Array<TokenCardMeta>>()
    private val _defaultWallet = MutableLiveData<Wallet>()
    private val _backupEvent = MutableLiveData<BackupLevel>()
    private val _fiatValues = MutableLiveData<Pair<Double, Double>>()
    private val _tokensToRemove = MutableLiveData<Array<Token>>()

    // 临时状态变量
    private var lastBackupCheck = 0L
    private var lastTokenFetchTime = 0L
    private var dialog: BottomSheetDialog? = null
    private var balanceUpdateCheck: Job? = null

    /**
     * 初始化块
     * 设置依赖注入的服务和仓库，初始化基础配置
     */
    init {
        setAnalyticsService(analyticsService)
    }

    /**
     * 清理资源，取消所有正在进行的操作
     */
    override fun onCleared() {
        super.onCleared()
        balanceUpdateCheck?.cancel()
        dialog?.dismiss()
    }

    // 公共访问器方法

    /**
     * 获取代币数据的LiveData
     * @return 代币数据的只读LiveData
     */
    fun tokens(): LiveData<Array<TokenCardMeta>> = _tokens

    /**
     * 获取更新代币数据的LiveData
     * @return 更新代币数据的只读LiveData
     */
    fun onUpdatedTokens(): LiveData<Array<TokenCardMeta>> = _updatedTokens

    /**
     * 获取默认钱包的LiveData
     * @return 默认钱包的只读LiveData
     */
    fun defaultWallet(): LiveData<Wallet> = _defaultWallet

    /**
     * 获取备份事件的LiveData
     * @return 备份事件的只读LiveData
     */
    fun backupEvent(): LiveData<BackupLevel> = _backupEvent

    /**
     * 获取法币价值的LiveData
     * @return 法币价值的只读LiveData
     */
    fun onFiatValues(): LiveData<Pair<Double, Double>> = _fiatValues

    /**
     * 获取待移除代币的LiveData
     * @return 待移除代币的只读LiveData
     */
    fun removeDisplayTokens(): LiveData<Array<Token>> = _tokensToRemove

    /**
     * 获取当前钱包地址
     * @return 钱包地址字符串，如果没有默认钱包则返回null
     */
    fun getWalletAddr(): String? = _defaultWallet.value?.address

    /**
     * 获取当前钱包类型
     * @return 钱包类型，如果没有默认钱包则返回KEYSTORE
     */
    fun getWalletType(): WalletType = _defaultWallet.value?.type ?: WalletType.KEYSTORE

    /**
     * 获取当前钱包对象
     * @return 当前钱包对象
     */
    fun getWallet(): Wallet? = _defaultWallet.value

    // 公共业务方法

    /**
     * 准备钱包数据
     * 异步获取默认钱包并设置相关数据
     */
    fun prepare() {
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            val wallet = genericWalletInteract.find()
            withMain {
                onDefaultWallet(wallet)
            }
        }
    }

    /**
     * 重新加载代币数据
     * 如果有默认钱包，则重新获取代币信息
     */
    fun reloadTokens() {
        _defaultWallet.value?.let { wallet ->
            launchSafely(
                onError = { throwable -> handleError(throwable) }
            ) {
                fetchTokens(wallet)
            }
        }
    }

    /**
     * 搜索代币
     * @param search 搜索关键词
     */
    fun searchTokens(search: String) {
        _defaultWallet.value?.let { wallet ->
            launchSafely(
                onError = { throwable -> handleError(throwable) }
            ) {
                val searchResults : Array<TokenCardMeta?>? = fetchTokensInteract.searchTokenMetas(
                    wallet,
                    tokensService.getNetworkFilters(),
                    search
                )
                withMain {
                    val filteredResults = searchResults?.filterNotNull()?.toTypedArray() ?: emptyArray()
                    _tokens.postValue(filteredResults)
                }
            }
        }
    }

    /**
     * 开始更新监听器
     * 启动定期检查代币更新的任务
     */
    fun startUpdateListener() {
        balanceUpdateCheck = viewModelScope.launch {
            while (true) {
                delay(TimeUnit.SECONDS.toMillis(10))
                checkTokenUpdates()
            }
        }
    }

    /**
     * 停止更新监听器
     * 取消定期检查代币更新的任务
     */
    fun stopUpdateListener() {
        balanceUpdateCheck?.cancel()
        balanceUpdateCheck = null
    }

    /**
     * 设置密钥备份时间
     * @param walletAddr 钱包地址
     */
    suspend fun setKeyBackupTime(walletAddr: String) {
        genericWalletInteract.updateBackupTime(walletAddr)
    }

    /**
     * 设置密钥警告忽略时间
     * @param walletAddr 钱包地址
     */
    suspend fun setKeyWarningDismissTime(walletAddr: String) {
        genericWalletInteract.updateWarningTime(walletAddr)
    }

    /**
     * 设置代币启用状态
     * @param token 代币对象
     * @param enabled 是否启用
     */
    fun setTokenEnabled(token: Token, enabled: Boolean) {
        val contractAddress = ContractAddress(token.tokenInfo.chainId, token.tokenInfo.address)
        changeTokenEnableInteract.setEnable(_defaultWallet.value, contractAddress, enabled)
        token.tokenInfo.isEnabled = enabled
    }

    /**
     * 显示购买ETH选项
     * @param activity 当前活动上下文
     */
    fun showBuyEthOptions(activity: Activity) {
        coinbasePayRouter.open(activity)
    }

    /**
     * 显示我的地址页面
     * @param context 当前活动上下文
     */
    fun showMyAddress(context: Activity) {
        _defaultWallet.value?.let { wallet ->
            myAddressRouter.open(context, wallet)
        }
    }

    /**
     * 显示QR码扫描页面
     * @param activity 当前活动上下文
     */
    fun showQRCodeScanning(activity: Activity) {
        val intent = Intent(activity, QRScannerActivity::class.java)
        intent.putExtra(C.EXTRA_UNIVERSAL_SCAN, true)
        intent.putExtra(C.EXTRA_SCAN_SOURCE, QrScanSource.WALLET_SCREEN.ordinal)
        activity.startActivityForResult(intent, C.BARCODE_READER_REQUEST_CODE)
    }

    /**
     * 获取代币组信息
     * @param token 代币对象
     * @return 代币组类型
     */
    fun getTokenGroup(token: Token?): TokenGroup? {
        return tokensService.getTokenGroup(token)
    }

    /**
     * 显示代币详情页面
     * @param activity 当前活动上下文
     * @param token 代币对象
     */
    fun showTokenDetail(activity: Activity, token: Token) {
        when (token.getInterfaceSpec()) {
            ContractType.ERC721,
            ContractType.ERC721_LEGACY,
            ContractType.ERC721_TICKET,
            ContractType.ERC721_UNDETERMINED -> {
                tokenDetailRouter.open(activity, token, _defaultWallet.value, false)
            }
            ContractType.ERC1155 -> {
                tokenDetailRouter.open(activity, token, _defaultWallet.value, true)
            }
            ContractType.ERC875_LEGACY,
            ContractType.ERC875 -> {
                tokenDetailRouter.openLegacyToken(activity, token, _defaultWallet.value)
            }
            else -> {
                // 其他类型不处理
            }
        }
    }

    /**
     * 检查备份状态
     * @param fiatValue 法币价值
     */
    fun checkBackup(fiatValue: Double) {
        val walletAddr = getWalletAddr()
        if (TextUtils.isEmpty(walletAddr) || System.currentTimeMillis() < (lastBackupCheck + BALANCE_BACKUP_CHECK_INTERVAL)) {
            return
        }
        lastBackupCheck = System.currentTimeMillis()
        val walletUSDValue = tokensService.convertToUSD(fiatValue)

        if (walletUSDValue > 0.0) {
            launchSafely(
                onError = { throwable -> onTokenBalanceError(throwable) }
            ) {
                val needsBackup = genericWalletInteract.getBackupWarning(walletAddr!!)
                val calcValue = BigDecimal.valueOf(walletUSDValue)
                val backupLevel = calculateBackupWarning(needsBackup, calcValue)
                withMain {
                    _backupEvent.postValue(backupLevel)
                }
            }
        }
    }

    /**
     * 通知刷新
     * 清除焦点代币并触发钱包刷新
     */
    fun notifyRefresh() {
        tokensService.clearFocusToken() // 确保刷新时没有焦点代币阻止正确更新
        tokensService.onWalletRefreshSwipe()
    }

    /**
     * 检查是否为链代币
     * @param chainId 链ID
     * @param tokenAddress 代币地址
     * @return 是否为链代币
     */
    fun isChainToken(chainId: Long, tokenAddress: String): Boolean {
        return tokensService.isChainToken(chainId, tokenAddress)
    }

    /**
     * 计算法币价值
     * 异步获取法币价值对并更新LiveData
     */
    fun calculateFiatValues() {
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            val fiatValuePair = tokensService.getFiatValuePair()
            withMain {
                val safePair = fiatValuePair?.let { pair ->
                    Pair(pair.first ?: 0.0, pair.second ?: 0.0)
                } ?: Pair(0.0, 0.0)
                _fiatValues.postValue(safePair)
            }
        }
    }

    /**
     * 显示管理钱包页面
     * @param context 上下文
     * @param clearStack 是否清除堆栈
     */
    fun showManageWallets(context: Context, clearStack: Boolean) {
        manageWalletsRouter.open(context, clearStack)
    }

    /**
     * 检查是否已显示Marshmallow警告
     * @return 是否已显示警告
     */
    fun isMarshMallowWarningShown(): Boolean {
        return preferenceRepository.isMarshMallowWarningShown
    }

    /**
     * 设置Marshmallow警告状态
     * @param shown 是否已显示
     */
    fun setMarshMallowWarning(shown: Boolean) {
        preferenceRepository.setMarshMallowWarning(shown)
    }

    /**
     * 保存钱包头像
     * @param wallet 钱包对象
     */
    fun saveAvatar(wallet: Wallet) {
        launchSafely {
            genericWalletInteract.updateWalletItem(wallet, WalletItem.ENS_AVATAR) {}
        }
    }

    /**
     * 获取购买意图
     * @param address 钱包地址
     * @return 购买意图
     */
    fun getBuyIntent(address: String): Intent {
        val intent = Intent()
        intent.putExtra(C.DAPP_URL_LOAD, onRampRepository.getUri(address, null))
        return intent
    }

    /**
     * 获取活跃的WalletConnect会话
     * @return WalletConnect会话项的LiveData
     */
    fun activeWalletConnectSessions(): MutableLiveData<List<WalletConnectSessionItem>> {
        return awWalletConnectClient.sessionItemMutableLiveData()
    }

    /**
     * 检查并删除元数据
     * @param metas 代币卡片元数据数组
     */
    fun checkDeleteMetas(metas: Array<TokenCardMeta>) {
        val metasToDelete = metas.filter { it.balance == "-2" }

        if (metasToDelete.isNotEmpty()) {
            launchSafely(
                onError = { throwable -> handleError(throwable) }
            ) {
                tokensService.deleteTokens(metasToDelete)
            }
        }
    }

    /**
     * 移除代币元数据项
     * @param tokenKeyId 代币键ID
     */
    fun removeTokenMetaItem(tokenKeyId: String) {
        val tokenKey = if (tokenKeyId.endsWith(Attestation.ATTESTATION_SUFFIX)) {
            tokenKeyId.substring(0, tokenKeyId.length - Attestation.ATTESTATION_SUFFIX.length)
        } else {
            tokenKeyId
        }

        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            withContext(Dispatchers.IO) {
                val wallet = _defaultWallet.value ?: return@withContext
                realmManager.getRealmInstance(wallet).use { realm ->
                    realm.executeTransaction { r ->
                        val realmAttn = r.where(RealmAttestation::class.java)
                            .equalTo("address", tokenKey)
                            .findFirst()
                        realmAttn?.deleteFromRealm()
                    }
                }
            }
        }
    }

    /**
     * 删除代币
     * @param token 要删除的代币
     */
    fun deleteToken(token: Token) {
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            withContext(Dispatchers.IO) {
                val wallet = _defaultWallet.value ?: return@withContext
                realmManager.getRealmInstance(wallet).use { realm ->
                    realm.executeTransaction { r ->
                        val realmAttn = r.where(RealmAttestation::class.java)
                            .equalTo("address", token.getDatabaseKey())
                            .findFirst()
                        realmAttn?.deleteFromRealm()
                    }
                }
            }
        }
    }

    /**
     * 检查已移除的元数据
     * 查找标记为删除的认证并移除相应的代币
     */
    fun checkRemovedMetas() {
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            val forRemoval = withContext(Dispatchers.IO) {
                val tokensForRemoval = mutableListOf<Token>()
                val wallet = _defaultWallet.value ?: return@withContext tokensForRemoval.toTypedArray()

                realmManager.getRealmInstance(wallet).use { r: Realm ->
                    val attnResults: RealmResults<RealmAttestation> = r.where(RealmAttestation::class.java)
                        .equalTo("collectionId", ImportAttestation.DELETE_KEY)
                        .findAll()

                    for (i in 0 until attnResults.size) {
                        val attn = attnResults[i] ?: continue
                        val chainId = attn.chains[0]
                        val attestationForRemoval = tokensService.getAttestation(
                            chainId,
                            attn.tokenAddress,
                            attn.attestationID
                        ) as? Attestation
                        attestationForRemoval?.let { tokensForRemoval.add(it) }
                    }

                    if (attnResults.size > 0) {
                        r.executeTransaction {
                            attnResults.deleteAllFromRealm()
                        }
                    }
                }

                tokensForRemoval.toTypedArray()
            }

            withMain {
                _tokensToRemove.postValue(forRemoval)
            }
        }
    }

    // 服务访问器方法

    /**
     * 获取资产定义服务
     * @return 资产定义服务实例
     */
    fun getAssetDefinitionService(): AssetDefinitionService = assetDefinitionService

    /**
     * 获取代币服务
     * @return 代币服务实例
     */
    fun getTokensService(): TokensService = tokensService

    /**
     * 从服务获取代币
     * @param token 代币对象
     * @return 从服务获取的代币对象
     */
    fun getTokenFromService(token: Token): Token {
        val serviceToken = tokensService.getToken(token.tokenInfo.chainId, token.getAddress())
        return if (serviceToken != null && serviceToken.isEthereum()) {
            tokensService.getServiceToken(token.tokenInfo.chainId) ?: token
        } else {
            serviceToken ?: token
        }
    }

    // 私有辅助方法

    /**
     * 处理默认钱包设置
     * @param wallet 默认钱包对象
     */
    private fun onDefaultWallet(wallet: Wallet?) {

        wallet?.let {
            _defaultWallet.postValue(it)
            launchSafely(
                onError = { throwable -> handleError(throwable) }
            ) {
                fetchTokens(it)
            }
        }
    }

    /**
     * 获取代币数据
     * @param wallet 钱包对象
     */
    private suspend fun fetchTokens(wallet: Wallet) {
        val tokenMetas = fetchTokensInteract.fetchTokenMetas(
            wallet,
            tokensService.getNetworkFilters(),
            assetDefinitionService
        )
        withMain {
            onTokenMetas(tokenMetas ?: emptyArray())
        }
    }

    /**
     * 处理代币元数据
     * @param metaTokens 代币元数据数组
     */
    private fun onTokenMetas(metaTokens: Array<TokenCardMeta?>) {
        lastTokenFetchTime = System.currentTimeMillis()
        _tokens.postValue(metaTokens.filterNotNull().toTypedArray())
        checkDeleteMetas(metaTokens.filterNotNull().toTypedArray())
    }

    /**
     * 检查代币更新
     * 获取更新的代币元数据并发布到LiveData
     */
    private suspend fun checkTokenUpdates() {
        val updatedMetas = getUpdatedTokenMetas()
        withMain {
            _updatedTokens.postValue(updatedMetas)
        }
    }

    /**
     * 获取更新的代币元数据
     * @return 更新的代币元数据数组
     */
    private suspend fun getUpdatedTokenMetas(): Array<TokenCardMeta> {
        return withContext(Dispatchers.IO) {
            val tokenMetas = mutableListOf<TokenCardMeta>()
            
            val wallet = _defaultWallet.value ?: return@withContext tokenMetas.toTypedArray()
            
            realmManager.getRealmInstance(wallet).use { r: Realm ->
                val results = r.where(RealmToken::class.java)
                    .equalTo("isEnabled", true)
                    .like("address", TokensRealmSource.ADDRESS_FORMAT)
                    .greaterThan("updatedTime", lastTokenFetchTime)
                    .findAll()

                for (i in 0 until results.size) {
                    val token = results[i] ?: continue
                    if (!tokensService.getNetworkFilters().contains(token.chainId)) {
                        continue
                    }

                    val balance = TokensRealmSource.convertStringBalance(
                        token.balance,
                        token.contractType
                    )

                    val meta = TokenCardMeta(
                        token.chainId,
                        token.tokenAddress,
                        balance,
                        token.updateTime,
                        assetDefinitionService,
                        token.name,
                        token.symbol,
                        token.contractType,
                        getTokenGroup(tokensService.getToken(token.chainId, token.tokenAddress))
                    ).apply {
                        lastTxUpdate = token.lastTxTime
                        isEnabled = token.isEnabled
                    }
                    
                    tokenMetas.add(meta)
                    
                    if (token.balanceUpdateTime > lastTokenFetchTime) {
                        lastTokenFetchTime = token.balanceUpdateTime + 1
                    }
                }
            }

            tokenMetas.toTypedArray()
        }
    }

    /**
     * 处理代币余额错误
     * @param throwable 异常对象
     */
    private fun onTokenBalanceError(throwable: Throwable) {
        // 无法解析 - 手机可能离线
    }

    /**
     * 计算备份警告级别
     * @param needsBackup 是否需要备份
     * @param value 钱包价值
     * @return 备份警告级别
     */
    private fun calculateBackupWarning(
        needsBackup: Boolean,
        value: BigDecimal
    ): BackupLevel {
        return when {
            !needsBackup -> BackupLevel.BACKUP_NOT_REQUIRED
            value >= BigDecimal.valueOf(VALUE_THRESHOLD) -> BackupLevel.WALLET_HAS_HIGH_VALUE
            else -> BackupLevel.WALLET_HAS_LOW_VALUE
        }
    }

    companion object {
        // 常量定义
        const val BALANCE_BACKUP_CHECK_INTERVAL = 5 * DateUtils.MINUTE_IN_MILLIS // 备份检查间隔时间
        const val VALUE_THRESHOLD = 200.0 // 高价值钱包阈值（美元）
    }
}
