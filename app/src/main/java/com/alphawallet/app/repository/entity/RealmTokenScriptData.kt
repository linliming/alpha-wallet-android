package com.alphawallet.app.repository.entity

import android.text.TextUtils
import com.alphawallet.ethereum.EthereumNetworkBase
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.Arrays

/**
 * Created by JB on 17/08/2020.
 */
class RealmTokenScriptData : RealmObject() {
    @PrimaryKey
    private val instanceKey: String? = null
    var fileHash: String? = null // uniquely identify tokenscript - script MD5 hash
    var filePath: String? = null

    // CSV list of token names allowing for plurals. //TODO: replace with RealmMap when available
    private var names: String? = null

    // CSV list of event views //TODO: replace with RealmMap when available
    private var viewList: String? = null
    var ipfsPath: String? = null
    private var hasEvents = false // TokenScript has events
    var schemaUID: String? = null

    /**
     * 获取链ID
     *
     * 从instanceKey中解析链ID，格式为 "address-chainId"
     * 如果解析失败或instanceKey为空，返回主网ID
     *
     * @return 链ID，解析失败时返回主网ID
     */
    val chainId: Long
        get() {
            return try {
                // 1. 检查instanceKey是否为空
                val key = instanceKey ?: return EthereumNetworkBase.MAINNET_ID

                // 2. 按"-"分割instanceKey
                val parts = key.split("-", limit = 2)

                // 3. 检查分割结果是否有效
                if (parts.size < 2) {

                    return EthereumNetworkBase.MAINNET_ID
                }

                val chainIdPart = parts[1]

                // 4. 检查链ID部分是否为空
                if (chainIdPart.isBlank()) {

                    return EthereumNetworkBase.MAINNET_ID
                }

                // 5. 检查是否为数字
                if (chainIdPart.all { it.isDigit() }) {
                    chainIdPart.toLong()
                } else {

                    EthereumNetworkBase.MAINNET_ID
                }
            } catch (e: Exception) {

                EthereumNetworkBase.MAINNET_ID
            }
        }

    /**
     * 获取原始代币地址
     *
     * 从instanceKey中解析代币地址，格式为 "address-chainId"
     * 如果解析失败或instanceKey为空，返回空字符串
     *
     * @return 代币地址，解析失败时返回空字符串
     */
    val originTokenAddress: String
        get() {
            return try {
                // 1. 检查instanceKey是否为空
                val key = instanceKey ?: return ""

                // 2. 按"-"分割instanceKey
                val parts = key.split("-", limit = 2)

                // 3. 检查分割结果是否有效
                if (parts.isEmpty()) {

                    return ""
                }

                val addressPart = parts[0]

                // 4. 检查地址部分是否为空
                if (addressPart.isBlank()) {

                    return ""
                }

                addressPart
            } catch (e: Exception) {

                ""
            }
        }

    fun getName(count: Int): String? {
        val nameMap = getValueMap(names)
        var value: String? = null
        when (count) {
            1 -> if (nameMap.containsKey("one")) value = nameMap["one"]
            2 -> {
                if (value == null) value = nameMap["two"]
                if (value == null) value = getName(1) // still don't have anything, try singular
            }

            else -> {
                value = nameMap["other"]
                if (value == null) value = nameMap["two"]
                if (value == null) value = getName(1)
            }
        }

        if (value == null && nameMap.values.size > 0) {
            value = nameMap.values.iterator().next()
        }

        return value
    }

    /**
     * 解析键值对映射
     *
     * 从CSV格式的字符串中解析键值对，格式为 "key1,value1,key2,value2"
     *
     * @param values CSV格式的键值对字符串
     * @return 键值对映射
     */
    private fun getValueMap(values: String?): Map<String?, String> {
        val nameMap: MutableMap<String?, String> = HashMap()

        // 1. 检查输入是否为空
        if (TextUtils.isEmpty(values)) {
            return nameMap
        }

        return try {
            // 2. 按逗号分割字符串
            val nameList = values!!.split(",")

            // 3. 检查分割结果是否有效
            if (nameList.isEmpty()) {
                return nameMap
            }

            // 4. 解析键值对
            var state = true
            var key: String? = null

            for (s in nameList) {
                val trimmed = s.trim()

                if (state) {
                    key = trimmed
                    state = false
                } else {
                    if (key != null && trimmed.isNotBlank()) {
                        nameMap[key] = trimmed
                    }
                    state = true
                }
            }

            nameMap
        } catch (e: Exception) {
            nameMap
        }
    }

    fun setNames(names: String?) {
        this.names = names
    }

    /**
     * 获取视图列表
     *
     * 从CSV格式的字符串中解析视图名称列表
     *
     * @return 视图名称列表
     */
    fun getViewList(): List<String> {
        val viewNames: MutableList<String> = ArrayList()

        // 1. 检查viewList是否为空
        if (TextUtils.isEmpty(viewList)) {
            return viewNames
        }

        return try {
            // 2. 按逗号分割字符串
            val views = viewList!!.split(",")

            // 3. 过滤空值并添加到列表
            views.forEach { view ->
                val trimmed = view.trim()
                if (trimmed.isNotBlank()) {
                    viewNames.add(trimmed)
                }
            }

            viewNames
        } catch (e: Exception) {
            viewNames
        }
    }

    fun setViewList(viewList: String?) {
        this.viewList = viewList
    }

    fun hasEvents(): Boolean = hasEvents

    fun setHasEvents(hasEvents: Boolean) {
        this.hasEvents = hasEvents
    }
}
