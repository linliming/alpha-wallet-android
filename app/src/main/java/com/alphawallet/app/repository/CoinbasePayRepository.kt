package com.alphawallet.app.repository

import android.net.Uri
import android.text.TextUtils
import com.alphawallet.app.entity.coinbasepay.DestinationWallet
import com.alphawallet.app.util.CoinbasePayUtils

class CoinbasePayRepository : CoinbasePayRepositoryType {
    private val keyProvider: KeyProvider = KeyProviderFactory.get()

    override fun getUri(
        type: DestinationWallet.Type?,
        address: String?,
        list: List<String?>?
    ): String {
        val appId = keyProvider.getCoinbasePayAppId()
        if (TextUtils.isEmpty(appId)) {
            return ""
        } else {
            val builder = Uri.Builder()
            builder.scheme(SCHEME)
                .authority(AUTHORITY)
                .appendPath(BUY_PATH)
                .appendPath(SELECT_ASSET_PATH)
                .appendQueryParameter(RequestParams.APP_ID, keyProvider.getCoinbasePayAppId())
                .appendQueryParameter(
                    RequestParams.DESTINATION_WALLETS,
                    CoinbasePayUtils.getDestWalletJson(type, address, list)
                )

            return builder.build().toString()
        }
    }

    object Blockchains {
        const val ETHEREUM: String = "ethereum"
        const val SOLANA: String = "solana"
        const val AVALANCHE_C_CHAIN: String = "avalanche-c-chain"
    }

    private object RequestParams {
        const val APP_ID: String = "appId"
        const val ADDRESS: String = "address"
        const val DESTINATION_WALLETS: String = "destinationWallets"
        const val ASSETS: String = "assets"
        const val BLOCKCHAINS: String = "blockchains"
    }

    companion object {
        private const val SCHEME = "https"
        private const val AUTHORITY = "pay.coinbase.com"
        private const val BUY_PATH = "buy"
        private const val SELECT_ASSET_PATH = "select-asset"
    }
}
