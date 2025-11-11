package com.alphawallet.app.walletconnect.entity

import com.walletconnect.web3.wallet.client.Wallet


/**
 * Created by JB on 21/11/2022.
 */
object EthSignRequest {
    fun getSignRequest(sessionRequest: Wallet.Model.SessionRequest): BaseRequest? {
        var signRequest: BaseRequest? = null

        when (sessionRequest.request.method) {
            "eth_sign" ->                 // see https://docs.walletconnect.org/json-rpc-api-methods/ethereum
                // WalletConnect shouldn't provide access to deprecated eth_sign, as it can be used to scam people
                signRequest = SignRequest(sessionRequest.request.params)

            "personal_sign" -> signRequest =
                SignPersonalMessageRequest(sessionRequest.request.params)

            "eth_signTypedData", "eth_signTypedData_v4" -> signRequest =
                SignTypedDataRequest(sessionRequest.request.params)

            else -> {}
        }

        return signRequest
    }
}
