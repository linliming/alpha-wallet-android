package com.alphawallet.app.repository.entity

import com.alphawallet.ethereum.EthereumNetworkBase
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class RealmTokenScriptData : RealmObject() {

    @PrimaryKey
    var instanceKey: String? = null
    var fileHash: String? = null
    var filePath: String? = null
    private var names: String? = null
    private var viewList: String? = null
    var ipfsPath: String? = null
    private var hasEvents: Boolean = false
    var schemaUID: String? = null

    fun getChainId(): Long {
        val key = instanceKey ?: return EthereumNetworkBase.MAINNET_ID
        val parts = key.split("-")
        if (parts.size < 2) {
            return EthereumNetworkBase.MAINNET_ID
        }
        val chainComponent = parts[1]
        val firstChar = chainComponent.firstOrNull() ?: return EthereumNetworkBase.MAINNET_ID
        return if (firstChar.isDigit()) {
            chainComponent.toLongOrNull() ?: EthereumNetworkBase.MAINNET_ID
        } else {
            EthereumNetworkBase.MAINNET_ID
        }
    }

    fun getOriginTokenAddress(): String {
        val key = instanceKey ?: return ""
        val parts = key.split("-")
        return parts.firstOrNull().orEmpty()
    }

    fun getName(count: Int): String? {
        val nameMap = getValueMap(names)
        var value: String? = null
        when (count) {
            1 -> value = nameMap["one"]
            2 -> {
                value = nameMap["two"]
                if (value == null) {
                    value = getName(1)
                }
            }
            else -> {
                value = nameMap["other"]
                if (value == null) {
                    value = nameMap["two"]
                }
                if (value == null) {
                    value = getName(1)
                }
            }
        }

        if (value == null && nameMap.isNotEmpty()) {
            value = nameMap.values.firstOrNull()
        }

        return value
    }

    private fun getValueMap(values: String?): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (values.isNullOrEmpty()) {
            return result
        }

        val entries = values.split(",")
        var expectKey = true
        var key: String? = null

        for (entry in entries) {
            if (expectKey) {
                key = entry
                expectKey = false
            } else {
                if (key != null) {
                    result[key] = entry
                }
                expectKey = true
            }
        }

        return result
    }

    fun setNames(names: String?) {
        this.names = names
    }

    fun getViewList(): List<String> {
        val stored = viewList ?: return emptyList()
        if (stored.isEmpty()) {
            return emptyList()
        }
        return stored.split(",")
    }

    fun setViewList(viewList: String?) {
        this.viewList = viewList
    }

    fun hasEvents(): Boolean = hasEvents

    fun setHasEvents(hasEvents: Boolean) {
        this.hasEvents = hasEvents
    }
}
