package com.alphawallet.app.service

import android.net.Uri
import android.util.LongSparseArray
import com.alphawallet.app.entity.EtherscanEvent
import com.alphawallet.app.entity.OkxEvent
import com.alphawallet.app.entity.okx.OkProtocolType
import com.alphawallet.app.entity.okx.OkServiceResponse
import com.alphawallet.app.entity.okx.OkToken
import com.alphawallet.app.entity.okx.TokenListReponse
import com.alphawallet.app.entity.tokens.TokenInfo
import com.alphawallet.app.entity.transactionAPI.TransferFetchType
import com.alphawallet.app.repository.KeyProviderFactory
import com.alphawallet.app.util.JsonUtils
import com.alphawallet.ethereum.EthereumNetworkBase.ARBITRUM_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.AVALANCHE_ID
import com.alphawallet.ethereum.EthereumNetworkBase.BASE_MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.BINANCE_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.CLASSIC_ID
import com.alphawallet.ethereum.EthereumNetworkBase.FANTOM_ID
import com.alphawallet.ethereum.EthereumNetworkBase.KLAYTN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.LINEA_ID
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.OKX_ID
import com.alphawallet.ethereum.EthereumNetworkBase.OPTIMISTIC_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_AMOY_ID
import com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_ID
import com.alphawallet.ethereum.EthereumNetworkBase.SEPOLIA_TESTNET_ID
import com.google.gson.Gson
import io.reactivex.Single
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import timber.log.Timber

/**
 * Wrapper for the OKLink API used for token balances and transaction history.
 */
