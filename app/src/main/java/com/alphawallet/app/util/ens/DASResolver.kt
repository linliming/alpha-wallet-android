package com.alphawallet.app.util.ens

import com.alphawallet.app.util.das.DASBody
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.web3j.protocol.http.HttpService
import timber.log.Timber

class DASResolver(private val client: OkHttpClient) : Resolvable {
    override fun resolve(ensName: String?): String? {
        if (!EnsResolver.isValidEnsName(ensName)) return null

        val payload = DAS_PAYLOAD.replace(DAS_NAME, ensName!!)
        val requestBody = payload.toRequestBody(HttpService.JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(DAS_LOOKUP)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                //get result
                val result = response.body?.string().orEmpty()
                if (result.isEmpty()) return ""

                val dasResult = Gson().fromJson(result, DASBody::class.java)
                dasResult.buildMap()

                //find ethereum entry
                val ethLookup = dasResult.records["address.eth"]
                return if (ethLookup != null) {
                    ethLookup.address
                } else {
                    dasResult.ethOwner
                }
            }
        } catch (e: Exception) {
            Timber.tag("ENS").e(e)
        }

        return ""
    }

    companion object {
        private const val DAS_LOOKUP = "https://indexer.da.systems/"
        private const val DAS_NAME = "[DAS_NAME]"
        private const val DAS_PAYLOAD =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"das_searchAccount\",\"params\":[\"" + DAS_NAME + "\"]}"
    }
}
