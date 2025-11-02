package com.alphawallet.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.alphawallet.app.C
import com.alphawallet.app.entity.DApp
import com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_AMOY_ID
import com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_ID
import com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_TEST_ID
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * DApp 浏览器工具类，提供收藏/历史记录的本地持久化能力。
 * 迁移至 Kotlin 实现，同时增加简要注释并修复潜在静态分析警告。
 */
object DappBrowserUtils {

    private const val DAPPS_LIST_FILENAME = "dapps_list.json"
    private const val MY_DAPPS_FILE = "mydapps"
    private const val DAPPS_HISTORY_FILE = "dappshistory"
    private const val DEFAULT_HOMEPAGE = "https://alphawallet.com/browser/"
    private const val POLYGON_HOMEPAGE = "https://alphawallet.com/browser-item-category/polygon/"

    private val gson = Gson()

    /**
     * 保存用户收藏的 DApp。仅保留非官方站点，避免与默认站点重复。
     */
    @JvmStatic
    fun saveToPrefs(context: Context?, myDapps: List<DApp>) {
        if (context == null) return
        val primary = getPrimarySites(context)
        val primaryUrls = primary.map { it.url }.toSet()
        val customDapps =
            myDapps
                .filter { !primaryUrls.contains(it.url) && Utils.isValidUrl(it.url) }
        val json = gson.toJson(customDapps)
        storeJsonData(MY_DAPPS_FILE, json, context)
    }

    /**
     * 读取用户收藏的 DApp，自动合并默认站点。
     */
    @JvmStatic
    fun getMyDapps(context: Context?): List<DApp> {
        if (context == null) return emptyList()
        var json = loadJsonData(MY_DAPPS_FILE, context)
        if (json.isEmpty()) {
            json = loadFromPrefsLegacy(context, "my_dapps", MY_DAPPS_FILE).orEmpty()
        }
        val primary = getPrimarySites(context).toMutableList()
        if (json.isNotEmpty()) {
            val custom: MutableList<DApp> =
                runCatching {
                    gson.fromJson<List<DApp>>(json, object : TypeToken<List<DApp>>() {}.type)
                        ?.toMutableList()
                        ?: mutableListOf()
                }.getOrElse {
                    Timber.w(it, "Failed to parse custom dapps")
                    mutableListOf()
                }
            primary.addAll(custom)
        }
        return primary
    }

    /**
     * 获取浏览历史记录，并自动清理非法 URL。
     */
    @JvmStatic
    fun getBrowserHistory(context: Context?): List<DApp> {
        if (context == null) return emptyList()
        var historyJson = loadJsonData(DAPPS_HISTORY_FILE, context)
        if (historyJson.isEmpty()) {
            blankPrefEntry(context, C.DAPP_BROWSER_HISTORY)
        }

        val history: MutableList<DApp> =
            if (historyJson.isEmpty()) {
                mutableListOf()
            } else {
                runCatching {
                    gson.fromJson<List<DApp>>(historyJson, object : TypeToken<List<DApp>>() {}.type)
                        ?.toMutableList()
                        ?: mutableListOf()
                }.getOrElse {
                    Timber.w(it, "Failed to parse dapp history")
                    mutableListOf()
                }
            }

        val cleaned = parseDappHistory(history)
        if (cleaned.size != history.size) {
            val json = gson.toJson(cleaned)
            storeJsonData(DAPPS_HISTORY_FILE, json, context)
        }
        return cleaned
    }

    /**
     * 清空浏览历史记录。
     */
    @JvmStatic
    fun clearHistory(context: Context?) {
        if (context != null) {
            storeJsonData(DAPPS_HISTORY_FILE, "", context)
        }
    }

    /**
     * 新增历史记录，若存在同 URL 则保留最新。
     */
    @JvmStatic
    fun addToHistory(context: Context?, dapp: DApp?) {
        if (context == null || dapp == null || isWithinHomePage(dapp.url)) return
        val history = ArrayList<DApp>()
        history.add(dapp)
        history.addAll(getBrowserHistory(context))
        if (history.size > 1) {
            val duplicateIndex = history.indexOfFirst { it !== dapp && it.url == dapp.url }
            if (duplicateIndex >= 0) {
                history.removeAt(duplicateIndex)
            }
        }
        saveHistory(context, history)
    }

    /**
     * 按 URL 移除历史记录。
     */
    @JvmStatic
    fun removeFromHistory(context: Context?, url: String?) {
        if (context == null || url.isNullOrEmpty()) return
        val history = getBrowserHistory(context).toMutableList()
        history.removeAll { it.url == url }
        saveHistory(context, history)
    }

