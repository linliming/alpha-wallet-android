package com.alphawallet.app.entity

import org.json.JSONObject

/**
 * Created by JB on 21/04/2021.
 */
class DexGuruTicker
    (jsonData: String) {
    val address: String
    val usdPrice: Double
    val usdChange: Double
    val verified: Boolean

    init {
        val data = JSONObject(jsonData)

        address = data.getString("address")
        usdPrice = data.getDouble("priceUSD")
        usdChange = data.getDouble("priceChange24h")
        verified = data.getBoolean("verified")
    }
}
