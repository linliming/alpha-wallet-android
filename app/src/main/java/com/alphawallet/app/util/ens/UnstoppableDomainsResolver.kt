package com.alphawallet.app.util.ens

import android.net.Uri
import android.text.TextUtils
import com.alphawallet.app.entity.unstoppable.GetRecordsResult
import com.alphawallet.app.repository.KeyProviderFactory.get
import com.alphawallet.ethereum.EthereumNetworkBase
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

class UnstoppableDomainsResolver(private val client: OkHttpClient, private val chainId: Long) :
    Resolvable {
    private val keyProvider = get()

    @Throws(Exception::class)
    override fun resolve(domainName: String?): String? {
        val builder = Uri.Builder()
        builder.encodedPath(GET_RECORDS_FOR_DOMAIN)
            .appendEncodedPath(domainName)

        val request = Request.Builder()
            .header("Authorization", "Bearer " + keyProvider.getUnstoppableDomainsKey())
            .url(builder.build().toString())
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body
                if (responseBody != null) {
                    val result = Gson().fromJson(
                        responseBody.string(),
                        GetRecordsResult::class.java
                    )
                    response.close()
                    return getAddressFromRecords(result.records!!, chainId)
                }
                response.close()
                return ""
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        return ""
    }

    private fun getAddressFromRecords(records: HashMap<String, String>, chainId: Long): String? {
        val ethAddress = records.getOrDefault("crypto.ETH.address", "")
        val maticAddress = records.getOrDefault("crypto.MATIC.version.MATIC.address", "")
        if (chainId == EthereumNetworkBase.MAINNET_ID) {
            return ethAddress
        } else if (chainId == EthereumNetworkBase.POLYGON_ID) {
            return maticAddress
        } else {
            if (!TextUtils.isEmpty(ethAddress)) {
                return ethAddress
            } else if (!TextUtils.isEmpty(maticAddress)) {
                return maticAddress
            }
        }
        return ""
    }

    companion object {
        private const val GET_RECORDS_FOR_DOMAIN = "https://resolve.unstoppabledomains.com/domains/"
    }
}
