package com.alphawallet.app.service

import android.text.TextUtils
import com.alphawallet.app.entity.QueryResponse
import com.alphawallet.app.entity.tokenscript.TestScript
import com.alphawallet.app.util.Utils
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Fetches TokenScript resources via HTTP or IPFS, supporting fallback behaviour.
 */
class IPFSService(
    private val client: OkHttpClient,
) : IPFSServiceType {

    /**
     * Retrieves the textual content at [url], returning an empty string on failure.
     */
    override fun getContent(url: String?): String? {
        return try {
            val response = performIO(url, null)
            if (response?.isSuccessful == true) {
                response.body
            } else {
                ""
            }
        } catch (e: Exception) {
            Timber.w(e)
            ""
        }
    }

    /**
     * Performs the network request for the supplied [url], optionally applying custom [headers].
     *
     * @throws IOException when the request cannot be completed.
     */
    @Throws(IOException::class)
    override fun performIO(
        url: String?,
        headers: Array<String?>?,
    ): QueryResponse? {
        var targetUrl = url?.trim()
        if (targetUrl.isNullOrEmpty() || !Utils.isValidUrl(targetUrl)) {
            throw IOException("URL not valid")
        }

        return if (Utils.isIPFS(targetUrl)) {
            getFromIPFS(targetUrl)
        } else {
            get(targetUrl, headers)
        }
    }

    /**
     * Executes a standard HTTP GET request, applying optional headers.
     */
    @Throws(IOException::class)
    private fun get(url: String, headers: Array<String?>?): QueryResponse {
        val builder = Request.Builder()
            .url(url)
            .get()

        if (headers != null) {
            addHeaders(builder, headers)
        }

        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return QueryResponse(response.code, body)
        }
    }

    /**
     * Resolves IPFS URIs (with test-code shortcuts) using the configured gateways.
     */
    @Throws(IOException::class)
    private fun getFromIPFS(url: String): QueryResponse? {
        if (isTestCode(url)) {
            return loadTestCode()
        }

        var tryIPFS = Utils.resolveIPFS(url, Utils.IPFS_INFURA_RESOLVER)
        return try {
            get(tryIPFS, null)
        } catch (timeout: SocketTimeoutException) {
            tryIPFS = Utils.resolveIPFS(url, Utils.IPFS_INFURA_RESOLVER)
            get(tryIPFS, null)
        }
    }

    @Throws(IOException::class)
    private fun addHeaders(builder: Request.Builder, headers: Array<String?>) {
        if (headers.size % 2 != 0) {
            throw IOException("Headers must be even value: [{name, value}, {...}]")
        }

        var name: String? = null
        for (header in headers) {
            if (name == null) {
                if (header == null) {
                    throw IOException("Header name missing")
                }
                name = header
            } else {
                if (header == null) {
                    throw IOException("Header value missing for $name")
                }
                builder.addHeader(name, header)
                name = null
            }
        }
    }

    /**
     * Returns true when the URL corresponds to a static test TokenScript.
     */
    private fun isTestCode(url: String?): Boolean {
        return !url.isNullOrEmpty() && url.endsWith("QmXXLFBeSjXAwAhbo1344wJSjLgoUrfUK9LE57oVubaRRp")
    }

    /**
     * Loads the embedded TokenScript resource used by the certificate test.
     */
    private fun loadTestCode(): QueryResponse {
        return QueryResponse(200, TestScript.testScriptXXLF)
    }
}