    /**
     * 获取站点 favicon。
     */
    @JvmStatic
    fun getIconUrl(url: String?): String =
        "https://www.google.com/s2/favicons?sz=128&domain=${url.orEmpty()}"

    /**
     * 获取默认的 DApp 列表。
     */
    @JvmStatic
    fun getDappsList(context: Context): List<DApp> =
        runCatching<List<DApp>> {
            val json = Utils.loadJSONFromAsset(context, DAPPS_LIST_FILENAME)
            if (json.isNullOrEmpty()) {
                emptyList()
            } else {
                gson.fromJson<List<DApp>>(json, object : TypeToken<List<DApp>>() {}.type) ?: emptyList()
            }
        }.getOrElse {
            Timber.e(it, "Failed to load default dapps list")
            emptyList()
        }

    /**
     * 判断指定链接是否在默认 DApp 列表中。
     */
    @JvmStatic
    fun isInDappsList(context: Context, candidateURL: String?): Boolean {
        if (candidateURL.isNullOrEmpty()) return false
        val knownDapps = getDappsList(context)
        val candidateDomain = Utils.getDomainName(candidateURL)
        return knownDapps.any { candidateDomain == Utils.getDomainName(it.url) }
    }

    /**
     * 根据网络选择默认首页。
     */
    @JvmStatic
    fun defaultDapp(chainId: Long): String =
        if (chainId == POLYGON_ID || chainId == POLYGON_TEST_ID || chainId == POLYGON_AMOY_ID) {
            POLYGON_HOMEPAGE
        } else {
            DEFAULT_HOMEPAGE
        }

    /**
     * 当前链接是否位于默认首页。
     */
    @JvmStatic
    fun isWithinHomePage(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val homePageRoot = DEFAULT_HOMEPAGE.dropLast(1)
        return url.startsWith(homePageRoot)
    }

    /**
     * 判断是否为默认首页地址。
     */
    @JvmStatic
    fun isDefaultDapp(url: String?): Boolean =
        url == DEFAULT_HOMEPAGE || url == POLYGON_HOMEPAGE

    // ---- 私有工具方法 ----

    private fun getPrimarySites(context: Context): MutableList<DApp> =
        mutableListOf() // 保留兼容逻辑，原实现返回空列表

    private fun parseDappHistory(history: List<DApp>): MutableList<DApp> {
        var requireRefresh = false
        val cleaned = mutableListOf<DApp>()
        for (dapp in history) {
            val url = dapp.url
            if (url.isNullOrEmpty()) {
                requireRefresh = true
                continue
            }
            if (Utils.isValidUrl(url)) {
                cleaned.add(dapp)
            } else {
                requireRefresh = true
            }
        }
        return if (requireRefresh) cleaned else history.toMutableList()
    }

    private fun saveHistory(context: Context?, history: List<DApp>) {
        if (context == null) return
        val json = gson.toJson(history)
        storeJsonData(DAPPS_HISTORY_FILE, json, context)
    }

    private fun loadJsonData(fName: String, context: Context): String {
        val file = File(context.filesDir, fName)
        if (!file.exists()) return ""
        return runCatching {
            FileInputStream(file).use { fis ->
                BufferedReader(InputStreamReader(fis)).use { reader ->
                    buildString {
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            if (line.isNullOrBlank()) continue
                            append(line)
                            append("\n")
                        }
                    }
                }
            }
        }.getOrElse {
            Timber.w(it, "Failed to load json data from $fName")
            ""
        }
    }

    private fun storeJsonData(fName: String, json: String, context: Context) {
        val file = File(context.filesDir, fName)
        runCatching {
            FileOutputStream(file).use { fos ->
                BufferedWriter(OutputStreamWriter(fos)).use { writer ->
                    writer.write(json)
                    writer.flush()
                }
            }
        }.onFailure {
            Timber.w(it, "Failed to store json data in $fName")
        }
    }

    /**
     * 读取旧版 SharedPreferences 数据，并迁移至文件存储。
     */
    private fun loadFromPrefsLegacy(context: Context, key: String, fileName: String): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val data = prefs.getString(key, "")
        return if (!data.isNullOrEmpty()) {
            blankPrefEntry(context, key, prefs)
            storeJsonData(fileName, data, context)
            data
        } else {
            null
        }
    }

    private fun blankPrefEntry(context: Context, key: String, prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)) {
        prefs.edit().putString(key, "").apply()
    }
}
