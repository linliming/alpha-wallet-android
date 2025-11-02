package com.alphawallet.app.service

import android.net.Uri
import com.alphawallet.app.C
import com.alphawallet.app.entity.lifi.RouteOptions
import com.alphawallet.app.entity.lifi.Token
import com.alphawallet.app.repository.SwapRepository
import com.alphawallet.app.util.BalanceUtils
import com.alphawallet.app.util.JsonUtils
import io.reactivex.Single
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.Set
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around the Li.Fi swap API endpoints used to fetch chains, quotes, and routes.
 */
class SwapService {

    /** Returns the available chains supported by the swap provider. */
    fun getChains(): Single<String> = Single.fromCallable { fetchChains() }

    /** Returns the available swap tools/exchanges supported by the provider. */
    fun getTools(): Single<String> = Single.fromCallable { fetchTools() }

    /** Fetches the list of token pairs that can be swapped between the supplied chains. */
    fun getConnections(from: Long, to: Long): Single<String> =
        Single.fromCallable { fetchPairs(from, to) }

    /** Retrieves a swap quote for the given source/destination tokens and account. */
    fun getQuote(
        source: Token,
        dest: Token,
        address: String,
        amount: String,
        slippage: String,
        allowExchanges: String,
    ): Single<String> =
        Single.fromCallable {
            fetchQuote(source, dest, address, amount, slippage, allowExchanges)
        }

    /** Retrieves detailed swap routes constrained to a list of exchanges. */
    fun getRoutes(
        source: Token,
        dest: Token,
        address: String,
        amount: String,
        slippage: String,
        exchanges: Set<String>,
    ): Single<String> =
        Single.fromCallable {
            fetchRoutes(source, dest, address, amount, slippage, exchanges)
        }

    /** Retrieves detailed swap routes using explicit identifiers. */
    fun getRoutes(
        fromChainId: String,
        toChainId: String,
        fromTokenAddress: String,
        toTokenAddress: String,
        fromAddress: String,
        fromAmount: String,
        slippage: String,
        exchanges: Set<String>,
    ): Single<String> =
        Single.fromCallable {
            fetchRoutes(
                fromChainId,
                toChainId,
                fromTokenAddress,
                toTokenAddress,
                fromAddress,
                fromAmount,
                slippage,
                exchanges,
            )
        }

    fun fetchChains(): String {
        val url = Uri.Builder().encodedPath(SwapRepository.FETCH_CHAINS).build().toString()
        return executeRequest(url)
    }

    fun fetchTools(): String {
        val url = Uri.Builder().encodedPath(SwapRepository.FETCH_TOOLS).build().toString()
        return executeRequest(url)
    }

    fun fetchPairs(fromChain: Long, toChain: Long): String {
        val url =
            Uri.Builder()
                .encodedPath(SwapRepository.FETCH_TOKENS)
                .appendQueryParameter("fromChain", fromChain.toString())
                .appendQueryParameter("toChain", toChain.toString())
                .build()
                .toString()
        return executeRequest(url)
    }

    fun fetchQuote(
        source: Token,
        dest: Token,
        address: String,
        amount: String,
        slippage: String,
        allowExchanges: String,
    ): String {
        val url =
            Uri.Builder()
                .encodedPath(SwapRepository.FETCH_QUOTE)
                .appendQueryParameter("fromChain", source.chainId.toString())
                .appendQueryParameter("toChain", dest.chainId.toString())
                .appendQueryParameter("fromToken", source.address)
                .appendQueryParameter("toToken", dest.address)
                .appendQueryParameter("fromAddress", address)
                .appendQueryParameter(
                    "fromAmount",
                    BalanceUtils.getRawFormat(amount, source.decimals),
                )
                .appendQueryParameter("allowExchanges", allowExchanges)
                .appendQueryParameter("slippage", slippage)
                .build()
                .toString()
        return executeRequest(url)
    }

    fun fetchRoutes(
        source: Token,
        dest: Token,
        address: String,
        amount: String,
        slippage: String,
        exchanges: Set<String>,
    ): String {
        val body = buildRouteBody(
            source.chainId.toString(),
            dest.chainId.toString(),
            source.address,
            dest.address,
            address,
            BalanceUtils.getRawFormat(amount, source.decimals),
            slippage,
            exchanges,
        )
        return executePostRequest(SwapRepository.FETCH_ROUTES, body)
    }

    fun fetchRoutes(
        fromChainId: String,
        toChainId: String,
        fromTokenAddress: String,
        toTokenAddress: String,
        fromAddress: String,
        fromAmount: String,
        slippage: String,
        exchanges: Set<String>,
    ): String {
        val body = buildRouteBody(
            fromChainId,
            toChainId,
            fromTokenAddress,
            toTokenAddress,
            fromAddress,
            fromAmount,
            slippage,
            exchanges,
        )
        return executePostRequest(SwapRepository.FETCH_ROUTES, body)
    }

    private fun buildRouteBody(
        fromChainId: String,
        toChainId: String,
        fromTokenAddress: String,
        toTokenAddress: String,
        fromAddress: String,
        fromAmount: String,
        slippage: String,
        exchanges: Set<String>,
    ): RequestBody? {
        val options = RouteOptions().apply {
            this.slippage = slippage
            this.exchanges.allow.addAll(exchanges)
        }
        return try {
            val json = JSONObject().apply {
                put("fromChainId", fromChainId)
                put("toChainId", toChainId)
                put("fromTokenAddress", fromTokenAddress)
                put("toTokenAddress", toTokenAddress)
                put("fromAddress", fromAddress)
                put("fromAmount", fromAmount)
                put("options", options.getJson())
            }
            RequestBody.create(JSON_MEDIA_TYPE, json.toString())
        } catch (e: JSONException) {
            Timber.e(e)
            null
        }
    }

    private fun buildRequest(api: String): Request =
        Request.Builder()
            .url(api)
            .header("User-Agent", USER_AGENT)
            .addHeader("Content-Type", "application/json")
            .get()
            .build()

    private fun buildPostRequest(api: String, requestBody: RequestBody?): Request =
        Request.Builder()
            .url(api)
            .header("User-Agent", USER_AGENT)
            .addHeader("Content-Type", "application/json")
            .post(requestBody ?: RequestBody.create(JSON_MEDIA_TYPE, ""))
            .build()

    private fun executeRequest(api: String): String {
        return try {
            httpClient.newCall(buildRequest(api)).execute().use { response ->
                val body: ResponseBody? = response.body
                if (response.isSuccessful) {
                    body?.string() ?: JsonUtils.EMPTY_RESULT
                } else {
                    body?.string() ?: JsonUtils.EMPTY_RESULT
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
            e.message ?: JsonUtils.EMPTY_RESULT
        }
    }

    private fun executePostRequest(api: String, body: RequestBody?): String {
        return try {
            httpClient.newCall(buildPostRequest(api, body)).execute().use { response ->
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

    companion object {
        private const val USER_AGENT = "Chrome/74.0.3729.169"
        private val JSON_MEDIA_TYPE: MediaType? = "application/json".toMediaTypeOrNull()

        private val httpClient: OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(C.CONNECT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(C.READ_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(C.WRITE_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
    }
}
