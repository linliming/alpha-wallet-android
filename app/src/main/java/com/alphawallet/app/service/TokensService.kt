package com.alphawallet.app.service

import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Pair
import com.alphawallet.app.BuildConfig
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.entity.AnalyticsProperties
import com.alphawallet.app.entity.ContractLocator
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.CustomViewSettings
import com.alphawallet.app.entity.ImageEntry
import com.alphawallet.app.entity.NetworkInfo
import com.alphawallet.app.entity.ServiceSyncCallback
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.okx.OkProtocolType
import com.alphawallet.app.entity.okx.OkToken
import com.alphawallet.app.entity.okx.OkTokenCheck
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.entity.tokendata.TokenTicker
import com.alphawallet.app.entity.tokendata.TokenUpdateType
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.entity.tokens.TokenFactory
import com.alphawallet.app.entity.tokens.TokenInfo
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.repository.EthereumNetworkRepositoryType
import com.alphawallet.app.repository.TokenRepositoryType
import com.alphawallet.app.repository.TokensRealmSource.Companion.databaseKey
import com.alphawallet.app.util.CoroutineUtils.await
import com.alphawallet.app.util.Utils
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import com.alphawallet.token.entity.ContractAddress
import io.realm.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * TokensService - 代币服务类
 *
 * 这个类是AlphaWallet中最核心的服务之一，负责管理所有与代币相关的操作。
 * 主要功能包括：
 * 1. 代币信息的获取、存储和更新
 * 2. 代币余额的定期检查和同步
 * 3. 价格信息(Ticker)的管理和更新
 * 4. 多链网络的管理和过滤
 * 5. NFT资产的处理和管理
 * 6. 第三方API集成（OpenSea、OKX等）
 * 7. 未知代币的自动发现和识别
 * 8. 用户钱包焦点管理
 *
 * @author AlphaWallet Team
 * @since 2024
 */
