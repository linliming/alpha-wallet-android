package com.alphawallet.app.service

import android.text.TextUtils
import android.text.format.DateUtils
import com.alphawallet.app.entity.ticker.TNDiscoveryTicker
import com.alphawallet.app.entity.tokendata.TokenTicker
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.entity.tokenscript.TokenscriptFunction.Companion.ZERO_ADDRESS
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.repository.KeyProvider
import com.alphawallet.app.repository.KeyProviderFactory
import com.alphawallet.app.repository.PreferenceRepositoryType
import com.alphawallet.app.repository.TokenLocalSource
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.app.util.BalanceUtils
import com.alphawallet.ethereum.EthereumNetworkBase.ARBITRUM_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.AURORA_MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.AVALANCHE_ID
import com.alphawallet.ethereum.EthereumNetworkBase.BASE_MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.BINANCE_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.CLASSIC_ID
import com.alphawallet.ethereum.EthereumNetworkBase.CRONOS_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.FANTOM_ID
import com.alphawallet.ethereum.EthereumNetworkBase.GNOSIS_ID
import com.alphawallet.ethereum.EthereumNetworkBase.HOLESKY_ID
import com.alphawallet.ethereum.EthereumNetworkBase.IOTEX_MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.KLAYTN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.LINEA_ID
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.MANTLE_MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.MILKOMEDA_C1_ID
import com.alphawallet.ethereum.EthereumNetworkBase.OKX_ID
import com.alphawallet.ethereum.EthereumNetworkBase.OPTIMISTIC_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_ID
import com.alphawallet.ethereum.EthereumNetworkBase.ROOTSTOCK_MAINNET_ID
import com.alphawallet.token.entity.ContractAddress
import com.alphawallet.token.entity.EthereumReadBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.utils.Numeric
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TickerService - 代币价格服务类
 *
 * 这是AlphaWallet中负责处理代币价格数据的核心服务，使用协程替代RxJava。
 * 主要功能包括：
 * 1. 代币价格获取与更新
 * 2. 货币汇率转换
 * 3. 多链价格数据管理
 * 4. 价格数据缓存与存储
 * 5. 第三方API集成（CoinGecko、DEX.GURU等）
 * 6. 智能合约价格预言机集成
 *
 * 使用协程提供更好的异步处理性能和可读性。
 *
 * @param httpClient HTTP客户端
 * @param sharedPrefs 偏好设置仓库
 * @param localSource 本地数据源
 *
 * @author AlphaWallet Team
 * @since 2024
 */
