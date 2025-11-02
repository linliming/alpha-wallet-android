package com.alphawallet.app.service

import com.alphawallet.app.service.JsonValidator.validateAndGetStream
import com.alphawallet.ethereum.EthereumNetworkBase.KLAYTN_BAOBAB_ID
import com.alphawallet.ethereum.EthereumNetworkBase.KLAYTN_ID
import com.google.gson.JsonParseException
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.slf4j.LoggerFactory
import org.web3j.protocol.http.HttpService
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Random

/**
 * HttpService implementation that falls back across multiple URLs until a request succeeds.
 */
class AWHttpServiceWaterfall(
    private val urls: Array<String>,
    private val chainId: Long,
    private val httpClient: OkHttpClient,
    private val infuraKey: String?,
    private val infuraSecret: String?,
    private val klaytnKey: String?,
    private val includeRawResponse: Boolean,
) : HttpService(includeRawResponse) {

    private val random = Random()
    private val headers = HashMap<String, String>()

    /**
     * Executes the JSON-RPC request against the configured endpoints in a round-robin fashion.
     *
     * @throws IOException when all configured URLs fail to return a successful response.
     */
    @Throws(IOException::class)
    override fun performIO(request: String): InputStream {
        val startIndex = random.nextInt(urls.size)
        for (count in urls.indices) {
            val url = urls[(startIndex + count) % urls.size]
            try {
                val response = performSingleIO(url, request)
                if (response.isSuccessful) {
                    return processResponse(response)
                } else {
                    Timber.d("Response was %s, retrying...", response.code)
                }
            } catch (e: IOException) {
                log.warn("Request to {} failed: {}", url, e.message)
            }
        }
        throw IOException("All requests failed!")
    }

    /**
     * Hook for subclasses to inspect response headers.
     */
    override fun processHeaders(headers: Headers) {
        // Default implementation left intentionally blank.
    }

    /**
     * Adds or replaces a header that will be applied to subsequent requests.
     */
    override fun addHeader(key: String, value: String) {
        headers[key] = value
    }

    /**
     * Bulk-adds headers to the request map.
     */
    override fun addHeaders(headersToAdd: Map<String, String>) {
        headers.putAll(headersToAdd)
    }

    /**
     * Returns the mutable header map currently configured for requests.
     */
    override fun getHeaders(): HashMap<String, String> = headers

    /**
     * No-op close; retained for API compatibility.
     */
    @Throws(IOException::class)
    override fun close() {
        // No resources to dispose.
    }

    @Throws(IOException::class)
    private fun performSingleIO(url: String, request: String): Response {
        val requestBody = try {
            RequestBody.create(JSON_MEDIA_TYPE, request)
        } catch (_: JsonParseException) {
            RequestBody.create(MEDIA_TYPE_TEXT, "")
        }

        addRequiredSecrets(url)

        val httpRequest = Request.Builder()
            .url(url)
            .headers(buildHeaders())
            .post(requestBody)
            .build()

        return httpClient.newCall(httpRequest).execute()
    }

    @Throws(IOException::class)
    private fun processResponse(response: Response): InputStream {
        processHeaders(response.headers)
        if (!response.isSuccessful) {
            throw IOException("Unsuccessful response: ${response.code}")
        }

        val body = response.body
        return if (body != null) {
            buildInputStream(response, body)
        } else {
            buildNullInputStream()
        }
    }

    private fun buildNullInputStream(): InputStream {
        val jsonData = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x\"}"
        return ByteArrayInputStream(jsonData.toByteArray(StandardCharsets.UTF_8))
    }

    @Throws(IOException::class)
    private fun buildInputStream(response: Response, body: ResponseBody): InputStream {
        val inputStream = validateAndGetStream(body)
        if (!includeRawResponse) {
            return inputStream
        }

        val source: BufferedSource = body.source()
        source.request(Long.MAX_VALUE)
        val buffer: Buffer = source.buffer
        val size = buffer.size
        if (size > Int.MAX_VALUE) {
            throw UnsupportedOperationException("Non-integer input buffer size specified: $size")
        }

        val bufferedInputStream = BufferedInputStream(inputStream, size.toInt())
        bufferedInputStream.mark(inputStream.available())
        return bufferedInputStream
    }

    private fun addRequiredSecrets(url: String) {
        if (!infuraKey.isNullOrEmpty() && url.endsWith(infuraKey) && !infuraSecret.isNullOrEmpty()) {
            addHeader("Authorization", "Basic $infuraSecret")
        } else if (!klaytnKey.isNullOrEmpty() && (chainId == KLAYTN_BAOBAB_ID || chainId == KLAYTN_ID)) {
            addHeader("x-chain-id", chainId.toString())
            addHeader("Authorization", "Basic $klaytnKey")
        }
    }

    private fun buildHeaders(): Headers = headers.toHeaders()

    companion object {
        /**
         * Copied from [ConnectionSpec.APPROVED_CIPHER_SUITES].
         */
        @Suppress("SpellCheckingInspection")
        private val INFURA_CIPHER_SUITES = arrayOf(
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256,
            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA256,
        )

        private val INFURA_CIPHER_SUITE_SPEC: ConnectionSpec =
            ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .cipherSuites(*INFURA_CIPHER_SUITES)
                .build()

        /**
         * Connection specs applied when establishing the HTTP client connection.
         */
        val CONNECTION_SPEC_LIST: List<ConnectionSpec> =
            Arrays.asList(INFURA_CIPHER_SUITE_SPEC, ConnectionSpec.CLEARTEXT)

        val JSON_MEDIA_TYPE: MediaType? = "application/json; charset=utf-8".toMediaTypeOrNull()
        private val MEDIA_TYPE_TEXT: MediaType? = "text/xml; charset=UTF-8".toMediaTypeOrNull()
        const val DEFAULT_URL = "http://localhost:8545/"
        private val log = LoggerFactory.getLogger(HttpService::class.java)
    }
}
