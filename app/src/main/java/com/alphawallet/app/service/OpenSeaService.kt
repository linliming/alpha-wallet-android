package com.alphawallet.app.service

import android.net.Uri
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.LongSparseArray
import com.alphawallet.app.C
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenFactory
import com.alphawallet.app.entity.tokens.TokenInfo
import com.alphawallet.app.repository.KeyProviderFactory
import com.alphawallet.app.util.JsonUtils
import com.alphawallet.app.util.JsonUtils.EMPTY_RESULT
import com.alphawallet.ethereum.EthereumNetworkBase.ARBITRUM_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.AVALANCHE_ID
import com.alphawallet.ethereum.EthereumNetworkBase.BINANCE_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.KLAYTN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.OPTIMISTIC_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_ID
import com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_TEST_ID
import com.alphawallet.ethereum.EthereumNetworkBase.SEPOLIA_TESTNET_ID
import io.reactivex.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.math.BigInteger
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * OpenSeaService - OpenSea NFT 数据获取服务
 *
 * Java 版本使用 RxJava Single; 该 Kotlin 重写版改用协程 suspend 函数。
 * 为兼容遗留调用点，提供了与旧行为等价的同步 `fetch*` 方法。
 */
class OpenSeaService {
    companion object {
        private const val PAGE_SIZE = 200

        private val API_CHAIN_MAP =
            mapOf(
                MAINNET_ID to "ethereum",
                KLAYTN_ID to "klaytn",
                POLYGON_TEST_ID to "mumbai",
                POLYGON_ID to "matic",
                OPTIMISTIC_MAIN_ID to "optimism",
                ARBITRUM_MAIN_ID to "arbitrum",
                SEPOLIA_TESTNET_ID to "sepolia",
                AVALANCHE_ID to "avalanche",
                BINANCE_MAIN_ID to "bsc",
            )

        fun hasOpenSeaAPI(chainId: Long): Boolean = API_CHAIN_MAP.containsKey(chainId)
    }

    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(C.CONNECT_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .connectTimeout(C.READ_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .writeTimeout(C.WRITE_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    private val imageUrls: MutableMap<String, String> = HashMap()
    private val networkCheckTimes = LongSparseArray<Long>()
    private val pageOffsets = ConcurrentHashMap<Long, String>()
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val tokenFactory = TokenFactory()

    /**
     * 获取地址在指定网络上的 NFT 列表。
     * @return 返回找到的 Token 数组。
     */
    suspend fun getTokens(
        address: String,
        networkId: Long,
        networkName: String,
        tokensService: TokensService,
    ): Array<Token> =
        withContext(Dispatchers.IO) {
            val foundTokens = HashMap<String, Token>()

            val currentTime = System.currentTimeMillis()
            if (!canCheckChain(networkId)) return@withContext emptyArray()
            networkCheckTimes.put(networkId, currentTime)

            var pageCursor = pageOffsets[networkId] ?: ""
            var currentPage = 0
            var receivedTokens: Int

            Timber.d("Fetch from OpenSea : %s", networkName)

            do {
                val jsonData = fetchAssets(networkId, address, pageCursor)
                if (!JsonUtils.hasAssets(jsonData)) {
                    return@withContext foundTokens.values.toTypedArray()
                }

                val result = JSONObject(jsonData)
                val assets = result.getJSONArray("nfts")
                receivedTokens = assets.length()

                processOpenseaTokens(
                    foundTokens,
                    assets,
                    address,
                    networkId,
                    networkName,
                    tokensService,
                )
                currentPage++
                pageCursor = if (result.has("next")) result.optString("next", "") else ""
                if (pageCursor.isEmpty()) {
                    break
                }
            } while (currentPage <= 3) // fetch 4 pages max per loop

            pageOffsets[networkId] = pageCursor

            if (receivedTokens < PAGE_SIZE) {
                Timber.d("Reset OpenSea API reads at: %s", pageCursor)
            } else {
                networkCheckTimes.put(networkId, currentTime - 55 * DateUtils.SECOND_IN_MILLIS)
            }

            // store contract images
            for ((contract, imageUrl) in imageUrls) {
                tokensService.addTokenImageUrl(networkId, contract, imageUrl)
            }
            imageUrls.clear()

            foundTokens.values.toTypedArray()
        }

    @Deprecated("Use suspend getTokens", ReplaceWith("getTokens(address, networkId, networkName, tokensService)"))
    fun getTokensSingle(
        address: String,
        networkId: Long,
        networkName: String,
        tokensService: TokensService,
    ): Single<Array<Token>> = singleFrom { getTokens(address, networkId, networkName, tokensService) }

    private fun processOpenseaTokens(
        foundTokens: MutableMap<String, Token>,
        assets: JSONArray,
        address: String,
        networkId: Long,
        networkName: String,
        tokensService: TokensService,
    ) {
        val assetList: MutableMap<String, MutableMap<BigInteger, NFTAsset>> = HashMap()

        for (i in 0 until assets.length()) {
            val assetJSON = assets.getJSONObject(i)
            val tokenStandard = assetJSON.optString("token_standard").lowercase(Locale.ROOT)
            if (tokenStandard.isEmpty()) continue

            when (tokenStandard) {
                "erc721" -> handleERC721(assetList, assetJSON, networkId, foundTokens, tokensService, networkName, address)
                "erc1155" -> handleERC1155(assetList, assetJSON, networkId, foundTokens, tokensService, networkName, address)
            }
        }
    }

    private fun handleERC721(
        assetList: MutableMap<String, MutableMap<BigInteger, NFTAsset>>,
        assetJSON: JSONObject,
        networkId: Long,
        foundTokens: MutableMap<String, Token>,
        tokensService: TokensService,
        networkName: String,
        address: String,
    ) {
        val asset = NFTAsset(assetJSON.toString())
        val tokenId =
            if (assetJSON.has("identifier")) {
                BigInteger(assetJSON.getString("identifier"))
            } else {
                null
            } ?: return

        val contractAddress = assetJSON.getString("contract")
        val collectionName = assetJSON.optString("collection")

        var token = foundTokens[contractAddress]
        if (token == null) {
            val checkToken = tokensService.getToken(networkId, contractAddress)
            val (tokenInfo, type, lastCheck) =
                if (checkToken != null && (checkToken.isERC721() || checkToken.isERC721Ticket())) {
                    assetList[contractAddress] = checkToken.getTokenAssets().toMutableMap()
                    var info = checkToken.tokenInfo
                    if (!TextUtils.isEmpty(collectionName) && TextUtils.isEmpty(info.name)) {
                        info = TokenInfo(contractAddress, collectionName, "", info.decimals, info.isEnabled, networkId)
                    }
                    Triple(info, checkToken.getInterfaceSpec(), checkToken.lastTxTime)
                } else {
                    Triple(TokenInfo(contractAddress, asset.name, "", 0, true, networkId), ContractType.ERC721, 0L)
                }

            token = tokenFactory.createToken(tokenInfo, type, networkName)
            token.setTokenWallet(address)
            token.lastTxTime = lastCheck
            foundTokens[contractAddress] = token
        }

        val existingAssets = token?.let { assetList.getOrPut(it.getAddress()) { mutableMapOf() } }
        asset.updateAsset(tokenId, existingAssets)
        token?.addAssetToTokenBalanceAssets(tokenId, asset)
        addAssetImageToHashMap(contractAddress, asset.image)
    }

    private fun handleERC1155(
        assetList: MutableMap<String, MutableMap<BigInteger, NFTAsset>>,
        assetJSON: JSONObject,
        networkId: Long,
        foundTokens: MutableMap<String, Token>,
        tokensService: TokensService,
        networkName: String,
        address: String,
    ) {
        val asset = NFTAsset(assetJSON.toString())
        val tokenId =
            if (assetJSON.has("identifier")) {
                BigInteger(assetJSON.getString("identifier"))
            } else {
                null
            } ?: return

        val contractAddress = assetJSON.getString("contract")
        val collectionName = assetJSON.optString("collection")

        var token = foundTokens[contractAddress]
        if (token == null) {
            val checkToken = tokensService.getToken(networkId, contractAddress)
            val (tokenInfo, type, lastCheck) =
                if (checkToken != null && checkToken.getInterfaceSpec() == ContractType.ERC1155) {
                    assetList[contractAddress] = checkToken.getTokenAssets().toMutableMap()
                    Triple(checkToken.tokenInfo, checkToken.getInterfaceSpec(), checkToken.lastTxTime)
                } else {
                    Triple(TokenInfo(contractAddress, collectionName, "", 0, true, networkId), ContractType.ERC1155, 0L)
                }

            token = tokenFactory.createToken(tokenInfo, type, networkName)
            token.setTokenWallet(address)
            token.lastTxTime = lastCheck
            foundTokens[contractAddress] = token
        }

        val existingAssets = assetList.getOrPut(token?.getAddress() ?: "") { mutableMapOf() }
        asset.updateAsset(tokenId, existingAssets)
        token?.addAssetToTokenBalanceAssets(tokenId, asset)
        addAssetImageToHashMap(contractAddress, asset.image)
    }

    private fun addAssetImageToHashMap(address: String, imageUrl: String?) {
        if (!imageUrl.isNullOrEmpty() && !imageUrls.containsKey(address)) {
            imageUrls[address] = imageUrl
        }
    }

    fun resetOffsetRead(networkFilter: List<Long>) {
        var offsetTime = System.currentTimeMillis() - 57 * DateUtils.SECOND_IN_MILLIS
        for (networkId in networkFilter) {
            if (OpenSeaService.hasOpenSeaAPI(networkId)) {
                networkCheckTimes.put(networkId, offsetTime)
                offsetTime += 10 * DateUtils.SECOND_IN_MILLIS
            }
        }
        pageOffsets.clear()
    }

    fun canCheckChain(networkId: Long): Boolean {
        val lastCheckTime = networkCheckTimes.get(networkId, 0L)
        return System.currentTimeMillis() > lastCheckTime + 10 * DateUtils.MINUTE_IN_MILLIS
    }

    /**
     * 按旧接口保留：返回单个资产的 JSON。
     * 注意：此方法仍为同步实现。
     */
    suspend fun getAsset(token: Token, tokenId: BigInteger): String =
        withContext(Dispatchers.IO) {
            if (!hasOpenSeaAPI(token.tokenInfo.chainId)) return@withContext ""
            fetchAsset(token.tokenInfo.chainId, token.tokenInfo.address?:"", tokenId.toString())
        }

    @Deprecated("Use suspend getAsset", ReplaceWith("getAsset(token, tokenId)"))
    fun getAssetSingle(token: Token, tokenId: BigInteger): Single<String> = singleFrom { getAsset(token, tokenId) }

    suspend fun getCollection(token: Token, slug: String): String =
        withContext(Dispatchers.IO) {
            fetchCollection(token.tokenInfo.chainId, slug)
        }

    @Deprecated("Use suspend getCollection", ReplaceWith("getCollection(token, slug)"))
    fun getCollectionSingle(token: Token, slug: String): Single<String> = singleFrom { getCollection(token, slug) }

    fun fetchAssets(networkId: Long, address: String, pageCursor: String): String {
        val mappingName = API_CHAIN_MAP[networkId]
        if (mappingName.isNullOrEmpty()) return EMPTY_RESULT

        val api =
            C.OPENSEA_ASSETS_API_V2
                .replace("{CHAIN}", mappingName)
                .replace("{ADDRESS}", address)

        val builder =
            Uri
                .Builder()
                .encodedPath(api)
                .appendQueryParameter("limit", PAGE_SIZE.toString())

        if (pageCursor.isNotEmpty()) {
            builder.appendQueryParameter("next", pageCursor)
        }

        return executeRequest(networkId, builder.build().toString())
    }

    fun fetchAsset(networkId: Long, contractAddress: String, tokenId: String): String {
        val mappingName = API_CHAIN_MAP[networkId]
        if (mappingName.isNullOrEmpty()) return EMPTY_RESULT

        val api =
            C.OPENSEA_NFT_API_V2
                .replace("{CHAIN}", mappingName)
                .replace("{ADDRESS}", contractAddress)
                .replace("{TOKEN_ID}", tokenId)
        return executeRequest(networkId, api)
    }

    fun fetchCollection(networkId: Long, slug: String): String {
        val api = C.OPENSEA_COLLECTION_API_MAINNET + slug
        return executeRequest(networkId, api)
    }

    private fun buildRequest(networkId: Long, api: String): Request {
        val builder =
            Request
                .Builder()
                .url(api)
                .method("GET", null)

        val apiKey = KeyProviderFactory.get().getOpenSeaKey()
        if (!apiKey.isNullOrEmpty() &&
            apiKey != "..." &&
            com.alphawallet.app.repository.EthereumNetworkBase.hasRealValue(networkId)
        ) {
            builder.addHeader("X-API-KEY", apiKey)
        }

        return builder.build()
    }

    private fun executeRequest(networkId: Long, api: String): String =
        try {
            httpClient
                .newCall(buildRequest(networkId, api))
                .execute()
                .use { response ->
                    if (response.isSuccessful) {
                        val responseBody: ResponseBody? = response.body
                        if (responseBody != null) {
                            return responseBody.string()
                        }
                    }
                    EMPTY_RESULT
                }
        } catch (e: Exception) {
            Timber.e(e)
            EMPTY_RESULT
        }

    private fun <T : Any> singleFrom(block: suspend () -> T): Single<T> =
        Single.create { emitter ->
            val job =
                bridgeScope.launch {
                    try {
                        emitter.onSuccess(block())
                    } catch (throwable: Throwable) {
                        emitter.tryOnError(throwable)
                    }
                }
            emitter.setCancellable {
                job.cancel()
            }
        }
}