@Singleton
class TickerService
    @Inject
    constructor(
        private val httpClient: OkHttpClient,
        private val sharedPrefs: PreferenceRepositoryType,
        private val localSource: TokenLocalSource,
    ) {
        companion object {
            private const val UPDATE_TICKER_CYCLE = 5L // 5分钟更新周期
            private const val MEDIANIZER = "0x729D19f657BD0614b4985Cf1D82531c67569197B"
            private const val MARKET_ORACLE_CONTRACT = "0x40805417CD347dB17829725C74b8E5990dC251d8"
            private const val CONTRACT_ADDR = "[CONTRACT_ADDR]"
            private const val CHAIN_IDS = "[CHAIN_ID]"
            private const val CURRENCY_TOKEN = "[CURRENCY]"
            private const val COINGECKO_CHAIN_CALL = "https://api.coingecko.com/api/v3/simple/price?ids=$CHAIN_IDS&vs_currencies=$CURRENCY_TOKEN&include_24hr_change=true"
            private const val COINGECKO_API = "https://api.coingecko.com/api/v3/simple/token_price/$CHAIN_IDS?contract_addresses=$CONTRACT_ADDR&vs_currencies=$CURRENCY_TOKEN&include_24hr_change=true"
            private const val TOKEN_DISCOVERY_API = "https://api.token-discovery.tokenscript.org/get-raw-token-price?blockchain=evm&smartContract=$CONTRACT_ADDR&chain=$CHAIN_IDS"
            private const val COINGECKO_MAX_FETCH = 10
            private const val DEXGURU_API = "https://api.dex.guru/v1/tokens/$CONTRACT_ADDR-$CHAIN_IDS"
            private const val CURRENCY_CONV = "currency"
            private const val ALLOW_UNVERIFIED_TICKERS = false // 允许来自DEX.GURU的未验证ticker，不推荐
            const val TICKER_TIMEOUT = DateUtils.WEEK_IN_MILLIS // 一周内未见的ticker将被删除
            const val TICKER_STALE_TIMEOUT = 30 * DateUtils.MINUTE_IN_MILLIS // 如果AlphaWallet市场预言机未更新，使用市场API

            // CoinGecko链ID到API名称的映射
            // 从这里更新: https://api.coingecko.com/api/v3/asset_platforms
            val coinGeckoChainIdToAPIName =
                mapOf(
                    MAINNET_ID to "ethereum",
                    GNOSIS_ID to "xdai",
                    BINANCE_MAIN_ID to "binance-smart-chain",
                    POLYGON_ID to "polygon-pos",
                    CLASSIC_ID to "ethereum-classic",
                    FANTOM_ID to "fantom",
                    AVALANCHE_ID to "avalanche",
                    ARBITRUM_MAIN_ID to "arbitrum-one",
                    OKX_ID to "okex-chain",
                    1666600000L to "harmony-shard-0",
                    321L to "kucoin-community-chain",
                    88L to "tomochain",
                    42220L to "celo",
                    KLAYTN_ID to "klay-token",
                    IOTEX_MAINNET_ID to "iotex",
                    AURORA_MAINNET_ID to "aurora",
                    MILKOMEDA_C1_ID to "cardano",
                    CRONOS_MAIN_ID to "cronos",
                    ROOTSTOCK_MAINNET_ID to "rootstock",
                    LINEA_ID to "linea",
                    BASE_MAINNET_ID to "base",
                    MANTLE_MAINNET_ID to "mantle",
                )

            // 暂时不使用Dexguru，除非获得API密钥
            private val dexGuruChainIdToAPISymbol = mapOf<Long, String>()

            // 从这里更新: https://api.coingecko.com/api/v3/coins/list
            // 如果ticker与ethereum挂钩（L2），则在这里使用'ethereum'
            val chainPairs =
                mapOf(
                    MAINNET_ID to "ethereum",
                    CLASSIC_ID to "ethereum-classic",
                    GNOSIS_ID to "xdai",
                    BINANCE_MAIN_ID to "binancecoin",
                    AVALANCHE_ID to "avalanche-2",
                    FANTOM_ID to "fantom",
                    POLYGON_ID to "matic-network",
                    ARBITRUM_MAIN_ID to "ethereum",
                    OPTIMISTIC_MAIN_ID to "ethereum",
                    KLAYTN_ID to "klay-token",
                    IOTEX_MAINNET_ID to "iotex",
                    AURORA_MAINNET_ID to "aurora",
                    MILKOMEDA_C1_ID to "cardano",
                    CRONOS_MAIN_ID to "crypto-com-chain",
                    OKX_ID to "okb",
                    ROOTSTOCK_MAINNET_ID to "rootstock",
                    LINEA_ID to "ethereum",
                    BASE_MAINNET_ID to "base",
                    MANTLE_MAINNET_ID to "mantle",
                )
            /**
             * 获取不带符号的货币字符串
             */
            fun getCurrencyWithoutSymbol(price: Double): String = BalanceUtils.genCurrencyString(price, "")

            /**
             * 获取当前ISO货币字符串，如EUR、AUD等
             */
            fun getCurrencySymbolTxt(): String = currentCurrencySymbolTxt

            @Volatile private var currentCurrencySymbolTxt: String = ""
            @Volatile private var currentCurrencySymbol: String = ""
            /**
             * 以“金额 + 货币代码”形式返回价格字符串，适合列表等展示场景。
             */
            fun getFullCurrencyString(price: Double): String = getCurrencyString(price) + " " + currentCurrencySymbolTxt

            /**
             * 返回当前货币的符号，例如 ¥、$。
             */
            fun getCurrencySymbol(): String = currentCurrencySymbol

            /**
             * 根据当前本地化设置，格式化并返回带货币符号的金额字符串。
             */
            fun getCurrencyString(price: Double): String = BalanceUtils.genCurrencyString(price, currentCurrencySymbol)
        }

        private val keyProvider: KeyProvider = KeyProviderFactory.get()
        private val ethTickers = ConcurrentHashMap<Long?, TokenTicker?>()
        private var currentConversionRate = 0.0


        private val tokenCheckQueue = ConcurrentLinkedDeque<TokenCardMeta>()
        private val secondaryCheckQueue = ConcurrentLinkedDeque<ContractAddress>()
        private val dexGuruQuery = ConcurrentHashMap<String, TokenCardMeta>()

        private var lastTickerUpdate = 0L
        private var keyCycle = 0

        // 协程作业管理
        private var tickerUpdateJob: Job? = null
        private var erc20TickerCheckJob: Job? = null
        private var mainTickerUpdateJob: Job? = null

        // 状态流
        private val _tickerUpdateState = MutableStateFlow<TickerUpdateState>(TickerUpdateState.Idle)
        val tickerUpdateState: StateFlow<TickerUpdateState> = _tickerUpdateState

        init {
            resetTickerUpdate()
            initCurrency()
            lastTickerUpdate = 0
        }

        /**
         * 启动价格更新循环。
         *
         * 该方法会按照固定间隔触发 {@link #tickerUpdate()}，持续刷新缓存中的
         * 法币汇率与代币行情数据，并在循环启动前确保没有残留的更新任务。
         */
        fun updateTickers() {
            if (mainTickerUpdateJob?.isActive == true &&
                System.currentTimeMillis() > (lastTickerUpdate + DateUtils.MINUTE_IN_MILLIS)
            ) {
                return // 如果更新正在进行中，则不更新
            }

            tickerUpdateJob?.cancel()
            sharedPrefs.commit()

            tickerUpdateJob =
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    while (isActive) {
                        tickerUpdate()
                        delay(UPDATE_TICKER_CYCLE * 60 * 1000) // 转换为毫秒
                    }
                }
        }

        /**
         * 执行一次完整的价格刷新流程。
         *
         * 该流程会依次完成：
         * 1. 计算当前法币汇率；
         * 2. 从市场预言机读取主链行情；
         * 3. 对缺失的链调用第三方价格接口；
         * 4. 触发本地代币价格检查与通知。
         * 整体运行在 IO 线程，完成或失败时会切回主线程反馈结果。
         */
        private suspend fun tickerUpdate() {
            mainTickerUpdateJob =
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        _tickerUpdateState.value = TickerUpdateState.Updating

                        val conversionRate = updateCurrencyConversion()
                        val tickerCount = updateTickersFromOracle(conversionRate)
                        val finalCount = fetchTickersSeparatelyIfRequired(tickerCount)
                        val result = checkTickers(finalCount)

                        withContext(Dispatchers.Main) {
                            tickersUpdated(result)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            onTickersError(e)
                        }
                    }
                }
        }

        /**
         * 价格更新完成回调。
         *
         * 更新成功后需要记录完成时间、清理任务状态，并通知外部观察者本次
         * 共更新了多少条行情数据。
         */
        private fun tickersUpdated(tickerCount: Int) {
            Timber.d("价格更新完成: %s", tickerCount)
            mainTickerUpdateJob = null
            lastTickerUpdate = System.currentTimeMillis()
            _tickerUpdateState.value = TickerUpdateState.Completed(tickerCount)
        }

        /**
         * 刷新当前所选法币的美元兑换率。
         *
         * 该方法会使用首选的货币代码向远端接口请求美元兑目标货币的价格，
         * 若请求失败则尝试返回本地缓存中的旧数据。
         */
        suspend fun updateCurrencyConversion(): Double =
            withContext(Dispatchers.IO) {
                initCurrency()
                val rate = convertPair("USD", currentCurrencySymbolTxt)
                storeCurrentRate(rate)
            }

        /**
         * 将最新的汇率写入本地数据库。
         *
         * 当远端返回的汇率为 0 时，表示请求失败，此时优先返回缓存中的旧值；
         * 否则更新到 Realm 并返回最新汇率。
         */
        private fun storeCurrentRate(rate: Double): Double =
            if (rate == 0.0) {
                val tt = localSource.getCurrentTicker(TokensRealmSource.databaseKey(0, CURRENCY_CONV))
                if (tt != null) {
                    tt.price.toDouble()
                } else {
                    0.0
                }
            } else {
                val currencyTicker =
                    TokenTicker(
                        rate.toString(),
                        "0",
                        currentCurrencySymbolTxt,
                        null,
                        System.currentTimeMillis(),
                    )
                val tickerMap: MutableMap<String?, TokenTicker?> = mutableMapOf(CURRENCY_CONV to currencyTicker)
                localSource.updateERC20Tickers(0, tickerMap)
                rate
            }

        /**
         * 判断是否需要补充获取链行情。
         *
         * 当主流程没有覆盖全部链时，回退到 CoinGecko 单独请求缺失链的价格，
         * 否则直接返回主流程的统计数量。
         */
        private suspend fun fetchTickersSeparatelyIfRequired(tickerCount: Int): Int =
            withContext(Dispatchers.IO) {
                if (receivedAllChainPairs()) {
                    tickerCount
                } else {
                    fetchCoinGeckoChainPrices()
                }
            }

        /**
         * 向 CoinGecko 请求各主链的行情数据。
         *
         * 仅针对尚未成功获取行情的链发起网络请求，成功后会更新链级缓存并尝试
         * 处理与基础链挂钩的代币价格。
         */
        private suspend fun fetchCoinGeckoChainPrices(): Int =
            withContext(Dispatchers.IO) {
                var tickers = 0
                val request =
                    Request
                        .Builder()
                        .url(getCoinGeckoChainCall())
                        .get()
                        .build()

                try {
                    httpClient.newCall(request).execute().use { response ->
                        if (response.code / 200 == 1) {
                            val result = response.body?.string()
                            val data = JSONObject(result)

                            for ((chainId, chainSymbol) in chainPairs) {
                                if (!data.has(chainSymbol)) continue
                                val tickerData = data.getJSONObject(chainSymbol)
                                val tTicker = decodeCoinGeckoTicker(tickerData)
                                ethTickers[chainId] = tTicker
                                checkPeggedTickers(chainId, tTicker)
                                tickers++
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                }

                tickers
            }

        /**
         * 调用市场预言机同步主链价格。
         *
         * 通过智能合约读取批量行情，若数据未过期则写入本地缓存，并返回
         * 成功解析的行情条目数量。
         */
        private suspend fun updateTickersFromOracle(conversionRate: Double): Int =
            withContext(Dispatchers.IO) {
                resetTickerUpdate()
                currentConversionRate = conversionRate

                var tickerSize = 0
                val web3j = TokenRepository.getWeb3jService(HOLESKY_ID)
                val function = getTickers()
                val responseValue = callSmartContractFunction(web3j, function, MARKET_ORACLE_CONTRACT)
                val responseValues = FunctionReturnDecoder.decode(responseValue, function.outputParameters)

                if (responseValues.isNotEmpty()) {
                    val T = responseValues[0]

                    @Suppress("UNCHECKED_CAST")
                    val values = T.value as List<Uint256>
                    val tickerUpdateTime = values[0].value.toLong() * 1000L

                    if ((System.currentTimeMillis() - tickerUpdateTime) < TICKER_STALE_TIMEOUT) {
                        for (i in 1 until values.size) {
                            val tickerInfo = values[i].value
                            addToTokenTickers(tickerInfo, tickerUpdateTime)
                            tickerSize++
                        }
                    }
                }

                tickerSize
            }

        /**
         * 同步指定链上的 ERC20 代币价格。
         *
         * 仅对具有实际价值的网络执行同步，过滤余额为 0 的代币后交由后台任务
         * 处理，并在同步完成后自动继续下一条链。
         */
        suspend fun syncERC20Tickers(
            chainId: Long,
            erc20Tokens: List<TokenCardMeta?>?,
        ): Int =
            withContext(Dispatchers.IO) {
                if (!EthereumNetworkRepository.hasRealValue(chainId) || erc20Tokens?.isEmpty() == true) {
                    return@withContext 0
                }

                val currentTickerMap: Map<String?, Long?>? = localSource.getTickerTimeMap(chainId, erc20Tokens)

                // 确定是否添加到检查队列
                if (erc20Tokens != null) {
                    for (tcm in erc20Tokens) {
                        if (currentTickerMap != null) {
                            if (!currentTickerMap.containsKey(tcm?.address) && !alreadyInQueue(tcm)) {
                                tokenCheckQueue.addLast(tcm)
                            }
                        }
                    }
                }

                if (tokenCheckQueue.isEmpty()) {
                    0
                } else {
                    beginTickerCheck()
                }
            }

        /**
         * 开始价格检查
         */
        private fun beginTickerCheck(): Int {
            if (tokenCheckQueue.isNotEmpty() && (erc20TickerCheckJob?.isActive != true)) {
                erc20TickerCheckJob =
                    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                        while (isActive) {
                            checkTokenDiscoveryTickers()
                            delay(2000) // 2秒间隔
                        }
                    }
            }
            return tokenCheckQueue.size
        }

        /**
         * 检查代币发现价格
         */
        private suspend fun checkTokenDiscoveryTickers() {
            val thisTCM = tokenCheckQueue.pollFirst()
            if (thisTCM == null) {
                // 终止检查周期
                erc20TickerCheckJob?.cancel()
                return
            }

            try {
                val tickers: MutableMap<String?, TokenTicker?> = fetchTickers(thisTCM)
                if (tickers.isNotEmpty()) {
                    localSource.updateERC20Tickers(thisTCM.chain, tickers)
                }
            } catch (e: Exception) {
                Timber.e(e, "检查代币发现价格时发生错误")
            }
        }

        /**
         * 获取价格数据
         */
        private suspend fun fetchTickers(tcm: TokenCardMeta): MutableMap<String?, TokenTicker?> =
            withContext(Dispatchers.IO) {
                val apiChainName = coinGeckoChainIdToAPIName[tcm.chain]
                val tickersMap = mutableMapOf<String?, TokenTicker?>()

                val buildRequestTN =
                    Request
                        .Builder()
                        .url(
                            TOKEN_DISCOVERY_API
                                .replace(CHAIN_IDS, apiChainName ?: "")
                                .replace(CONTRACT_ADDR, tcm.getContractAddress().address),
                        ).get()

                try {
                    httpClient.newCall(buildRequestTN.build()).execute().use { response ->
                        val code = response.code
                        if (code / 100 != 2) {
                            return@withContext tickersMap
                        }

                        val result = JSONArray(response.body?.string())
                        TNDiscoveryTicker.toTokenTickers(tickersMap, result, currentCurrencySymbolTxt, currentConversionRate)
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                }

                tickersMap
            }

        /**
         * 检查是否已在队列中
         */
        private fun alreadyInQueue(tcm: TokenCardMeta?): Boolean {
            for (thisTcm in tokenCheckQueue) {
                if (tcm != null) {
                    if (tcm.tokenId.equals(thisTcm.tokenId, ignoreCase = true)) {
                        return true
                    }
                }
            }
            return dexGuruQuery.containsKey(tcm?.tokenId)
        }

        /**
         * 检查挂钩价格
         */
        private fun checkPeggedTickers(
            chainId: Long,
            ticker: TokenTicker,
        ) {
            if (chainId == MAINNET_ID) {
                for ((entryChainId, entryValue) in chainPairs) {
                    if (entryValue == "ethereum") {
                        ethTickers[entryChainId] = ticker
                    }
                }
            }
        }

        /**
         * 添加到代币价格映射
         */
        private fun addToTokenTickers(
            tickerInfo: BigInteger,
            tickerTime: Long,
        ) {
            try {
                val tickerData = Numeric.toBytesPadded(tickerInfo, 32)
                val buffer = ByteArrayInputStream(tickerData)
                val ds = EthereumReadBuffer(buffer)

                val chainId = ds.readBI(4)
                val changeVal = ds.readInt()
                val correctedPrice = ds.readBI(24)
                ds.close()

                val changeValue = BigDecimal(changeVal).movePointLeft(3)
                val priceValue = BigDecimal(correctedPrice).movePointLeft(12)

                val price = priceValue.toDouble()

                val tTicker =
                    TokenTicker(
                        (price * currentConversionRate).toString(),
                        changeValue.setScale(3, RoundingMode.DOWN).toString(),
                        currentCurrencySymbolTxt,
                        "",
                        tickerTime,
                    )

                ethTickers[chainId.toLong()] = tTicker
                checkPeggedTickers(chainId.toLong(), tTicker)
            } catch (e: Exception) {
                Timber.e(e, "添加代币价格时发生错误")
            }
        }

        /**
         * 检查价格数据
         */
        private fun checkTickers(tickerSize: Int): Int {
            Timber.d("收到价格数据: %s", tickerSize)
            localSource.updateEthTickers(ethTickers)
            return tickerSize
        }

        /**
         * 获取以太坊价格
         */
        fun getEthTicker(chainId: Long): TokenTicker? = ethTickers[chainId]

        /**
         * 解码CoinGecko价格数据
         */
        private fun decodeCoinGeckoTicker(eth: JSONObject): TokenTicker =
            try {
                var changeValue = BigDecimal.ZERO
                var fiatPrice = 0.0
                var fiatChangeStr = "0.0"

                if (eth.has(currentCurrencySymbolTxt.lowercase())) {
                    fiatPrice = eth.getDouble(currentCurrencySymbolTxt.lowercase())
                    fiatChangeStr = eth.getString("${currentCurrencySymbolTxt.lowercase()}_24h_change")
                } else {
                    fiatPrice = eth.getDouble("usd") * currentConversionRate
                    fiatChangeStr = eth.getString("usd_24h_change")
                }

                if (!TextUtils.isEmpty(fiatChangeStr) && Character.isDigit(fiatChangeStr[0])) {
                    changeValue = BigDecimal(eth.getDouble("${currentCurrencySymbolTxt.lowercase()}_24h_change"))
                }

                TokenTicker(
                    fiatPrice.toString(),
                    changeValue.setScale(3, RoundingMode.DOWN).toString(),
                    currentCurrencySymbolTxt,
                    "",
                    System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                Timber.e(e, "解码CoinGecko价格数据时发生错误")
                TokenTicker()
            }

        /**
         * 查询两种法币之间的即时汇率。
         *
         * 通过 GrandTrunk 汇率服务获取最新价格，若两种货币相同则直接返回 1，
         * 失败时返回 0 供上层决定是否回退到缓存值。
         */
        suspend fun convertPair(
            currency1: String,
            currency2: String,
        ): Double =
            withContext(Dispatchers.IO) {
                if (currency1 == null || currency2 == null || currency1 == currency2) {
                    return@withContext 1.0
                }

                val conversionURL = "http://currencies.apps.grandtrunk.net/getlatest/$currency1/$currency2"
                var rate = 0.0

                val request =
                    Request
                        .Builder()
                        .url(conversionURL)
                        .addHeader("Connection", "close")
                        .get()
                        .build()

                try {
                    httpClient.newCall(request).execute().use { response ->
                        val resultCode = response.code
                        if ((resultCode / 100) == 2 && response.body != null) {
                            val responseBody = response.body!!.string()
                            rate = responseBody.toDouble()
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                    rate = 0.0
                }

                rate
            }

        /**
         * 调用智能合约函数
         */
        private suspend fun callSmartContractFunction(
            web3j: Web3j,
            function: Function,
            contractAddress: String,
        ): String? =
            withContext(Dispatchers.IO) {
                val encodedFunction = FunctionEncoder.encode(function)

                try {
                    val transaction =
                        org.web3j.protocol.core.methods.request.Transaction
                            .createEthCallTransaction(ZERO_ADDRESS, contractAddress, encodedFunction)
                    val response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send()
                    response.value
                } catch (e: IOException) {
                    // 连接错误，使用缓存值
                    null
                } catch (e: Exception) {
                    Timber.e(e)
                    null
                }
            }

        /**
         * 获取价格数据函数
         */
        private fun getTickers(): Function =
            Function(
                "getTickers",
                emptyList(),
                listOf(object : TypeReference<DynamicArray<Uint256>>() {}),
            )

        /**
         * 为整条链写入自定义行情。
         *
         * 该接口供自定义构建或特殊渠道使用，可直接覆盖链级别的价格数据。
         */
        @Suppress("unused")
        fun addCustomTicker(
            chainId: Long,
            ticker: TokenTicker?,
        ) {
            ticker?.let { ethTickers[chainId] = it }
        }

        /**
         * 为特定合约写入自定义行情。
         *
         * 会在后台线程更新 Realm，写入后 UI 会自动读取到新的价格信息。
         */
        @Suppress("unused")
        fun addCustomTicker(
            chainId: Long,
            address: String?,
            ticker: TokenTicker?,
        ) {
            if (ticker != null && address != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val tickerMap: MutableMap<String?, TokenTicker?> = mutableMapOf(address to ticker)
                        localSource.updateERC20Tickers(chainId, tickerMap)
                    } catch (e: Exception) {
                        Timber.e(e, "添加自定义价格时发生错误")
                    }
                }
            }
        }

        /**
         * 价格更新错误处理
         */
        private fun onTickersError(throwable: Throwable) {
            mainTickerUpdateJob = null
            Timber.e(throwable)
            _tickerUpdateState.value = TickerUpdateState.Error(throwable.message ?: "未知错误")
        }

        /**
         * 以“金额 + 货币代码”形式返回价格字符串，适合列表等展示场景。
         */
        fun getFullCurrencyString(price: Double): String = getCurrencyString(price) + " " + currentCurrencySymbolTxt

        /**
         * 根据当前本地化设置，格式化并返回带货币符号的金额字符串。
         */


        fun getCurrencyString(price: Double): String = BalanceUtils.genCurrencyString(price, currentCurrencySymbol)

        /**
         * 将价格变化值格式化为百分比字符串，保留两位小数。
         */
        fun getPercentageConversion(d: Double): String = BalanceUtils.getScaledValue(BigDecimal.valueOf(d), 0, 2)

        /**
         * 初始化当前货币设置。
         *
         * 从偏好设置中读取默认货币代码与符号，并缓存到内存变量供后续使用。
         */
        private fun initCurrency() {
            currentCurrencySymbolTxt = sharedPrefs.defaultCurrency ?: ""
            currentCurrencySymbol = sharedPrefs.defaultCurrencySymbol ?: ""
        }

        /**
         * 返回当前选定的 ISO 货币代码，例如 CNY、USD。
         */
        fun getCurrencySymbolTxt(): String = currentCurrencySymbolTxt

        /**
         * 返回当前货币的符号，例如 ¥、$。
         */
        fun getCurrencySymbol(): String = currentCurrencySymbol

        /**
         * 获取最近一次成功刷新后的 USD->本地货币汇率。
         */
        fun getCurrentConversionRate(): Double = currentConversionRate

        /**
         * 重置价格更新
         */
        private fun resetTickerUpdate() {
            ethTickers.clear()
            tokenCheckQueue.clear()
            dexGuruQuery.clear()
        }

        /**
         * 异步清空所有缓存的行情数据。
         *
         * 用于帐户切换或用户请求重新同步时，触发后 Realm 中的行情表会被清空。
         */
        fun deleteTickers() {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    localSource.deleteTickers()
                } catch (e: Exception) {
                    Timber.e(e, "删除价格数据时发生错误")
                }
            }
        }

        /**
         * 判断指定代币是否支持通过 CoinGecko 查询价格。
         *
         * 基于代币所属链及类型过滤，仅对主流公链与 ERC20 代币返回 true。
         */
        fun validateCoinGeckoAPI(token: Token): Boolean =
            if (token.isEthereum() && chainPairs.containsKey(token.tokenInfo.chainId)) {
                true
            } else if (!token.isEthereum() && !token.isNonFungible() && coinGeckoChainIdToAPIName.containsKey(token.tokenInfo.chainId)) {
                true
            } else {
                false
            }

        /**
         * 获取CoinGecko链调用URL
         */
        private fun getCoinGeckoChainCall(): String {
            val tokenList = StringBuilder()
            var firstPair = true

            for ((chainId, chainSymbol) in chainPairs) {
                if (ethTickers.containsKey(chainId)) {
                    continue
                }
                if (!firstPair) tokenList.append(",")
                firstPair = false
                tokenList.append(chainSymbol)
            }

            return COINGECKO_CHAIN_CALL
                .replace(CHAIN_IDS, tokenList.toString())
                .replace(CURRENCY_TOKEN, currentCurrencySymbolTxt)
        }

        /**
         * 检查是否收到所有链对
         */
        private fun receivedAllChainPairs(): Boolean {
            for (chainId in chainPairs.keys) {
                if (!ethTickers.containsKey(chainId)) {
                    return false
                }
            }
            return true
        }

        /**
         * 存储接收到的价格数据（如果需要）
         */
        fun storeTickers(
            chainId: Long,
            tickerMap: Map<String, TokenTicker>,
        ) {
            val tickerUpdateMap = mutableMapOf<String?, TokenTicker?>()

            for ((key, ticker) in tickerMap) {
                val dbKey = TokensRealmSource.databaseKey(chainId, key)
                val fromDb = localSource.getCurrentTicker(dbKey)
                if (fromDb == null || fromDb.getTickerAgeMillis() > TICKER_STALE_TIMEOUT) {
                    tickerUpdateMap[key] = ticker
                }
            }

            if (tickerUpdateMap.isNotEmpty()) {
                localSource.updateERC20Tickers(chainId, tickerUpdateMap)
            }
        }

        /**
         * 清理资源
         */
        fun cleanup() {
            tickerUpdateJob?.cancel()
            erc20TickerCheckJob?.cancel()
            mainTickerUpdateJob?.cancel()
        }
    }

/**
 * 价格更新状态
 */
sealed class TickerUpdateState {
    object Idle : TickerUpdateState()

    object Updating : TickerUpdateState()

    data class Completed(
        val tickerCount: Int,
    ) : TickerUpdateState()

    data class Error(
        val message: String,
    ) : TickerUpdateState()
}