class OkLinkService private constructor(
    private val httpClient: OkHttpClient,
) {
    private val hasKey: Boolean = KeyProviderFactory.get().getOkLinkKey().isNotEmpty()

    /**
     * Retrieves paged transfer events and adapts them to [EtherscanEvent].
     */
    fun getEtherscanEvents(
        chainId: Long,
        address: String,
        lastBlockRead: Long,
        tfType: TransferFetchType,
    ): Array<EtherscanEvent> {
        if (!hasKey || !supportsChain(chainId)) {
            return emptyArray()
        }

        val protocolType = getOkxFetchType(tfType)
        val events = ArrayList<OkxEvent>()
        var page = 1
        var totalPage = 0
        var reachedPreviousRead: Boolean

        do {
            val response = Gson().fromJson(
                fetchTransactions(chainId, address, protocolType, page.toString()),
                OkServiceResponse::class.java,
            )

            if (response.data != null && response.data.isNotEmpty()) {
                val totalPageStr = response.data[0].totalPage
                if (!totalPageStr.isNullOrEmpty()) {
                    totalPage = totalPageStr.toInt()
                }
                events.addAll(response.data[0].transactionLists)
                reachedPreviousRead = compareEventsWithLastRead(events, lastBlockRead)
            } else {
                break
            }
            page++
        } while (page <= totalPage && !reachedPreviousRead)

        val etherscanEvents = ArrayList<EtherscanEvent>()
        for (event in events) {
            try {
                val isNft = tfType == TransferFetchType.ERC_721 || tfType == TransferFetchType.ERC_1155
                etherscanEvents.add(event.getEtherscanTransferEvent(isNft))
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        return etherscanEvents.toTypedArray()
    }

    /**
     * Fetches token balances from OKLink, caching per-chain checks per address.
     */
    fun getTokensForChain(
        chainId: Long,
        address: String,
        tokenType: OkProtocolType,
    ): Single<List<OkToken>> {
        val tokens = ArrayList<OkToken>()
        if (!supportsChain(chainId) || !canCheckChain(chainId, address, tokenType)) {
            return Single.fromCallable { tokens }
        }

        return Single.fromCallable {
            var page = 1
            var totalPage = 0

            val thisChainCheck = getCheckList(address)
            val existingCheck = thisChainCheck.get(chainId, 0)
            val byteEntry = 2 xor tokenType.ordinal
            thisChainCheck.put(chainId, existingCheck or byteEntry)

            do {
                val response = Gson().fromJson(
                    fetchTokens(chainId, address, tokenType, page.toString()),
                    OkServiceResponse::class.java,
                )

                if (response.data != null && response.data.isNotEmpty()) {
                    val totalPageStr = response.data[0].totalPage
                    if (!totalPageStr.isNullOrEmpty()) {
                        totalPage = totalPageStr.toInt()
                    }
                    tokens.addAll(response.data[0].tokenList)
                } else {
                    break
                }
                page++
            } while (page <= totalPage)

            tokens
        }
    }

    private fun getCheckList(address: String): LongSparseArray<Int> {
        return networkChecked[address] ?: LongSparseArray<Int>().also { networkChecked[address] = it }
    }

    /**
     * Calls the OKLink API for token balances.
     */
    fun fetchTokens(
        chainId: Long,
        address: String,
        tokenType: OkProtocolType,
        page: String,
    ): String {
        val url =
            Uri.Builder()
                .encodedPath("$BASE_URL/v5/explorer/address/token-balance")
                .appendQueryParameter("address", address)
                .appendQueryParameter("chainShortName", getChainShortName(chainId))
                .appendQueryParameter("protocolType", tokenType.value)
                .appendQueryParameter("limit", LIMIT)
                .appendQueryParameter("page", page)
                .build()
                .toString()
        return executeRequest(url, true)
    }

    private fun compareEventsWithLastRead(events: List<OkxEvent>, lastBlockRead: Long): Boolean {
        for (event in events) {
            if (!event.height.isNullOrEmpty()) {
                val height = event.height.orEmpty().toLong()
                if (height < lastBlockRead) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Calls the OKLink transaction API and returns the raw JSON.
     */
    fun fetchTransactions(
        chainId: Long,
        address: String,
        protocolType: String,
        page: String,
    ): String {
        val url =
            Uri.Builder()
                .encodedPath("$BASE_URL/v5/explorer/address/transaction-list")
                .appendQueryParameter("address", address)
                .appendQueryParameter("protocolType", protocolType)
                .appendQueryParameter("chainShortName", getChainShortName(chainId))
                .appendQueryParameter("limit", LIMIT)
                .appendQueryParameter("page", page)
                .build()
                .toString()
        return executeRequest(url, false)
    }

    /**
     * Maps OKLink token details to the shared [TokenInfo] model.
     */
    fun getTokenInfo(chainId: Long, contractAddress: String): TokenInfo? {
        val details = getTokenDetails(chainId, contractAddress) ?: return null
        return TokenInfo(
            details.tokenContractAddress,
            details.tokenFullName,
            details.token,
            details.precision.toInt(),
            true,
            chainId,
        )
    }

    /**
     * Retrieves the first OKLink token detail for the supplied contract.
     */
    fun getTokenDetails(chainId: Long, contractAddress: String): TokenListReponse.TokenDetails? {
        val response =
            Gson().fromJson(fetchTokenDetails(chainId, contractAddress), TokenListReponse::class.java)
        if (response.data.isNotEmpty()) {
            val tokenList = response.data[0].tokenList
            if (tokenList.isNotEmpty()) {
                return tokenList[0]
            }
        }
        return null
    }

    fun fetchTokenDetails(chainId: Long, contractAddress: String): String {
        val url =
            Uri.Builder()
                .encodedPath("$BASE_URL/v5/explorer/token/token-list")
                .appendQueryParameter("tokenContractAddress", contractAddress)
                .appendQueryParameter("chainShortName", getChainShortName(chainId))
                .build()
                .toString()
        return executeRequest(url, false)
    }

    private fun getOkxFetchType(tfType: TransferFetchType): String =
        when (tfType) {
            TransferFetchType.ERC_721 -> "token_721"
            TransferFetchType.ERC_1155 -> "token_1155"
            else -> "token_20"
        }

    private fun canCheckChain(networkId: Long, address: String, checkType: OkProtocolType): Boolean {
        val addressCheck = networkChecked[address]
        return addressCheck == null || (addressCheck.get(networkId, 0) and (2 xor checkType.ordinal)) == 1
    }

    private fun buildRequest(api: String, useAlt: Boolean): Request {
        return Request.Builder()
            .url(api)
            .header("User-Agent", "Chrome/74.0.3729.169")
            .addHeader("Content-Type", "application/json")
            .addHeader(
                "Ok-Access-Key",
                if (useAlt) KeyProviderFactory.get().getOkLBKey() else KeyProviderFactory.get().getOkLinkKey(),
            )
            .get()
            .build()
    }

    private fun executeRequest(api: String, useAlt: Boolean): String {
        return try {
            httpClient.newCall(buildRequest(api, useAlt)).execute().use { response ->
                val responseBody: ResponseBody? = response.body
                if (response.isSuccessful) {
                    responseBody?.string() ?: JsonUtils.EMPTY_RESULT
                } else {
                    responseBody?.string() ?: JsonUtils.EMPTY_RESULT
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
            e.message ?: JsonUtils.EMPTY_RESULT
        }
    }

    private fun getChainShortName(chainId: Long): String = shortNames.get(chainId, "")

    companion object {
        private const val TAG = "OKLINK"
        private const val BASE_URL = "https://www.oklink.com/api"
        private const val LIMIT = "50"

        private val shortNames: LongSparseArray<String> =
            LongSparseArray<String>().apply {
                put(MAINNET_ID, "ETH")
                put(OKX_ID, "OKTC")
                put(POLYGON_AMOY_ID, "AMOY_TESTNET")
                put(ARBITRUM_MAIN_ID, "ARBITRUM")
                put(BINANCE_MAIN_ID, "BSC")
                put(KLAYTN_ID, "KLAYTN")
                put(CLASSIC_ID, "ETC")
                put(POLYGON_ID, "POLYGON")
                put(AVALANCHE_ID, "AVAXC")
                put(FANTOM_ID, "FTM")
                put(OPTIMISTIC_MAIN_ID, "OP")
                put(LINEA_ID, "LINEA")
                put(BASE_MAINNET_ID, "BASE")
                put(SEPOLIA_TESTNET_ID, "SEPOLIA_TESTNET")
            }

        private val networkChecked: MutableMap<String, LongSparseArray<Int>> = HashMap()

        @Volatile
        private var instance: OkLinkService? = null

        /**
         * Returns a singleton service bound to the provided [OkHttpClient].
         */
        fun get(httpClient: OkHttpClient): OkLinkService {
            return instance ?: synchronized(this) {
                instance ?: OkLinkService(httpClient).also { instance = it }
            }
        }

        fun supportsChain(chainId: Long): Boolean = shortNames.get(chainId, "").isNotEmpty()
    }
}
