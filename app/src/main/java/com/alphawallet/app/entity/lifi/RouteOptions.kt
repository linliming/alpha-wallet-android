package com.alphawallet.app.entity.lifi

import com.google.gson.Gson
import org.json.JSONException
import org.json.JSONObject

class RouteOptions {
    var integrator: String? = null
    var slippage: String? = null
    var exchanges: Exchanges
    var order: String? = null

    init {
        this.exchanges = Exchanges()
    }

    class Exchanges {
        var allow: List<String> = ArrayList()
    }

    @get:Throws(JSONException::class)
    val json: JSONObject
        get() {
            val json = Gson().toJson(this)
            return JSONObject(json)
        }
}
