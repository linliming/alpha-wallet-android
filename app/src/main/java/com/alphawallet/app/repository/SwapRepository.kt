package com.alphawallet.app.repository

import android.content.Context
import com.alphawallet.app.entity.lifi.SwapProvider
import com.alphawallet.app.util.Utils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SwapRepository(private val context: Context) : SwapRepositoryType {

    private val gson = Gson()

    override fun getProviders(): List<SwapProvider> {
        val json = Utils.loadJSONFromAsset(context, SWAP_PROVIDERS_FILENAME) ?: return emptyList()
        val type = object : TypeToken<List<SwapProvider>>() {}.type
        val providers: List<SwapProvider>? = gson.fromJson(json, type)
        return providers ?: emptyList()
    }

    companion object {
        @JvmField val FETCH_CHAINS = "https://li.quest/v1/chains"
        @JvmField val FETCH_TOKENS = "https://li.quest/v1/connections"
        @JvmField val FETCH_QUOTE = "https://li.quest/v1/quote"
        @JvmField val FETCH_TOOLS = "https://li.quest/v1/tools"
        @JvmField val FETCH_ROUTES = "https://li.quest/v1/advanced/routes"
        private const val SWAP_PROVIDERS_FILENAME = "swap_providers_list.json"
    }
}
