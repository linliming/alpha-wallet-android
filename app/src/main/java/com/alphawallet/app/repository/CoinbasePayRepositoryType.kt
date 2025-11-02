package com.alphawallet.app.repository

import com.alphawallet.app.entity.coinbasepay.DestinationWallet

interface CoinbasePayRepositoryType {
    fun getUri(type: DestinationWallet.Type?, json: String?, list: List<String?>?): String?
}
