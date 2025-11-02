package com.alphawallet.app.service

import com.alphawallet.app.entity.EIP1559FeeOracleResult
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.repository.HttpServiceHelper
import com.alphawallet.app.repository.KeyProviderFactory
import com.alphawallet.app.util.BalanceUtils
import io.reactivex.Single
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.util.HashMap

/**
 * Retrieves gas price estimates from Infura's gas oracle REST API.
 */
object InfuraGasAPI {

    /**
     * Fetches EIP-1559 fee recommendations for the supplied chain.
     *
     * @return a [Single] emitting cached results (when available) or the parsed API response.
     */
    fun get1559GasEstimates(
        chainId: Long,
        httpClient: OkHttpClient,
    ): Single<Map<Int, EIP1559FeeOracleResult>> =
        Single.fromCallable {
            var gasMap: Map<Int, EIP1559FeeOracleResult> = HashMap()

            val gasOracleAPI = EthereumNetworkRepository.getGasOracle(chainId)
            val infuraKey = KeyProviderFactory.get().getInfuraKey()
            val infuraSecret = KeyProviderFactory.get().getInfuraSecret()

            if (gasOracleAPI.isNullOrEmpty() || infuraKey.isNullOrEmpty() || infuraSecret.isNullOrEmpty()) {
                return@fromCallable gasMap
            }

            val requestBuilder = Request.Builder()
                .url(gasOracleAPI)
                .get()

            HttpServiceHelper.addInfuraGasCredentials(requestBuilder, infuraSecret)

            try {
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.code / 200 == 1) {
                        val body = response.body
                        val result = body?.string()
                        if (!result.isNullOrEmpty()) {
                            gasMap = readGasMap(result)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e)
            }

            gasMap
        }

    /**
     * Parses the JSON returned by the Infura gas oracle into a map keyed by speed tier.
     */
    private fun readGasMap(apiReturn: String): Map<Int, EIP1559FeeOracleResult> {
        val gasMap = HashMap<Int, EIP1559FeeOracleResult>()
        try {
            var baseFee = BigDecimal.ZERO
            val result = JSONObject(apiReturn)
            if (result.has("estimatedBaseFee")) {
                baseFee = BigDecimal(result.getString("estimatedBaseFee"))
            }

            val low = readFeeResult(result, "low", baseFee)
            val medium = readFeeResult(result, "medium", baseFee)
            val high = readFeeResult(result, "high", baseFee)

            if (low == null || medium == null || high == null) {
                return gasMap
            }

            val rapidPriorityFee =
                BigDecimal(high.priorityFee).multiply(BigDecimal.valueOf(1.2)).toBigInteger()
            val rapid = EIP1559FeeOracleResult(
                high.maxFeePerGas,
                rapidPriorityFee,
                BalanceUtils.gweiToWei(baseFee),
            )

            gasMap[0] = rapid
            gasMap[1] = high
            gasMap[2] = medium
            gasMap[3] = low
        } catch (e: JSONException) {
            Timber.w(e)
        }

        return gasMap
    }

    /**
     * Extracts a single fee tier (rapid/high/medium/low) from the oracle payload.
     */
    private fun readFeeResult(
        result: JSONObject,
        speed: String,
        baseFee: BigDecimal,
    ): EIP1559FeeOracleResult? {
        return try {
            if (result.has(speed)) {
                val thisSpeed = result.getJSONObject(speed)
                val maxFeePerGas = BigDecimal(thisSpeed.getString("suggestedMaxFeePerGas"))
                val priorityFee = BigDecimal(thisSpeed.getString("suggestedMaxPriorityFeePerGas"))
                EIP1559FeeOracleResult(
                    BalanceUtils.gweiToWei(maxFeePerGas),
                    BalanceUtils.gweiToWei(priorityFee),
                    BalanceUtils.gweiToWei(baseFee),
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e("Infura GasOracle read failing; please adjust your Infura API settings.")
            null
        }
    }
}
