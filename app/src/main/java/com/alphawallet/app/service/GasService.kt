package com.alphawallet.app.service

import android.text.TextUtils
import com.alphawallet.app.C
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.EIP1559FeeOracleResult
import com.alphawallet.app.entity.FeeHistory
import com.alphawallet.app.entity.GasEstimate
import com.alphawallet.app.entity.GasPriceSpread
import com.alphawallet.app.entity.TXSpeed
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.suggestEIP1559
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokenscript.TokenscriptFunction.Companion.ZERO_ADDRESS
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.EthereumNetworkRepositoryType
import com.alphawallet.app.repository.HttpServiceHelper
import com.alphawallet.app.repository.KeyProviderFactory
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.app.repository.TokensRealmSource.Companion.TICKER_DB
import com.alphawallet.app.repository.entity.Realm1559Gas
import com.alphawallet.app.repository.entity.RealmGasSpread
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import com.google.gson.Gson
import io.reactivex.Single
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthEstimateGas
import org.web3j.protocol.core.methods.response.EthGasPrice
import org.web3j.protocol.http.HttpService.JSON_MEDIA_TYPE
import org.web3j.tx.gas.ContractGasProvider
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * GasService - Gas 价格管理服务
 *
 * 负责管理以太坊网络的 Gas 价格获取、缓存和估算功能。本服务采用 Kotlin 协程重构，
 * 提供更现代化的异步操作接口。
 *
 * 主要功能：
 * - Gas 价格周期获取和缓存
 * - EIP-1559 费用结构支持
 * - Gas 限制估算
 * - 多链网络支持
 * - 本地数据库缓存
 *
 * 技术特点：
 * - 基于 Kotlin 协程的异步操作
 * - 支持 EIP-1559 和传统 Gas 价格
 * - 多数据源支持（节点、Etherscan、PolygonScan）
 * - 本地 Realm 数据库缓存
 * - 自动重试和错误处理
 *
 * 协程升级说明：
 * - 将 RxJava Observable/Single 替换为协程
 * - 使用 CoroutineScope 管理生命周期
 * - 提供超时控制和异常处理
 * - 支持取消操作
 *
 * @property networkRepository 网络仓库
 * @property httpClient HTTP 客户端
 * @property realmManager Realm 管理器
 *
 * @since 2024
 * @author AlphaWallet Team
 */
