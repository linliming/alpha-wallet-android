package com.alphawallet.app.repository

import android.text.TextUtils
import okhttp3.Request
import org.web3j.protocol.http.HttpService

object HttpServiceHelper {
    fun addRequiredCredentials(serviceUrl: String?, httpService: HttpService, infuraKey: String) {
        if (serviceUrl != null && serviceUrl.contains(EthereumNetworkBase.INFURA_DOMAIN) && !TextUtils.isEmpty(
                infuraKey
            )
        ) {
            httpService.addHeader("Authorization", "Basic $infuraKey")
        }
    }

    fun addRequiredCredentials(serviceUrl: String?, service: Request.Builder, infuraKey: String) {
        if (serviceUrl != null && serviceUrl.contains(EthereumNetworkBase.INFURA_DOMAIN) && !TextUtils.isEmpty(
                infuraKey
            )
        ) {
            service.addHeader("Authorization", "Basic $infuraKey")
        }
    }

    @JvmStatic
    fun addInfuraGasCredentials(service: Request.Builder, infuraSecret: String) {
        service.addHeader("Authorization", "Basic $infuraSecret")
    }
}
