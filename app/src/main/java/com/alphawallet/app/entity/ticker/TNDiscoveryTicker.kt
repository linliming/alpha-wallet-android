package com.alphawallet.app.entity.ticker

import com.alphawallet.app.entity.tokendata.TokenTicker
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal


class TNDiscoveryTicker : BaseTicker {
    constructor(address: String, fiatPrice: Double, fiatChange: BigDecimal) : super(
        address,
        fiatPrice,
        fiatChange
    )

    constructor(result: JSONObject, address: String) : super(
        address,
        result.getDouble("usdPrice"),
        getFiatChange(result.getString("24hrPercentChange"))
    )

    companion object {
        @Throws(JSONException::class)
        fun toTokenTickers(
            tickers: MutableMap<String?, TokenTicker?>,
            result: JSONArray,
            currentCurrencySymbolTxt: String,
            currentConversionRate: Double
        ) {
            for (i in 0..<result.length()) {
                val thisTickerObject = result.getJSONObject(i)
                val thisTicker =
                    TNDiscoveryTicker(thisTickerObject, thisTickerObject.getString("contract"))
                tickers[thisTickerObject.getString("contract")] =
                    thisTicker.toTokenTicker(currentCurrencySymbolTxt, currentConversionRate)
            }
        }
    }
}