class GasService(
    private val networkRepository: EthereumNetworkRepositoryType,
    private val httpClient: OkHttpClient,
    private val realmManager: RealmManager
) : ContractGasProvider {

    companion object {
        const val FETCH_GAS_PRICE_INTERVAL_SECONDS = 15L
        private const val BLOCK_COUNT = "[BLOCK_COUNT]"
        private const val NEWEST_BLOCK = "[NEWEST_BLOCK]"
        private const val REWARD_PERCENTILES = "[REWARD_PERCENTILES]"
        private const val FEE_HISTORY = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_feeHistory\",\"params\":[\"$BLOCK_COUNT\", \"$NEWEST_BLOCK\",[$REWARD_PERCENTILES]],\"id\":1}"
        private const val WHALE_ACCOUNT = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045" // 用于计算超出默认 Gas 限制的交易估算
        private const val GAS_FETCH_TIMEOUT_MS = 30000L // 30秒超时
        private const val GAS_PRICE_CACHE_DURATION_MS = FETCH_GAS_PRICE_INTERVAL_SECONDS * 1000L

        /**
         * 获取默认 Gas 限制
         * @param token 代币
         * @param tx 交易
         * @return 默认 Gas 限制
         */
        fun getDefaultGasLimit(token: Token, tx: Web3Transaction): BigInteger {
            val hasPayload = tx.payload?.length ?: 0 >= 10

            return when (token.contractType) {
                ContractType.ETHEREUM -> {
                    if (hasPayload) BigInteger.valueOf(C.GAS_LIMIT_CONTRACT.toLong())
                    else BigInteger.valueOf(C.GAS_LIMIT_MIN.toLong())
                }
                ContractType.ERC20 -> BigInteger.valueOf(C.GAS_LIMIT_DEFAULT.toLong())
                ContractType.ERC875_LEGACY,
                ContractType.ERC875,
                ContractType.ERC721,
                ContractType.ERC721_LEGACY,
                ContractType.ERC721_TICKET,
                ContractType.ERC721_UNDETERMINED,
                ContractType.ERC721_ENUMERABLE -> BigInteger(C.DEFAULT_GAS_LIMIT_FOR_NONFUNGIBLE_TOKENS)
                else -> BigInteger.valueOf(C.GAS_LIMIT_CONTRACT.toLong()) // 未知
            }
        }

    }

    // ==================== 成员变量 ====================
    
    private var currentChainId: Long = MAINNET_ID
    private var web3j: Web3j? = null
    private var currentGasPrice: BigInteger = BigInteger.ZERO
    private var currentGasPriceTime: Long = 0
    private var currentLowGasPrice: BigInteger = BigInteger.ZERO
    private var keyFail: Boolean = false
    
    // API 密钥
    private val etherscanApiKey: String
    private val polygonscanApiKey: String
    
    // 协程相关
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var gasFetchJob: Job? = null

    init {
        val keyProvider = KeyProviderFactory.get()
        etherscanApiKey = "&apikey=${keyProvider.getEtherscanKey()}"
        polygonscanApiKey = "&apikey=${keyProvider.getPolygonScanKey()}"
        keyFail = false
        currentGasPrice = BigInteger.ZERO
        currentGasPriceTime = 0
    }

    // ==================== 公共 API ====================

    /**
     * 启动 Gas 价格获取周期
     * @param chainId 链ID
     */
    fun startGasPriceCycle(chainId: Long) {
        updateChainId(chainId)
        gasFetchJob?.cancel()
        gasFetchJob = serviceScope.launch {
            while (isActive) {
                try {
                    fetchCurrentGasPrice()
                    delay(FETCH_GAS_PRICE_INTERVAL_SECONDS * 1000)
                } catch (e: CancellationException) {
                    Timber.d("Gas 价格获取周期被取消")
                    break
                } catch (e: Exception) {
                    Timber.e(e, "Gas 价格获取失败")
                    delay(FETCH_GAS_PRICE_INTERVAL_SECONDS * 1000)
                }
            }
        }
    }

    /**
     * 停止 Gas 价格获取周期
     */
    fun stopGasPriceCycle() {
        gasFetchJob?.cancel()
        gasFetchJob = null
    }

    /**
     * 更新链ID
     * @param chainId 新的链ID
     */
    fun updateChainId(chainId: Long) {
        if (networkRepository.getNetworkByChain(chainId) == null) {
            Timber.d("网络错误，无链信息，尝试选择: %s", chainId)
        } else if (web3j == null || web3j?.ethChainId()?.id != chainId) {
            currentGasPrice = BigInteger.ZERO
            currentGasPriceTime = 0
            currentChainId = chainId
            web3j = TokenRepository.getWeb3jService(chainId)
        }
    }

    // ==================== ContractGasProvider 实现 ====================

    override fun getGasPrice(contractFunc: String?): BigInteger = currentGasPrice

    override fun getGasPrice(): BigInteger = currentGasPrice

    override fun getGasLimit(contractFunc: String?): BigInteger? = null

    override fun getGasLimit(): BigInteger = BigInteger(C.DEFAULT_GAS_LIMIT_FOR_NONFUNGIBLE_TOKENS)

    // ==================== 私有方法 ====================

    /**
     * 获取当前 Gas 价格
     */
    private suspend fun fetchCurrentGasPrice() {
        currentLowGasPrice = BigInteger.ZERO
        
        try {
            // 更新当前 Gas 价格
            updateCurrentGasPrices()
            useNodeEstimate(false)
            
            // 如果支持 EIP-1559 且之前没有确定不支持，则更新 EIP-1559
            if (!keyFail) {
                getEIP1559FeeStructure(currentChainId)?.let { result ->
                    updateEIP1559Realm(result, currentChainId)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "获取当前 Gas 价格失败")
        }
    }

    /**
     * 检查节点获取是否有效
     */
    private fun nodeFetchValid(): Boolean {
        return (System.currentTimeMillis() + GAS_PRICE_CACHE_DURATION_MS) <= currentGasPriceTime
    }

    /**
     * 更新当前 Gas 价格
     */
    private suspend fun updateCurrentGasPrices(): Boolean = withContext(Dispatchers.IO) {
        val gasOracleAPI = EthereumNetworkBase.getEtherscanGasOracle(currentChainId)
        return@withContext if (!TextUtils.isEmpty(gasOracleAPI)) {
            val apiUrl = when {
                !keyFail && gasOracleAPI.contains("etherscan") -> gasOracleAPI + etherscanApiKey
                !keyFail && gasOracleAPI.contains("polygonscan") -> gasOracleAPI + polygonscanApiKey
                else -> gasOracleAPI
            }
            updateEtherscanGasPrices(apiUrl)
        } else {
            // 使用节点获取链价格
            useNodeEstimate(false)
        }
    }

    /**
     * 使用节点估算
     */
    private suspend fun useNodeEstimate(updated: Boolean): Boolean = withContext(Dispatchers.IO) {
        return@withContext when {
            nodeFetchValid() -> true
            (networkRepository as? EthereumNetworkBase)?.hasGasOverride(currentChainId) == true -> {
                val overrideValue = (networkRepository as EthereumNetworkBase).gasOverrideValue(currentChainId)
                updateRealm(
                    GasPriceSpread(overrideValue, networkRepository.hasLockedGas(currentChainId)),
                    currentChainId
                )
                currentGasPriceTime = System.currentTimeMillis()
                currentGasPrice = overrideValue
                true
            }
            else -> {
                val price = getNodeEstimate(currentChainId)
                updateGasPrice(price, currentChainId, updated)
            }
        }
    }

    /**
     * 获取节点估算
     */
    private suspend fun getNodeEstimate(chainId: Long): EthGasPrice = withContext(Dispatchers.IO) {
        return@withContext suspendCancellableCoroutine { continuation ->
            try {
                val result = TokenRepository.getWeb3jService(chainId).ethGasPrice().send()
                continuation.resume(result)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * 更新 Gas 价格
     */
    private fun updateGasPrice(ethGasPrice: EthGasPrice, chainId: Long, databaseUpdated: Boolean): Boolean {
        currentGasPrice = ethGasPrice.gasPrice
        currentGasPriceTime = System.currentTimeMillis()
        if (!databaseUpdated) {
            updateRealm(
                GasPriceSpread(currentGasPrice, networkRepository.hasLockedGas(chainId)),
                chainId
            )
        }
        return true
    }

    /**
     * 更新 Etherscan Gas 价格
     */
    private suspend fun updateEtherscanGasPrices(gasOracleAPI: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext suspendCancellableCoroutine { continuation ->
            try {
                val request = Request.Builder()
                    .url(gasOracleAPI)
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.code / 200 == 1) {
                        val result = response.body?.string()
                        if (result != null) {
                            val gps = GasPriceSpread(result)
                            updateRealm(gps, currentChainId)

                            if (gps.isResultValid()) {
                                currentLowGasPrice = gps.getBaseFee()
                                continuation.resume(true)
                            } else {
                                keyFail = true
                                continuation.resume(false)
                            }
                        } else {
                            continuation.resume(false)
                        }
                    } else {
                        continuation.resume(false)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "更新 Etherscan Gas 价格失败")
                continuation.resume(false)
            }
        }
    }

    /**
     * 更新 Realm 数据库中的最新 Gas 价格
     * 这解耦了 Gas 服务与任何活动
     * @param oracleResult Gas 价格扩展结果
     * @param chainId 链ID
     */
    private fun updateRealm(oracleResult: GasPriceSpread, chainId: Long) {
        try {
            realmManager.getRealmInstance(TICKER_DB).use { realm ->
                realm.executeTransaction { r ->
                    val rgs = r.where(RealmGasSpread::class.java)
                        .equalTo("chainId", chainId)
                        .findFirst() ?: r.createObject(RealmGasSpread::class.java, chainId)

                    rgs.setGasSpread(oracleResult, System.currentTimeMillis())
                    r.insertOrUpdate(rgs)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "更新 Realm Gas 价格失败")
        }
    }

    /**
     * 更新 EIP-1559 Realm 数据
     */
    private fun updateEIP1559Realm(result: Map<Int, EIP1559FeeOracleResult>, chainId: Long): Boolean {
        return try {
            realmManager.getRealmInstance(TICKER_DB).use { realm ->
                realm.executeTransaction { r ->
                    val rgs = r.where(Realm1559Gas::class.java)
                        .equalTo("chainId", chainId)
                        .findFirst() ?: r.createObject(Realm1559Gas::class.java, chainId)

                    rgs.setResultData(result, System.currentTimeMillis())
                }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "更新 EIP-1559 Realm 数据失败")
            false
        }
    }

    /**
     * 拉取最新 Gas 价格（保留 RxSingle 以兼容旧代码）
     */
    fun fetchGasPrice(chainId: Long, use1559Gas: Boolean): Single<EIP1559FeeOracleResult> =
        singleFrom { fetchGasPriceSuspend(chainId, use1559Gas) }

    /**
     * 协程版本的 Gas 价格获取接口
     */
    suspend fun fetchGasPriceSuspend(chainId: Long, use1559Gas: Boolean): EIP1559FeeOracleResult {
        updateChainId(chainId)
        return if (use1559Gas) {
            val result = getEIP1559FeeStructure(chainId)
            val standard = result?.get(TXSpeed.STANDARD.ordinal)
            if (standard != null) {
                standard
            } else {
                val gasPrice = getNodeEstimate(chainId)
                EIP1559FeeOracleResult(BigInteger.ZERO, BigInteger.ZERO, gasPrice.gasPrice)
            }
        } else {
            val gasPrice = getNodeEstimate(chainId)
            EIP1559FeeOracleResult(gasPrice.gasPrice, BigInteger.ZERO, BigInteger.ZERO)
        }
    }

    /**
     * 计算 Gas 估算（暴露为 RxSingle 以兼容旧调用）
     */
    fun calculateGasEstimate(
        transactionBytes: ByteArray?,
        chainId: Long,
        toAddress: String,
        amount: BigInteger?,
        wallet: Wallet?,
        defaultLimit: BigInteger,
    ): Single<GasEstimate> =
        singleFrom {
            calculateGasEstimateSuspend(transactionBytes, chainId, toAddress, amount, wallet, defaultLimit)
        }

    /**
     * 协程版本的 Gas 估算，供内部和新代码调用
     */
    suspend fun calculateGasEstimateSuspend(
        transactionBytes: ByteArray?,
        chainId: Long,
        toAddress: String,
        amount: BigInteger?,
        wallet: Wallet?,
        defaultLimit: BigInteger,
    ): GasEstimate {
        updateChainId(chainId)
        useNodeEstimate(true)
        return calculateGasEstimateInternal(transactionBytes, chainId, toAddress, amount, wallet, defaultLimit)
    }

    private suspend fun calculateGasEstimateInternal(
        transactionBytes: ByteArray?,
        chainId: Long,
        toAddress: String,
        amount: BigInteger?,
        wallet: Wallet?,
        defaultLimit: BigInteger,
    ): GasEstimate {
        val txData =
            if (transactionBytes != null && transactionBytes.isNotEmpty()) {
                Numeric.toHexString(transactionBytes)
            } else {
                ""
            }

        updateChainId(chainId)
        val currentWeb3j = web3j ?: TokenRepository.getWeb3jService(chainId).also { web3j = it }
        val useGasLimit =
            if (defaultLimit == BigInteger.ZERO) {
                EthereumNetworkBase.getMaxGasLimit(chainId)
            } else {
                defaultLimit
            }
        val resolvedDefaultLimit =
            if (defaultLimit == BigInteger.ZERO) {
                BigInteger(C.DEFAULT_GAS_LIMIT_FOR_NONFUNGIBLE_TOKENS)
            } else {
                defaultLimit
            }
        val targetWallet = wallet ?: run {
            Timber.w("calculateGasEstimateInternal invoked without wallet")
            return GasEstimate(resolvedDefaultLimit, "Wallet unavailable")
        }

        return if ((toAddress.isEmpty() || toAddress.equals(ZERO_ADDRESS, ignoreCase = true)) && txData.isNotEmpty()) {
            val nonce = networkRepository.getLastTransactionNonce(currentWeb3j, targetWallet.address.orEmpty())
            val estimate =
                ethEstimateGasConstructor(
                    targetWallet.address.orEmpty(),
                    nonce,
                    getLowGasPrice(),
                    EthereumNetworkBase.getMaxGasLimit(chainId),
                    txData,
                )
            convertToGasLimit(estimate, EthereumNetworkBase.getMaxGasLimit(chainId))
        } else {
            val nonce = networkRepository.getLastTransactionNonce(currentWeb3j, targetWallet.address.orEmpty())
            val estimate =
                ethEstimateGas(
                    chainId,
                    targetWallet.address.orEmpty(),
                    useGasLimit,
                    nonce,
                    toAddress,
                    amount,
                    txData,
                )
            val resolvedEstimate = handleOutOfGasError(estimate, chainId, toAddress, amount, txData)
            convertToGasLimit(resolvedEstimate, resolvedDefaultLimit)
        }
    }

    private suspend fun handleOutOfGasError(
        estimate: EthEstimateGas,
        chainId: Long,
        toAddress: String,
        amount: BigInteger?,
        txData: String,
    ): EthEstimateGas {
        if (!estimate.hasError() || chainId != MAINNET_ID) return estimate

        val currentWeb3j = web3j ?: TokenRepository.getWeb3jService(chainId).also { web3j = it }
        val whaleNonce = networkRepository.getLastTransactionNonce(currentWeb3j, WHALE_ACCOUNT)
        return ethEstimateGas(
            chainId,
            WHALE_ACCOUNT,
            EthereumNetworkBase.getMaxGasLimit(chainId),
            whaleNonce,
            toAddress,
            amount,
            txData,
        )
    }

    private fun convertToGasLimit(
        estimate: EthEstimateGas,
        defaultLimit: BigInteger,
    ): GasEstimate {
        val error = estimate.error

        return when {
            error != null -> {
                if (error.code == -32000) {
                    GasEstimate(defaultLimit, error.message)
                } else {
                    GasEstimate(BigInteger.ZERO, error.message)
                }
            }
            estimate.amountUsed > BigInteger.ZERO -> GasEstimate(estimate.amountUsed)
            defaultLimit == BigInteger.ZERO -> GasEstimate(BigInteger(C.DEFAULT_GAS_LIMIT_FOR_NONFUNGIBLE_TOKENS))
            else -> GasEstimate(defaultLimit)
        }
    }

    private suspend fun ethEstimateGasConstructor(
        fromAddress: String,
        nonce: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        txData: String,
    ): EthEstimateGas =
        withContext(Dispatchers.IO) {
            val service = web3j ?: TokenRepository.getWeb3jService(currentChainId).also { web3j = it }
            val transaction = Transaction(fromAddress, nonce, gasPrice, gasLimit, null, BigInteger.ZERO, txData)
            service.ethEstimateGas(transaction).send()
        }

    private suspend fun ethEstimateGas(
        chainId: Long,
        fromAddress: String,
        limit: BigInteger,
        nonce: BigInteger,
        toAddress: String,
        amount: BigInteger?,
        txData: String,
    ): EthEstimateGas =
        withContext(Dispatchers.IO) {
            val service = web3j ?: TokenRepository.getWeb3jService(chainId).also { web3j = it }
            val transaction =
                Transaction(
                    fromAddress,
                    nonce,
                    currentGasPrice,
                    limit,
                    toAddress,
                    amount,
                    txData,
                )
            service.ethEstimateGas(transaction).send()
        }

    /**
     * 获取低 Gas 价格
     */
    private fun getLowGasPrice(): BigInteger = currentGasPrice

    /**
     * 获取 EIP-1559 费用结构
     */
    private suspend fun getEIP1559FeeStructure(chainId: Long): Map<Int, EIP1559FeeOracleResult>? = 
        withContext(Dispatchers.IO) {
            try {
                val result = InfuraGasAPI.get1559GasEstimates(chainId, httpClient).blockingGet()
                val blockNativeResult = BlockNativeGasAPI.get(httpClient).get1559GasEstimates(result, chainId).blockingGet()
                useCalculationIfRequired(blockNativeResult)
            } catch (e: Exception) {
                Timber.e(e, "获取 EIP-1559 费用结构失败")
                null
            }
        }

    /**
     * 如果需要则使用计算
     */
    private suspend fun useCalculationIfRequired(resultMap: Map<Int, EIP1559FeeOracleResult>): Map<Int, EIP1559FeeOracleResult>? =
        withContext(Dispatchers.IO) {
            return@withContext if (resultMap.isNotEmpty()) {
                resultMap
            } else {
                getEIP1559FeeStructureCalculation()
            }
        }

    /**
     * 获取 EIP-1559 费用结构计算
     */
    private suspend fun getEIP1559FeeStructureCalculation(): Map<Int, EIP1559FeeOracleResult>? =
        withContext(Dispatchers.IO) {
            try {
                val feeHistory = getChainFeeHistory(100, "latest", "")
                suggestEIP1559(this@GasService, feeHistory)
            } catch (e: Exception) {
                Timber.e(e, "EIP-1559 费用结构计算失败")
                null
            }
        }

    /**
     * 获取链费用历史
     */
    suspend fun getChainFeeHistory(blockCount: Int, lastBlock: String, rewardPercentiles: String): FeeHistory =
        withContext(Dispatchers.IO) {
            return@withContext suspendCancellableCoroutine { continuation ->
                try {
                    // TODO: 一旦 Web3j 完全支持 EIP1559 就替换
                    val requestJSON = FEE_HISTORY
                        .replace(BLOCK_COUNT, Numeric.prependHexPrefix(blockCount.toLong().toString(16)))
                        .replace(NEWEST_BLOCK, lastBlock)
                        .replace(REWARD_PERCENTILES, rewardPercentiles)

                    val requestBody = requestJSON.toRequestBody(JSON_MEDIA_TYPE)
//                    val requestBody = RequestBody.create(requestJSON, okhttp3.MediaType.parse(HttpService.JSON_MEDIA_TYPE))
                    val info = networkRepository.getNetworkByChain(currentChainId)

                    val rqBuilder = Request.Builder()
                        .url(info.rpcServerUrl)
                        .post(requestBody)

                    HttpServiceHelper.addRequiredCredentials(
                        info.rpcServerUrl,
                        rqBuilder,
                        KeyProviderFactory.get().getInfuraSecret()
                    )

                    val request = rqBuilder.build()
                    httpClient.newCall(request).execute().use { response ->
                        if (response.code / 200 == 1) {
                            val jsonData = JSONObject(response.body?.string() ?: "{}")
                            val result = Gson().fromJson(
                                jsonData.getJSONObject("result").toString(),
                                FeeHistory::class.java
                            )
                            continuation.resume(result)
                        } else {
                            continuation.resume(FeeHistory())
                        }
                    }
                } catch (e: org.json.JSONException) {
                    Timber.e("注意: ${networkRepository.getNetworkByChain(currentChainId).shortName} 似乎不支持 EIP1559")
                    continuation.resume(FeeHistory())
                } catch (e: Exception) {
                    Timber.e(e, "获取链费用历史失败")
                    continuation.resume(FeeHistory())
                }
            }
        }

    private fun <T : Any> singleFrom(block: suspend () -> T): Single<T> =
        Single.create { emitter ->
            val job =
                serviceScope.launch {
                    try {
                        emitter.onSuccess(block())
                    } catch (cancellation: CancellationException) {
                        emitter.tryOnError(cancellation)
                    } catch (throwable: Throwable) {
                        emitter.tryOnError(throwable)
                    }
                }
            emitter.setCancellable { job.cancel() }
        }

    // ==================== 静态方法 ====================


}