class TokensService(
    // 依赖注入的核心组件
    private val ethereumNetworkRepository: EthereumNetworkRepositoryType,
    private val tokenRepository: TokenRepositoryType,
    private val tickerService: TickerService,
    private val openseaService: OpenSeaService,
    private val analyticsService: AnalyticsServiceType<AnalyticsProperties>,
    private val httpClient: OkHttpClient,
) {
    companion object {
        private const val TAG = "TOKENSSERVICE"
        const val UNKNOWN_CONTRACT = "[Unknown Contract]"
        const val EXPIRED_CONTRACT = "[Expired Contract]"

        // 等待处理的链超时时间：3分钟
        const val PENDING_TIME_LIMIT = 3 * DateUtils.MINUTE_IN_MILLIS

        // 更新周期相关常量
        private const val UPDATE_CYCLE_RESTART_THRESHOLD = 10000L // 10秒
        private const val UPDATE_CYCLE_MIN_INTERVAL = 2000L // 2秒
        private const val PERIODIC_UPDATE_INTERVAL = 500L // 500毫秒
        private const val SYNC_TIMEOUT = 5L // 5秒

        // 静态状态变量
        private val pendingChainMap = ConcurrentHashMap<Long, Long>()
        private var walletStartup = false
    }

    // 核心协程作用域 - 用于管理所有异步操作
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 数据存储队列 - 线程安全的数据结构
    private val tokenStoreList = ConcurrentLinkedDeque<Token>() // 待存储的代币列表
    private val pendingTokenMap = ConcurrentHashMap<String, Long>() // 代币更新时间映射
    private val unknownTokens = ConcurrentLinkedDeque<ContractAddress>() // 未知代币列表
    private val baseTokenCheck = ConcurrentLinkedQueue<Long>() // 基础代币检查队列
    private val imagesForWrite = ConcurrentLinkedQueue<ImageEntry>() // 待写入的图片队列
    private val chainCheckList = ConcurrentLinkedQueue<OkTokenCheck>() // OKX链检查列表

    // 服务状态变量
    private var currentAddress: String? = null // 当前钱包地址
    private val networkFilter = mutableListOf<Long>() // 网络过滤器
    private var focusToken: ContractLocator? = null // 当前焦点代币
    private var openSeaCheckId: Long = 0 // OpenSea检查的链ID
    private var appHasFocus = true // 应用是否有焦点
    private var transferCheckChain: Long = 0 // 转账检查的链
    private var syncTimer: Long = 0 // 同步计时器
    private var syncStart: Long = 0 // 同步开始时间
    private var completionCallback: ServiceSyncCallback? = null // 同步完成回调
    private var syncCount = 0 // 同步计数
    private var lastStartCycleTime: Long = 0 // 上次开始周期的时间

    // 代币工厂
    private val tokenFactory = TokenFactory()

    // 协程任务管理
    private var updateCycleJob: Job? = null // 更新周期任务
    private var unknownTokenCheckJob: Job? = null // 未知代币检查任务
    private var imageWriteJob: Job? = null // 图片写入任务
    private var okxCheckJob: Job? = null // OKX检查任务
    private var balanceCheckJob: Job? = null // 余额检查任务
    private var erc20CheckJob: Job? = null // ERC20检查任务
    private var openSeaQueryJob: Job? = null // OpenSea查询任务

    init {
        // 初始化服务
        setupFilter(ethereumNetworkRepository.hasSetNetworkFilters())
        setCurrentAddress(ethereumNetworkRepository.getCurrentWalletAddress())
        Timber.tag(TAG).d("TokensService initialized")
    }

    /**
     * 获取指定链和地址的代币
     *
     * @param chainId 链ID
     * @param addr 代币地址
     * @return Token对象，如果不存在则返回null
     */
    fun getToken(
        chainId: Long,
        addr: String?,
    ): Token? {
        if (TextUtils.isEmpty(currentAddress) || TextUtils.isEmpty(addr)) return null
        return tokenRepository.fetchToken(chainId, currentAddress!!, addr?.lowercase())
    }

    /**
     * 存储代币到数据库
     * 这是一个低优先级的操作，会将代币添加到存储队列中
     *
     * @param token 要存储的代币
     */
    fun storeToken(token: Token) {
        if (TextUtils.isEmpty(currentAddress) || token.getInterfaceSpec() == ContractType.OTHER) return

        serviceScope.launch {
            try {
                val checkedToken = tokenRepository.checkInterface(token, Wallet(token.getWallet()))
                tokenStoreList.add(checkedToken)
            } catch (e: Exception) {
                onERC20Error(e)
            }
        }
    }

    /**
     * 获取代币的价格信息
     *
     * @param token 代币对象
     * @return TokenTicker价格信息对象
     */
    fun getTokenTicker(token: Token): TokenTicker? = tokenRepository.getTokenTicker(token)

    /**
     * 获取所有代币的元数据
     *
     * @param searchString 搜索字符串，用于过滤代币
     * @return 代币元数据数组的Flow
     */
    suspend fun getAllTokenMetas(searchString: String): Array<TokenCardMeta?>? = tokenRepository.fetchAllTokenMetas(Wallet(currentAddress!!), networkFilter, searchString)

    /**
     * 获取指定地址在所有链上的代币
     *
     * @param addr 代币合约地址
     * @return 代币列表
     */
    fun getAllAtAddress(addr: String?): List<Token> {
        val tokens = mutableListOf<Token>()
        if (addr == null) return tokens

        for (chainId in networkFilter) {
            getToken(chainId, addr)?.let { tokens.add(it) }
        }
        return tokens
    }

    /**
     * 设置当前钱包地址
     * 当切换钱包时会调用此方法，重置所有相关状态
     *
     * @param newWalletAddr 新的钱包地址
     */
    fun setCurrentAddress(newWalletAddr: String?) {
        if (newWalletAddr != null && (currentAddress == null || !currentAddress.equals(newWalletAddr, ignoreCase = true))) {
            currentAddress = newWalletAddr.lowercase()
            stopUpdateCycle()
            addLockedTokens()
            openseaService.resetOffsetRead(networkFilter)
            tokenRepository.updateLocalAddress(newWalletAddr)
            lastStartCycleTime = 0
            Timber.tag(TAG).d("Current address changed to: $newWalletAddr")
        }
    }

    /**
     * 启动代币数据更新周期。
     *
     * 会根据当前时间和上次启动时间判断是否允许重启，随后：
     * 1. 校验当前钱包地址是否有效；
     * 2. 重置关键状态并构建网络过滤器；
     * 3. 发起后台协程，依次执行启动检查、问题代币扫描、余额刷新等任务。
     * 循环过程中还会维护同步计数、超时控制以及待处理队列，确保更新稳定进行。
     */
    fun startUpdateCycle() {
        val currentTime = System.currentTimeMillis()

        // 防止频繁重启更新周期
        if ((updateCycleJob?.isActive == true && (lastStartCycleTime + UPDATE_CYCLE_RESTART_THRESHOLD) > currentTime) ||
            (lastStartCycleTime + UPDATE_CYCLE_MIN_INTERVAL) > currentTime
        ) {
            return
        }

        lastStartCycleTime = currentTime

        if (!Utils.isAddressValid(currentAddress)) {
            Timber.tag(TAG).w("Invalid current address, cannot start update cycle")
            return
        }

        stopUpdateCycle()
        syncCount = 0
        setupFilters()

        updateCycleJob =
            serviceScope.launch {
                try {
                    // 初始化检查
                    startupPass()
                    checkIssueTokens()
                    pendingTokenMap.clear()
                    checkTokensOnOKx()

                    // 开始定期更新周期
                    startPeriodicUpdateCycle()
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error in update cycle initialization")
                    onError(e)
                }
            }
    }

    /**
     * 开始定期更新周期
     * 每500毫秒检查一次代币余额
     */
    private suspend fun startPeriodicUpdateCycle() {
        syncStart = System.currentTimeMillis()
        syncTimer = syncStart + SYNC_TIMEOUT * DateUtils.SECOND_IN_MILLIS

        // 使用Flow创建定期执行的任务
        flow {
            while (currentCoroutineContext().isActive) {
                emit(Unit)
                delay(PERIODIC_UPDATE_INTERVAL)
            }
        }.collect {
            checkTokensBalance()
        }
    }

    /**
     * 重启更新周期
     */
    fun restartUpdateCycle() {
        stopUpdateCycle()
        lastStartCycleTime = 0
        startUpdateCycle()
    }

    /**
     * 条件式启动更新周期。
     *
     * 当后台没有活跃的更新任务时，触发完整的代币同步流程；若循环仍在运行，
     * 则避免重复创建协程，降低资源占用。
     */
    fun startUpdateCycleIfRequired() {
        if (updateCycleJob?.isActive != true) {
            startUpdateCycle()
        }
    }

    /**
     * 停止所有代币同步相关的后台任务。
     *
     * 该方法会取消余额检查、行情刷新、OpenSea 查询等协程，并清空临时队列，
     * 适用于钱包切换或应用进入后台时的资源回收。
     */
    fun stopUpdateCycle() {
        // 取消所有协程任务
        updateCycleJob?.cancel()
        balanceCheckJob?.cancel()
        erc20CheckJob?.cancel()
        openSeaQueryJob?.cancel()
        unknownTokenCheckJob?.cancel()
        okxCheckJob?.cancel()

        // 清理数据结构
        pendingChainMap.clear()
        tokenStoreList.clear()
        baseTokenCheck.clear()
        pendingTokenMap.clear()
        unknownTokens.clear()
        chainCheckList.clear()

        Timber.tag(TAG).d("Update cycle stopped")
    }

    /**
     * 构建代币更新映射表
     * 分析哪些代币需要更新，计算优先级
     *
     * @return 需要更新的代币元数据数组
     */
    private suspend fun buildUpdateMap(): Array<TokenCardMeta> {
        var unSynced = 0
        val tokenList = tokenRepository.fetchTokenMetasForUpdate(Wallet(currentAddress!!), networkFilter) ?: return emptyArray()

        for (meta in tokenList.filterNotNull()) {
            meta.lastTxUpdate = meta.lastUpdate
            val key = databaseKey(meta.chain, meta.address)

            if (!pendingTokenMap.containsKey(key)) {
                if (meta.type == ContractType.ERC20 || meta.type == ContractType.ETHEREUM) unSynced++
                pendingTokenMap[key] = meta.lastUpdate
            } else if (meta.lastUpdate <= pendingTokenMap[key]!!) {
                meta.lastUpdate = pendingTokenMap[key]!!
                if ((meta.type == ContractType.ERC20 || meta.type == ContractType.ETHEREUM) &&
                    meta.lastUpdate < syncStart && meta.isEnabled && meta.hasValidName()
                ) {
                    unSynced++
                }
            } else if ((meta.type == ContractType.ERC20 || meta.type == ContractType.ETHEREUM) &&
                meta.lastUpdate < syncStart && meta.isEnabled && meta.hasValidName()
            ) {
                unSynced++
            }
        }

        val nonNullTokenList = tokenList.filterNotNull().toTypedArray()
        checkSyncStatus(unSynced, nonNullTokenList)
        return nonNullTokenList
    }

    /**
     * 检查同步状态
     *
     * @param unSynced 未同步的代币数量
     * @param tokenList 代币列表
     */
    private suspend fun checkSyncStatus(
        unSynced: Int,
        tokenList: Array<TokenCardMeta>,
    ) {
        if (syncTimer > 0 && System.currentTimeMillis() > syncTimer) {
            syncTimer =
                if (unSynced > 0) {
                    System.currentTimeMillis() + 5 * DateUtils.SECOND_IN_MILLIS
                } else {
                    0
                }
            syncChainTickers(tokenList, 0)
        }
    }

    /**
     * 同步链上的价格信息
     *
     * @param tokenList 代币列表
     * @param chainIndex 当前检查的链索引
     */
    private suspend fun syncChainTickers(
        tokenList: Array<TokenCardMeta>,
        chainIndex: Int,
    ) {
        val networks = ethereumNetworkRepository.getAvailableNetworkList()

        for (i in chainIndex until networks.size) {
            val info = networks[i]
            if (info.hasRealValue() && syncERC20Tickers(i, info.chainId, tokenList)) {
                return
            }
        }

        // 同步完成
        completionCallback?.syncComplete(this, syncCount)
    }

    /**
     * 同步指定链上的ERC20代币价格
     *
     * @param chainIndex 链索引
     * @param chainId 链ID
     * @param tokenList 代币列表
     * @return 是否执行了同步操作
     */
    private suspend fun syncERC20Tickers(
        chainIndex: Int,
        chainId: Long,
        tokenList: Array<TokenCardMeta>,
    ): Boolean {
        val erc20OnChain = getERC20OnChain(chainId, tokenList)

        return if (erc20OnChain.isNotEmpty()) {
            serviceScope.launch {
                try {
                    tickerService.syncERC20Tickers(chainId, erc20OnChain)
                    syncChainTickers(tokenList, chainIndex + 1)
                } catch (e: Exception) {
                    onERC20Error(e)
                }
            }
            true
        } else {
            false
        }
    }

    /**
     * 获取指定链上的ERC20代币
     *
     * @param chainId 链ID
     * @param tokenList 代币列表
     * @return ERC20代币列表
     */
    private fun getERC20OnChain(
        chainId: Long,
        tokenList: Array<TokenCardMeta>,
    ): List<TokenCardMeta> =
        tokenList.filter { tcm ->
            tcm.type == ContractType.ERC20 && tcm.hasPositiveBalance() && tcm.chain == chainId
        }

    /**
     * 创建基础代币
     *
     * @param chainId 链ID
     * @return 创建的代币数组的Flow
     */
    suspend fun createBaseToken(chainId: Long): Array<Token> {
        val token = tokenRepository.fetchToken(chainId, currentAddress!!, currentAddress!!)

        if (!networkFilter.contains(chainId)) {
            networkFilter.add(chainId)
            ethereumNetworkRepository.setFilterNetworkList(networkFilter.toTypedArray())
        }

        return tokenRepository.storeTokens(Wallet(currentAddress!!), arrayOf(token))?.filterNotNull()?.toTypedArray() ?: emptyArray()
    }

    // 获取当前地址
    fun getCurrentAddress(): String? = currentAddress

    // 设置钱包启动标志
    fun setWalletStartup() {
        walletStartup = true
    }

    /**
     * 设置过滤器
     *
     * @param userUpdated 是否由用户更新
     */
    fun setupFilter(userUpdated: Boolean) {
        networkFilter.clear()

        val lockedChains = CustomViewSettings.lockedChains
        if (lockedChains.isNotEmpty()) {
            networkFilter.addAll(lockedChains)
        } else {
            networkFilter.addAll(ethereumNetworkRepository.getFilterNetworkList())
        }

        if (userUpdated) {
            ethereumNetworkRepository.setHasSetNetworkFilters()
        }
    }

    /**
     * 设置焦点代币
     *
     * @param token 要设置焦点的代币
     */
    fun setFocusToken(token: Token) {
        focusToken = ContractLocator(token.getAddress(), token.tokenInfo.chainId)
        Timber.tag(TAG).d("Focus token set: ${token.getFullName()}")
    }

    /**
     * 清除焦点代币
     */
    fun clearFocusToken() {
        focusToken = null
        Timber.tag(TAG).d("Focus token cleared")
    }

    /**
     * 钱包刷新时调用
     */
    fun onWalletRefreshSwipe() {
        openseaService.resetOffsetRead(networkFilter)
    }

    /**
     * 检查是否为焦点代币
     */
    private fun isFocusToken(token: Token): Boolean = focusToken != null && focusToken!!.equals(token)

    /**
     * 检查是否为焦点代币
     */
    private fun isFocusToken(meta: TokenCardMeta): Boolean = focusToken != null && focusToken!!.equals(meta)

    /**
     * 添加未知代币到检查列表
     *
     * @param cAddr 合约地址
     */
    fun addUnknownTokenToCheck(cAddr: ContractAddress) {
        // 检查是否已经在列表中
        for (check in unknownTokens) {
            if (check.chainId == cAddr.chainId && check.address.equals(cAddr.address, ignoreCase = true)) {
                return
            }
        }

        if (getToken(cAddr.chainId, cAddr.address) == null) {
            unknownTokens.addLast(cAddr)
            startUnknownCheck()
        }
    }

    /**
     * 添加未知代币到检查列表（高优先级）
     *
     * @param cAddr 合约地址
     */
    fun addUnknownTokenToCheckPriority(cAddr: ContractAddress) {
        // 检查是否已经在列表中
        for (check in unknownTokens) {
            if (check.chainId == cAddr.chainId && (check.address == null || check.address.equals(cAddr.address, ignoreCase = true))) {
                return
            }
        }

        if (getToken(cAddr.chainId, cAddr.address) == null) {
            unknownTokens.addFirst(cAddr)
            startUnknownCheck()
        }
    }

    /**
     * 开始未知代币检查
     */
    private fun startUnknownCheck() {
        if (unknownTokenCheckJob?.isActive != true) {
            unknownTokenCheckJob =
                serviceScope.launch {
                    while (isActive) {
                        checkUnknownTokens()
                        delay(500)
                    }
                }
        }
    }

    /**
     * 检查未知代币
     */
    private suspend fun checkUnknownTokens() {
        val contractAddress = unknownTokens.pollFirst()

        if (contractAddress != null && contractAddress.address.isNotEmpty()) {
            val cachedToken = getToken(contractAddress.chainId, contractAddress.address)

            if (cachedToken == null || TextUtils.isEmpty(cachedToken.tokenInfo.name)) {
                try {
                    val tokenInfo = TokenInfo(contractAddress.address, "", "", 18, false, contractAddress.chainId)
                    val contractType = tokenRepository.determineCommonType(tokenInfo) ?: ContractType.NOT_SET
                    val updatedTokenInfo = tokenRepository.update(contractAddress.address, contractAddress.chainId, contractType) ?: tokenInfo
                    val token =
                        tokenFactory.createToken(
                            updatedTokenInfo,
                            contractType,
                            ethereumNetworkRepository.getNetworkByChain(contractAddress.chainId).shortName,
                        )

                    tokenRepository.updateTokenBalance(currentAddress!!, token)
                } catch (e: Exception) {
                    onCheckError(e, contractAddress)
                }
            }
        } else if (contractAddress == null) {
            // 停止检查
            unknownTokenCheckJob?.cancel()
        }
    }

    /**
     * 处理检查错误
     */
    private fun onCheckError(
        throwable: Throwable,
        contractAddress: ContractAddress,
    ) {
        Timber.tag(TAG).e(throwable, "Error checking unknown token: ${contractAddress.address}")
    }

    /**
     * 开始图片写入任务
     */
    private fun startImageWrite() {
        if (imageWriteJob?.isActive != true) {
            imageWriteJob =
                serviceScope.launch {
                    while (isActive) {
                        writeImages()
                        delay(500)
                    }
                }
        }
    }

    /**
     * 写入图片
     */
    private suspend fun writeImages() {
        if (imagesForWrite.isEmpty()) {
            imageWriteJob?.cancel()
        } else {
            try {
                val entries = mutableListOf<ImageEntry>()
                while (imagesForWrite.isNotEmpty()) {
                    imagesForWrite.poll()?.let { entries.add(it) }
                }
                tokenRepository.addImageUrl(entries)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error writing images")
            }
        }
    }

    /**
     * 在OKX上检查代币
     */
    private fun checkTokensOnOKx() {
        // 刷新检查列表
        chainCheckList.clear()

        for (chainId in networkFilter) {
            if (OkLinkService.supportsChain(chainId)) {
                chainCheckList.add(OkTokenCheck(chainId, OkProtocolType.ERC_20))
                chainCheckList.add(OkTokenCheck(chainId, OkProtocolType.ERC_721))
                chainCheckList.add(OkTokenCheck(chainId, OkProtocolType.ERC_1155))
            }
        }

        if (okxCheckJob?.isActive != true) {
            okxCheckJob =
                serviceScope.launch {
                    delay(2000) // 初始延迟
                    while (isActive) {
                        checkChainOnOkx()
                        delay(1000)
                    }
                }
        }
    }

    /**
     * 在OKX上检查链
     */
    private suspend fun checkChainOnOkx() {
        val tokenCheck = chainCheckList.poll()

        if (tokenCheck == null) {
            okxCheckJob?.cancel()
            return
        }

        try {
            checkOkTokens(tokenCheck.chainId, tokenCheck.type)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error checking OKX tokens")
        }
    }

    /**
     * 检查OKX代币
     */
    private suspend fun checkOkTokens(
        chainId: Long,
        tokenType: OkProtocolType,
    ) {
        try {
            val tokenList = OkLinkService.get(httpClient).getTokensForChain(chainId, currentAddress!!, tokenType).await()
            processOkTokenList(tokenList, chainId, tokenType)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error fetching OKX tokens for chain $chainId")
        }
    }

    /**
     * 处理OKX代币列表
     */
    private suspend fun processOkTokenList(
        tokenList: List<OkToken>,
        chainId: Long,
        tokenType: OkProtocolType,
    ) {
        val tickerMap = HashMap<String, TokenTicker>()

        for (okToken in tokenList) {
            // 检查代币是否已知
            var token = getToken(chainId, okToken.tokenContractAddress)
            if (token == null) {
                addUnknownTokenToCheck(ContractAddress(chainId, okToken.tokenContractAddress))
            }

            when (tokenType) {
                OkProtocolType.ERC_20 -> {
                    if (!TextUtils.isEmpty(okToken.priceUsd) && okToken.priceUsd != "0") {
                        val ticker =
                            TokenTicker(
                                okToken.priceUsd,
                                "0",
                                okToken.symbol,
                                "",
                                System.currentTimeMillis(),
                            )
                        tickerMap[okToken.tokenContractAddress] = ticker
                    }
                }
                OkProtocolType.ERC_721, OkProtocolType.ERC_1155 -> {
                    // 处理NFT
                    val tokenId = BigInteger(okToken.tokenId)
                    if (token == null) {
                        token =
                            tokenFactory.createToken(
                                okToken.createInfo(chainId),
                                OkProtocolType.getStandardType(tokenType),
                                ethereumNetworkRepository.getNetworkByChain(chainId).shortName,
                            )
                    }

                    val asset = token?.getAssetForToken(tokenId)
                    if (asset == null) {
                        val newAsset =
                            NFTAsset(tokenId).apply {
                                setBalance(BigDecimal(okToken.holdingAmount))
                            }
                        storeAsset(token, tokenId, newAsset)
                    }
                }
            }
        }

        if (tokenType == OkProtocolType.ERC_20) {
            tickerService.storeTickers(chainId, tickerMap)
        }
    }

    /**
     * 启动时检查
     */
    private suspend fun startupPass() {
        if (!walletStartup) return

        walletStartup = false

        try {
            // 一次性检查所有空名称的代币
            val contractAddrs = tokenRepository.fetchAllTokensWithBlankName(currentAddress!!, networkFilter)
            contractAddrs?.filterNotNull()?.let { unknownTokens.addAll(it) }
            startUnknownCheck()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in startup pass")
        }
    }

    /**
     * 获取网络过滤器
     */
    fun getNetworkFilters(): List<Long> = networkFilter.toList()

    /**
     * 获取网络名称
     */
    fun getNetworkName(chainId: Long): String {
        val info = ethereumNetworkRepository.getNetworkByChain(chainId)
        return info?.shortName ?: ""
    }

    /**
     * 获取网络符号
     */
    fun getNetworkSymbol(chainId: Long): String {
        var info = ethereumNetworkRepository.getNetworkByChain(chainId)
        if (info == null) {
            info = ethereumNetworkRepository.getNetworkByChain(MAINNET_ID)
        }
        return info.symbol
    }

    /**
     * 添加代币图片URL到写入队列
     */
    fun addTokenImageUrl(
        networkId: Long,
        address: String,
        imageUrl: String,
    ) {
        val entry = ImageEntry(networkId, address, imageUrl)
        imagesForWrite.add(entry)
        startImageWrite()
    }

    /**
     * 更新代币信息
     */
    suspend fun update(
        address: String,
        chainId: Long,
        type: ContractType,
    ): TokenInfo? = tokenRepository.update(address, chainId, type)

    /**
     * 检查有问题的代币
     */
    private suspend fun checkIssueTokens() {
        try {
            val tokens = tokenRepository.fetchTokensThatMayNeedUpdating(currentAddress!!, networkFilter)
            tokens?.forEach { token ->
                token?.let { storeToken(it) }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error checking issue tokens")
        }
    }

    /**
     * 检查代币余额
     * 这是更新周期的核心方法
     */
    private suspend fun checkTokensBalance() {
        val token = getNextInBalanceUpdateQueue()

        if (token != null) {
            Timber.tag(TAG).d("Updating: ${token.tokenInfo.chainId}${if (token.isEthereum()) " (Base Chain) " else ""}: ${token.getAddress()}: ${token.getFullBalance()}")

            balanceCheckJob =
                serviceScope.launch {
                    try {
                        val newBalance = tokenRepository.updateTokenBalance(currentAddress!!, token) ?: BigDecimal.ZERO
                        onBalanceChange(newBalance, token)
                    } catch (e: Exception) {
                        onError(e)
                    }
                }
        }

        checkPendingChains()
    }

    /**
     * 获取链余额
     */
    suspend fun getChainBalance(
        walletAddress: String,
        chainId: Long,
    ): BigDecimal = tokenRepository.fetchChainBalance(walletAddress, chainId) ?: BigDecimal.ZERO

    /**
     * 存储代币信息
     */
    suspend fun storeTokenInfo(
        wallet: Wallet,
        tInfo: TokenInfo,
        type: ContractType,
    ): TokenInfo? {
        val contractType = tokenRepository.determineCommonType(tInfo) ?: type
        val finalType = checkDefaultType(contractType, type)
        return tokenRepository.storeTokenInfo(wallet, tInfo, finalType)
    }

    /**
     * 直接存储代币信息
     */
    suspend fun storeTokenInfoDirect(
        wallet: Wallet,
        tInfo: TokenInfo,
        type: ContractType,
    ): TokenInfo? = tokenRepository.storeTokenInfo(wallet, tInfo, type)

    /**
     * 检查默认类型
     */
    private fun checkDefaultType(
        contractType: ContractType,
        defaultType: ContractType,
    ): ContractType =
        when (contractType) {
            ContractType.OTHER, ContractType.ERC721_UNDETERMINED -> defaultType
            else -> contractType
        }

    /**
     * 同步链余额
     */
    suspend fun syncChainBalances(
        walletAddress: String,
        updateType: TokenUpdateType,
    ): Array<Token?> =
        withContext(Dispatchers.IO) {
            val baseTokens = mutableListOf<Token?>()

            for (chainId in networkFilter) {
                var baseToken : Token? = tokenRepository.fetchToken(chainId, walletAddress, walletAddress)
                if (baseToken == null) {
                    baseToken =
                        ethereumNetworkRepository.getBlankOverrideToken(
                            ethereumNetworkRepository.getNetworkByChain(chainId),
                        )
                }

                baseToken?.setTokenWallet(walletAddress)
                var balance = baseToken?.balance

                if (updateType == TokenUpdateType.ACTIVE_SYNC) {
                    balance = tokenRepository.updateTokenBalance(walletAddress, baseToken)
                }

                if (balance != null) {
                    if (balance > BigDecimal.ZERO) {
                        baseToken?.balance = balance
                        baseTokens.add(baseToken)
                    }
                }
            }

            baseTokens.toTypedArray()
        }

    /**
     * 余额变化处理
     */
    private suspend fun onBalanceChange(
        newBalance: BigDecimal,
        token: Token,
    ) {
        val balanceChange = newBalance != token.balance

        // 代币被删除
        if (newBalance == BigDecimal.valueOf(-2)) {
            return
        }

        if (balanceChange && BuildConfig.DEBUG) {
            Timber.tag(TAG).d("Change Registered: * ${token.getFullName()}")
        }

        // 更新检查时间
        pendingTokenMap[databaseKey(token)] = System.currentTimeMillis()

        // 开启此代币链
        if (token.isEthereum() && newBalance > BigDecimal.ZERO) {
            checkChainVisibility(token)
            if (syncCount == 0) {
                completionCallback?.syncComplete(this, -1)
            }
        }

        if (token.isEthereum()) {
            checkERC20(token.tokenInfo.chainId)
        }

        checkOpenSea(token.tokenInfo.chainId)
    }

    /**
     * 检查链可见性
     */
    private fun checkChainVisibility(token: Token) {
        if (!networkFilter.contains(token.tokenInfo.chainId) &&
            EthereumNetworkRepository.hasRealValue(token.tokenInfo.chainId)
        ) {
            Timber.tag(TAG).d("Detected balance")
            networkFilter.add(token.tokenInfo.chainId)
            ethereumNetworkRepository.setFilterNetworkList(networkFilter.toTypedArray())
        }
    }

    /**
     * 检查等待中的链
     */
    private fun checkPendingChains() {
        val currentTime = System.currentTimeMillis()
        val iterator = pendingChainMap.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime > entry.value) {
                iterator.remove()
            }
        }
    }

    /**
     * 错误处理
     */
    private fun onError(throwable: Throwable) {
        Timber.tag(TAG).e(throwable, "TokensService error")
    }

    /**
     * 检查OpenSea
     */
    private suspend fun checkOpenSea(chainId: Long) {
        if (openSeaQueryJob?.isActive == true ||
            !EthereumNetworkBase.hasOpenseaAPI(chainId) ||
            !openseaService.canCheckChain(chainId)
        ) {
            return
        }

        val info = ethereumNetworkRepository.getNetworkByChain(chainId)
        if (info.chainId == transferCheckChain) return

        Timber.tag(TAG).d("Fetch from opensea: $currentAddress: ${info.shortName}")
        openSeaCheckId = info.chainId

        openSeaQueryJob =
            serviceScope.launch {
                try {
                    callOpenSeaAPI(info)
                    openSeaCheckId = 0
                } catch (e: Exception) {
                    openSeaCallError(e)
                }
            }
    }

    /**
     * OpenSea API调用错误处理
     */
    private fun openSeaCallError(error: Throwable) {
        Timber.tag(TAG).w(error, "OpenSea API call error")
        openSeaCheckId = 0
    }

    /**
     * 调用OpenSea API
     */
    private suspend fun callOpenSeaAPI(info: NetworkInfo): Boolean =
        withContext(Dispatchers.IO) {
            val wallet = Wallet(currentAddress!!)

            try {
                val tokens = openseaService.getTokens(currentAddress!!, info.chainId, info.shortName, this@TokensService)

                for (token in tokens) {
                    val checkedToken = tokenRepository.checkInterface(token, wallet)
                    val initializedToken = tokenRepository.initNFTAssets(wallet, checkedToken)
                    tokenRepository.storeTokens(wallet, arrayOf(initializedToken))
                }

                true
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error calling OpenSea API")
                false
            }
        }

    /**
     * 检查OpenSea更新是否在进行中
     */
    fun openSeaUpdateInProgress(chainId: Long): Boolean = openSeaQueryJob?.isActive == true && openSeaCheckId == chainId

    /**
     * 检查ERC20
     */
    private suspend fun checkERC20(chainId: Long) {
        if (erc20CheckJob?.isActive == true) return

        erc20CheckJob =
            serviceScope.launch {
                try {
                    val updated = tickerService.syncERC20Tickers(chainId, getAllERC20(chainId))
                    finishCheckChain(updated)
                } catch (e: Exception) {
                    onERC20Error(e)
                }
            }
    }

    /**
     * 获取所有ERC20代币
     */
    private suspend fun getAllERC20(chainId: Long): List<TokenCardMeta?>? {
        val tokenList: Array<TokenCardMeta?>? =
            tokenRepository.fetchTokenMetasForUpdate(
                Wallet(currentAddress!!),
                listOf(chainId),
            )

        return tokenList?.filter { tcm ->
            tcm?.type == ContractType.ERC20 && tcm.isEnabled
        }
    }

    /**
     * 完成检查链
     */
    private fun finishCheckChain(updated: Int) {
        erc20CheckJob = null
    }

    /**
     * ERC20错误处理
     */
    private fun onERC20Error(throwable: Throwable) {
        erc20CheckJob = null
        Timber.tag(TAG).e(throwable, "ERC20 check error")
    }

    /**
     * 更新价格信息
     */
    fun updateTickers() {
        tickerService.updateTickers()
    }

    /**
     * 获取Realm实例
     */
    fun getRealmInstance(wallet: Wallet): Realm? = tokenRepository.getRealmInstance(wallet)

    /**
     * 获取钱包Realm实例
     */
    fun getWalletRealmInstance(): Realm? =
        currentAddress?.let {
            tokenRepository.getRealmInstance(Wallet(it))
        }

    /**
     * 标记链为等待状态
     * 当创建交易时调用
     */
    fun markChainPending(chainId: Long) {
        pendingChainMap[chainId] = System.currentTimeMillis() + PENDING_TIME_LIMIT
    }

    /**
     * 获取法币价值对
     */
    suspend fun getFiatValuePair(): Pair<Double?, Double?>? = tokenRepository.getTotalValue(currentAddress!!, EthereumNetworkBase.getAllMainNetworks())

    /**
     * 获取价格更新列表
     */
    suspend fun getTickerUpdateList(): List<String?>? = tokenRepository.getTickerUpdateList(networkFilter)

    /**
     * 转换为USD
     */
    fun convertToUSD(localFiatValue: Double): Double = localFiatValue / tickerService.getCurrentConversionRate()

    /**
     * 获取法币价值对
     */
    fun getFiatValuePair(
        chainId: Long,
        address: String,
    ): Pair<Double, Double> {
        val token = getToken(chainId, address)
        val ticker = token?.let { getTokenTicker(it) }

        return if (ticker != null) {
            Pair(ticker.price.toDouble(), ticker.percentChange24h.toDouble())
        } else {
            Pair(0.0, 0.0)
        }
    }

    /**
     * 获取代币法币价值
     */
    fun getTokenFiatValue(
        chainId: Long,
        address: String,
    ): Double {
        val token = getToken(chainId, address) ?: return 0.0
        val ticker = getTokenTicker(token) ?: return 0.0

        val correctedBalance = token.getCorrectedBalance(18)
        val fiatValue =
            correctedBalance
                .multiply(BigDecimal(ticker.price))
                .setScale(18, RoundingMode.DOWN)

        return fiatValue.toDouble()
    }

    /**
     * 获取下一个需要余额更新的代币
     * 代币更新启发式算法 - 计算哪个代币应该下次更新
     */
    suspend fun getNextInBalanceUpdateQueue(): Token? {
        val tokenList = buildUpdateMap()
        var highestWeighting = 0f
        val currentTime = System.currentTimeMillis()

        // 检查待存储的代币
        val storeToken = pendingBaseCheck() ?: tokenStoreList.poll()
        if (storeToken != null) return storeToken

        var highestToken: TokenCardMeta? = null

        for (check in tokenList) {
            val lastCheckDiff = currentTime - check.lastUpdate
            val lastUpdateDiff = if (check.lastTxUpdate > 0) currentTime - check.lastTxUpdate else 0
            var weighting = check.calculateBalanceUpdateWeight()

            // 过滤条件
            if ((!check.isEnabled || check.isNFT()) && !isSynced()) continue
            if (!isSynced() && check.lastUpdate > syncStart) continue
            if (!appHasFocus && (!check.isEthereum() && !isFocusToken(check))) continue

            var updateFactor = weighting * lastCheckDiff.toFloat() * (if (check.isEnabled) 1f else 0.25f)
            var cutoffCheck = check.calculateUpdateFrequency()

            // 特殊情况处理
            if (!check.isEthereum() && lastUpdateDiff > DateUtils.DAY_IN_MILLIS) {
                cutoffCheck = 120 * DateUtils.SECOND_IN_MILLIS
                updateFactor *= 0.5f
            }

            if (isFocusToken(check)) {
                updateFactor = 3.0f * lastCheckDiff.toFloat()
                cutoffCheck = 15 * DateUtils.SECOND_IN_MILLIS
            } else if (check.isEthereum() && pendingChainMap.containsKey(check.chain)) {
                cutoffCheck = 15 * DateUtils.SECOND_IN_MILLIS
                updateFactor = 4.0f * lastCheckDiff.toFloat()
            } else if (check.isEthereum()) {
                cutoffCheck = 20 * DateUtils.SECOND_IN_MILLIS
            } else if (focusToken != null) {
                updateFactor = 0.1f * lastCheckDiff.toFloat()
                cutoffCheck = 60 * DateUtils.SECOND_IN_MILLIS
            }

            if (updateFactor > highestWeighting && lastCheckDiff > cutoffCheck) {
                highestWeighting = updateFactor
                highestToken = check
            }
        }

        return if (highestToken != null) {
            pendingTokenMap[databaseKey(highestToken.chain, highestToken.address)] = System.currentTimeMillis()
            getToken(highestToken.chain, highestToken.address)
        } else {
            null
        }
    }

    /**
     * 等待基础检查
     */
    private fun pendingBaseCheck(): Token? {
        val chainId = baseTokenCheck.poll()

        return if (chainId != null) {
            Timber.tag(TAG).d("Base Token Check: ${ethereumNetworkRepository.getNetworkByChain(chainId).name}")
            createCurrencyToken(ethereumNetworkRepository.getNetworkByChain(chainId), Wallet(currentAddress!!))
        } else {
            if (syncCount == 0) {
                syncCount = 1
            }
            null
        }
    }

    /**
     * 初始化余额检查所需的过滤器队列。
     *
     * 当用户尚未自定义网络过滤时，根据当前钱包持有的代币情况自动构建
     * 基础链检查列表，保证首次同步时能够遍历所有已启用网络。
     */
    private fun setupFilters() {
        baseTokenCheck.clear()

        if (!ethereumNetworkRepository.hasSetNetworkFilters()) {
            blankFiltersForZeroBalance()
            val networks = ethereumNetworkRepository.getAvailableNetworkList()
            for (info in networks) {
                baseTokenCheck.add(info.chainId)
            }
        }
    }

    /**
     * 为零余额设置空过滤器
     */
    private fun blankFiltersForZeroBalance() {
        networkFilter.clear()
        val networks = ethereumNetworkRepository.getAvailableNetworkList()

        if (!ethereumNetworkRepository.hasSetNetworkFilters()) {
            for (network in networks) {
                val token = getToken(network.chainId, currentAddress!!)
                if (token != null && token.balance > BigDecimal.ZERO) {
                    networkFilter.add(network.chainId)
                }
            }
        }

        for (lockedChain in CustomViewSettings.lockedChains) {
            if (!networkFilter.contains(lockedChain)) {
                networkFilter.add(lockedChain)
            }
        }

        if (networkFilter.isEmpty()) {
            networkFilter.add(ethereumNetworkRepository.getDefaultNetwork())
        }

        ethereumNetworkRepository.setFilterNetworkList(networkFilter.toTypedArray())
    }

    /**
     * 添加代币
     */
    suspend fun addToken(
        info: TokenInfo,
        walletAddress: String,
    ): Token {
        val contractType = tokenRepository.determineCommonType(info) ?: ContractType.NOT_SET
        val token =
            tokenFactory.createToken(
                info,
                contractType,
                ethereumNetworkRepository.getNetworkByChain(info.chainId).shortName,
            )

        token.setTokenWallet(walletAddress)
        val newBalance = tokenRepository.updateTokenBalance(walletAddress, token)
        if (newBalance != null) {
            token.balance = newBalance
        }

        return token
    }

    /**
     * 添加代币列表
     */
    fun addTokens(tokenList: List<Token>) {
        for (token in tokenList) {
            tokenStoreList.addFirst(token)
        }
    }

    /**
     * 注入预置的锁定代币。
     *
     * 某些代币需要在钱包初始化时强制展示，此处会异步创建并启用它们，
     * 以确保用户切换钱包后仍能看到这些关键资产。
     */
    private fun addLockedTokens() {
        val wallet = currentAddress!!

        serviceScope.launch {
            try {
                for (info in CustomViewSettings.lockedTokens) {
                    val token = addToken(info, wallet)
                    enableToken(wallet, token.getContractAddress())
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error adding locked tokens")
            }
        }
    }

    /**
     * 启用代币
     */
    suspend fun enableToken(
        walletAddr: String,
        cAddr: ContractAddress,
    ) {
        withContext(Dispatchers.IO) {
            val wallet = Wallet(walletAddr)
            tokenRepository.setEnable(wallet, cAddr, true)
            tokenRepository.setVisibilityChanged(wallet, cAddr)
        }
    }

    /**
     * 获取Ticker Realm实例
     */
    fun getTickerRealmInstance(): Realm? = tokenRepository.tickerRealmInstance

    /**
     * 钱包获得焦点
     */
    fun walletInFocus() {
        appHasFocus = true
        Timber.tag(TAG).d("Wallet in focus")
    }

    /**
     * 钱包失去焦点
     */
    fun walletOutOfFocus() {
        appHasFocus = false
        Timber.tag(TAG).d("Wallet out of focus")
    }

    /**
     * 跟踪Gas速度使用情况
     */
    fun track(gasSpeed: String) {
        val analyticsProperties =
            AnalyticsProperties().apply {
                put(Analytics.PROPS_GAS_SPEED, gasSpeed)
            }
        analyticsService.track(Analytics.Action.USE_GAS_WIDGET.getValue(), analyticsProperties)
    }

    /**
     * 获取代币或基础货币
     */
    fun getTokenOrBase(
        chainId: Long,
        address: String,
    ): Token? {
        var token = getToken(chainId, address)

        if (token == null) {
            token = getToken(chainId, currentAddress) // 使用基础货币
        }

        if (token == null) {
            // 如果需要，创建基础代币
            token =
                ethereumNetworkRepository.getBlankOverrideToken(
                    ethereumNetworkRepository.getNetworkByChain(chainId),
                )
        }

        return token
    }

    /**
     * 更新资产
     */
    fun updateAssets(
        token: Token,
        additions: List<BigInteger>,
        removals: List<BigInteger>,
    ) {
        tokenRepository.updateAssets(currentAddress, token, additions, removals)
    }

    /**
     * 存储资产
     */
    fun storeAsset(
        token: Token?,
        tokenId: BigInteger,
        asset: NFTAsset,
    ) {
        tokenRepository.storeAsset(currentAddress, token, tokenId, asset)
    }

    /**
     * 是否为链代币
     */
    fun isChainToken(
        chainId: Long,
        tokenAddress: String,
    ): Boolean = ethereumNetworkRepository.isChainContract(chainId, tokenAddress)

    /**
     * 是否有链代币
     */
    fun hasChainToken(chainId: Long): Boolean = EthereumNetworkRepository.getChainOverrideAddress(chainId).isNotEmpty()

    /**
     * 获取服务代币
     */
    fun getServiceToken(chainId: Long): Token? =
        if (hasChainToken(chainId)) {
            getToken(chainId, EthereumNetworkRepository.getChainOverrideAddress(chainId))
        } else {
            getToken(chainId, currentAddress!!)
        }

    /**
     * 获取代币的后备URL
     */
    fun getFallbackUrlForToken(token: Token): String? {
        var url = tokenRepository.getTokenImageUrl(token.tokenInfo.chainId, token.getAddress())
        if (TextUtils.isEmpty(url)) {
            url = Utils.getTWTokenImageUrl(token.tokenInfo.chainId, token.getAddress())
        }
        return url
    }

    /**
     * 正在检查链
     */
    fun checkingChain(chainId: Long) {
        transferCheckChain = chainId
    }

    /**
     * 添加余额检查
     */
    fun addBalanceCheck(token: Token) {
        for (existingToken in tokenStoreList) {
            if (existingToken == token) return
        }
        tokenStoreList.add(token)
    }

    /**
     * 创建货币代币
     */
    private fun createCurrencyToken(
        network: NetworkInfo,
        wallet: Wallet,
    ): Token {
        val tokenInfo =
            TokenInfo(
                wallet.address,
                network.name,
                network.symbol,
                18,
                true,
                network.chainId,
            )

        val balance = BigDecimal.ZERO
        val token = Token(tokenInfo, balance, 0, network.shortName, ContractType.ETHEREUM)

        token.setTokenWallet(wallet.address)
        token.setIsEthereum()
        token.pendingBalance = balance

        return token
    }

    /**
     * 是否已同步
     */
    fun isSynced(): Boolean = syncTimer == 0L

    /**
     * 开始钱包同步
     */
    fun startWalletSync(cb: ServiceSyncCallback): Boolean {
        setCompletionCallback(cb, 0)
        return true
    }

    /**
     * 设置完成回调
     */
    fun setCompletionCallback(
        cb: ServiceSyncCallback,
        sync: Int,
    ) {
        syncCount = sync
        completionCallback = cb
        syncTimer = System.currentTimeMillis()

        if (sync > 0) {
            baseTokenCheck.clear()
            networkFilter.clear()

            val networks = ethereumNetworkRepository.getAvailableNetworkList()
            for (info in networks) {
                if (info.hasRealValue()) {
                    networkFilter.add(info.chainId)
                    baseTokenCheck.add(info.chainId)
                }
            }
        }
    }

    /**
     * 获取代币组
     */
    fun getTokenGroup(token: Token?): TokenGroup? =
        if (token != null) {
            tokenRepository.getTokenGroup(token.tokenInfo.chainId, token.tokenInfo.address, token.getInterfaceSpec())
        } else {
            TokenGroup.ASSET
        }

    /**
     * 是否有锁定的Gas
     */
    fun hasLockedGas(chainId: Long): Boolean = ethereumNetworkRepository.hasLockedGas(chainId)

    /**
     * 删除代币
     */
    suspend fun deleteTokens(metasToDelete: List<TokenCardMeta>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                tokenRepository.deleteRealmTokens(Wallet(currentAddress!!), metasToDelete)
                for (tcm in metasToDelete) {
                    pendingTokenMap.remove(databaseKey(tcm.chain, tcm.address))
                }
                true
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error deleting tokens")
                false
            }
        }

    /**
     * 获取代币（带钱包地址）
     */
    fun getToken(
        walletAddress: String?,
        chainId: Long,
        tokenAddress: String,
    ): Token? {
        val addr = walletAddress ?: currentAddress

        if (TextUtils.isEmpty(addr) || TextUtils.isEmpty(tokenAddress)) return null
        return tokenRepository.fetchToken(chainId, addr!!, tokenAddress.lowercase())
    }

    /**
     * 获取证明
     */
    fun getAttestation(
        chainId: Long,
        addr: String,
        attnId: String,
    ): Token? = tokenRepository.fetchAttestation(chainId, currentAddress!!, addr.lowercase(), attnId)

    /**
     * 获取证明列表
     */
    fun getAttestations(
        chainId: Long,
        address: String,
    ): List<Token?>? = tokenRepository.fetchAttestations(chainId, currentAddress!!, address)

    /**
     * 是否有焦点
     */
    fun isOnFocus(): Boolean = appHasFocus

    /**
     * 服务销毁时清理资源
     */
    fun destroy() {
        serviceScope.cancel()
        Timber.tag(TAG).d("TokensService destroyed")
    }
}
