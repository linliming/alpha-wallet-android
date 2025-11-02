package com.alphawallet.app.repository

import android.content.Context
import android.net.Uri
import com.alphawallet.app.C
import com.alphawallet.app.entity.OnRampContract
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.util.Utils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

class OnRampRepository(private val context: Context) : OnRampRepositoryType {

    private val keyProvider: KeyProvider = KeyProviderFactory.get()

    override fun getUri(address: String?, token: Token?): String? {
        val userAddress = address ?: return null
        val contract = getContract(token)
        val provider = contract.provider?.lowercase(Locale.ROOT) ?: RAMP
        val symbol = contract.symbol ?: ""

        return when (provider) {
            RAMP -> buildRampUri(userAddress, symbol).toString()
            else -> buildRampUri(userAddress, symbol).toString()
        }
    }

    override fun getContract(token: Token?): OnRampContract {
        token ?: return OnRampContract()

        val contractMap = getKnownContracts()
        val contract = contractMap[token.getAddress().lowercase(Locale.ROOT)]
        return when {
            contract != null -> contract
            token.isEthereum() -> OnRampContract(token.tokenInfo.symbol)
            else -> OnRampContract()
        }
    }

    private fun getKnownContracts(): Map<String, OnRampContract> {
        val json = Utils.loadJSONFromAsset(context, ONRAMP_CONTRACTS_FILE_NAME) ?: return emptyMap()
        val type = object : TypeToken<Map<String, OnRampContract>>() {}.type
        val result: Map<String, OnRampContract>? = Gson().fromJson(json, type)
        return result ?: emptyMap()
    }

    private fun buildRampUri(address: String, symbol: String): Uri {
        val builder = Uri.Builder()
            .scheme("https")
            .authority("buy.ramp.network")
            .appendQueryParameter("hostApiKey", keyProvider.getRampKey())
            .appendQueryParameter("hostLogoUrl", C.ALPHAWALLET_LOGO_URI)
            .appendQueryParameter("hostAppName", "AlphaWallet")
            .appendQueryParameter("userAddress", address)

        if (symbol.isNotEmpty()) {
            builder.appendQueryParameter("swapAsset", symbol)
        }

        return builder.build()
    }

    companion object {
        const val DEFAULT_PROVIDER = "Ramp"
        private const val RAMP = "ramp"
        private const val ONRAMP_CONTRACTS_FILE_NAME = "onramp_contracts.json"
    }
}
