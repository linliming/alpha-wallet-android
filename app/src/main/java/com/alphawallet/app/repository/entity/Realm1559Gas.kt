package com.alphawallet.app.repository.entity

import com.alphawallet.app.entity.EIP1559FeeOracleResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

/**
 * Created by JB on 19/01/2022.
 */
class Realm1559Gas : RealmObject() {
    @PrimaryKey
    private val chainId: Long = 0

    var timeStamp: Long = 0
        private set
    var resultData: String? = null //JSON format string
        private set

    val result: Map<Int, EIP1559FeeOracleResult>?
        get() {
            val entry = object :
                TypeToken<Map<Int?, EIP1559FeeOracleResult?>?>() {}.type
            return Gson().fromJson(
                resultData,
                entry
            )
        }

    fun setResultData(result: Map<Int, EIP1559FeeOracleResult>, ts: Long) {
        //form JSON string and write to DB
        resultData = Gson().toJson(result)
        timeStamp = ts
    }
}
