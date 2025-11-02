package com.alphawallet.app.entity.ticker

import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Locale

/**
 * Created by JB on 21/04/2021.
 */
class CoinGeckoTicker(address: String, fiatPrice: Double, fiatChange: BigDecimal) :
    BaseTicker(address, fiatPrice, fiatChange) {
    companion object {
        @Throws(JSONException::class)
        fun buildTickerList(
            jsonData: String,
            currencyIsoSymbol: String,
            currentConversionRate: Double
        ): List<CoinGeckoTicker> {
            val res: MutableList<CoinGeckoTicker> = ArrayList()
            val data = JSONObject(jsonData)
            if (data.names() == null) return res

            for (i in 0..<data.names().length()) {
                val address = data.names()[i].toString()
                val obj = data.getJSONObject(address)
                var fiatPrice = 0.0
                val fiatChangeStr: String
                if (obj.has(currencyIsoSymbol.lowercase(Locale.getDefault()))) {
                    fiatPrice = obj.getDouble(currencyIsoSymbol.lowercase(Locale.getDefault()))
                    fiatChangeStr =
                        obj.getString(currencyIsoSymbol.lowercase(Locale.getDefault()) + "_24h_change")
                } else if (obj.has("usd")) {
                    fiatPrice = obj.getDouble("usd") * currentConversionRate
                    fiatChangeStr = obj.getString("usd_24h_change")
                } else {
                    continue  //handle empty/corrupt returns
                }

                res.add(CoinGeckoTicker(address, fiatPrice, getFiatChange(fiatChangeStr)))
            }

            return res
        }
    }
}
