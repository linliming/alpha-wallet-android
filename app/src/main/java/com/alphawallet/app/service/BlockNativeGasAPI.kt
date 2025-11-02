package com.alphawallet.app.service

import android.text.TextUtils
import com.alphawallet.app.entity.EIP1559FeeOracleResult
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.KeyProviderFactory
import com.alphawallet.app.util.BalanceUtils
import com.alphawallet.app.util.JsonUtils
import com.google.gson.Gson
import io.reactivex.Single
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Retrieves EIP-1559 fee recommendations from the BlockNative API.
 */
class BlockNativeGasAPI private constructor(
    private val httpClient: OkHttpClient,
) {

    /**
     * Returns any cached BlockNative results, or fetches them using the supplied `httpClient`.
     */
    fun get1559GasEstimates(
        existing: Map<Int, EIP1559FeeOracleResult>,
        chainId: Long,
    ): Single<Map<Int, EIP1559FeeOracleResult>> =
        if (existing.isNotEmpty()) {
            Single.fromCallable { existing }
        } else {
            val oracleAPI = EthereumNetworkBase.getBlockNativeOracle(chainId)
            Single.fromCallable { buildOracleResult(executeRequest(oracleAPI)) }
        }

    private fun buildOracleResult(oracleReturn: String): Map<Int, EIP1559FeeOracleResult> {
        val results = HashMap<Int, EIP1559FeeOracleResult>()
        try {
            val prices = JSONObject(oracleReturn)
            val blockPrices = prices.getJSONArray("blockPrices")
            val blockPrice0 = blockPrices.getJSONObject(0)
            val baseFeePerGasStr = blockPrice0.getString("baseFeePerGas")
            val baseFeePerGas = BigDecimal(baseFeePerGasStr)
            val baseFeePerGasWei = BalanceUtils.gweiToWei(baseFeePerGas)
            val estimatedPrices = blockPrice0.getJSONArray("estimatedPrices").toString()
            val priceElements = Gson().fromJson(estimatedPrices, Array<PriceElement>::class.java)

            results[0] = EIP1559FeeOracleResult(priceElements[0].getFeeOracleResult(baseFeePerGasWei))
            results[1] = EIP1559FeeOracleResult(priceElements[2].getFeeOracleResult(baseFeePerGasWei))
            results[2] = EIP1559FeeOracleResult(priceElements[3].getFeeOracleResult(baseFeePerGasWei))
            results[3] = EIP1559FeeOracleResult(priceElements[4].getFeeOracleResult(baseFeePerGasWei))
        } catch (e: JSONException) {
            Timber.w(e)
        }
        return results
    }

    private fun executeRequest(api: String?): String =
        if (!TextUtils.isEmpty(api)) {
            try {
                httpClient.newCall(buildRequest(api!!)).execute().use { response ->
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
        } else {
            JsonUtils.EMPTY_RESULT
        }

    private fun buildRequest(api: String): Request =
        Request.Builder()
            .url(api)
            .header("Content-Type", "application/json")
            .addHeader("Authorization", KeyProviderFactory.get().getBlockNativeKey())
            .get()
            .build()

    private class PriceElement {
        var confidence: String? = null
        var price: String? = null
        var maxPriorityFeePerGas: String? = null
        var maxFeePerGas: String? = null

        fun getMaxPriorityFeePerGasWei(): BigInteger = elementToWei(maxPriorityFeePerGas)
        fun getMaxFeePerGasWei(): BigInteger = elementToWei(maxFeePerGas)

        private fun elementToWei(value: String?): BigInteger {
            return try {
                val gweiValue = BigDecimal(value)
                BalanceUtils.gweiToWei(gweiValue)
            } catch (e: Exception) {
                BigInteger.ZERO
            }
        }

        fun getFeeOracleResult(baseFee: BigInteger): EIP1559FeeOracleResult {
            return EIP1559FeeOracleResult(
                getMaxFeePerGasWei(),
                getMaxPriorityFeePerGasWei(),
                baseFee,
            )
        }
    }

    companion object {
        @Volatile
        private var instance: BlockNativeGasAPI? = null

        /**
         * Supplies a singleton instance for interacting with the BlockNative API.
         */
        fun get(httpClient: OkHttpClient): BlockNativeGasAPI {
            return instance ?: synchronized(this) {
                instance ?: BlockNativeGasAPI(httpClient).also { instance = it }
            }
        }
    }
}
