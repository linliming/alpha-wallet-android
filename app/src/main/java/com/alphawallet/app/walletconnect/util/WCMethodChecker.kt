package com.alphawallet.app.walletconnect.util

import com.alphawallet.app.walletconnect.entity.WCMethod
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object WCMethodChecker {
    private val methods: List<String>

    init {
        val gson = Gson()
        val json = gson.toJson(WCMethod.entries.toTypedArray())
        val type = object : TypeToken<List<String?>?>() {
        }.type
        methods = gson.fromJson(json, type)
    }

    @JvmStatic
    fun includes(method: String): Boolean {
        return methods.contains(method)
    }
}
