package com.alphawallet.app.service

import com.alphawallet.app.C
import com.alphawallet.app.entity.Result
import com.alphawallet.app.util.JsonUtils
import com.google.gson.Gson
import io.reactivex.Single
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.web3j.utils.Numeric
import timber.log.Timber
import java.util.Objects
import java.util.concurrent.TimeUnit

/**
 * Simple client for resolving function selectors to their 4byte directory signature strings.
 */
class SignatureLookupService {
    private val httpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(C.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(C.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    /**
     * Looks up the first matching text signature for the hexadecimal payload.
     */
    fun getFunctionName(payload: String): Single<String> {
        return Single.fromCallable {
            val request = buildRequest(payload)
            val response = executeRequest(request)
            getTextSignature(response)
        }
    }

    /**
     * Extracts the first signature from the 4byte directory response.
     */
    fun getTextSignature(response: String): String {
        val result = Gson().fromJson(response, Result::class.java)
        return result?.first?.text_signature ?: ""
    }

    private fun buildRequest(payload: String): Request {
        val url = BASE_API_URL + getFirstFourBytes(payload)
        return Request.Builder()
            .url(url)
            .header("User-Agent", "Chrome/74.0.3729.169")
            .addHeader("Content-Type", "application/json")
            .get()
            .build()
    }

    private fun executeRequest(request: Request): String {
        return try {
            httpClient.newCall(request).execute().use { response ->
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

    private fun getFirstFourBytes(payload: String): String {
        return Numeric.prependHexPrefix(payload).substring(0, 10)
    }

    companion object {
        private const val BASE_API_URL = "https://www.4byte.directory/api/v1/signatures/?hex_signature="
    }
}
