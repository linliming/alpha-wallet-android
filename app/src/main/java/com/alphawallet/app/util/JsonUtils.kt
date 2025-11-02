package com.alphawallet.app.util

object JsonUtils {
    const val EMPTY_RESULT: String = "{\"noresult\":[]}"

    fun hasAssets(jsonData: String?): Boolean {
        return jsonData != null && jsonData.length >= 10 &&
                jsonData.contains("assets")
    }

    fun isEmpty(jsonData: String): Boolean {
        return jsonData.equals(EMPTY_RESULT, ignoreCase = true)
    }

    fun isValidAsset(jsonData: String): Boolean {
        return jsonData.isNotEmpty() && jsonData.length > 15 &&
                jsonData.contains("name")
    }

    fun isValidCollection(jsonData: String): Boolean {
        return jsonData.isNotEmpty() && jsonData.length > 15 &&
                jsonData.contains("collection")
    }
}
