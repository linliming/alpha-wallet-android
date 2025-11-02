package com.alphawallet.app.util

import com.alphawallet.app.entity.coinbasepay.DestinationWallet
import com.google.gson.Gson

object CoinbasePayUtils {
    fun getDestWalletJson(
        type: DestinationWallet.Type,
        address: String,
        value: List<String>?
    ): String {
        val destinationWallets: MutableList<DestinationWallet> = ArrayList()
        destinationWallets.add(DestinationWallet(type, address, value))
        return Gson().toJson(destinationWallets)
    }
}
