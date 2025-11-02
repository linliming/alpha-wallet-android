package com.alphawallet.app.repository

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.alphawallet.app.C
import com.alphawallet.app.entity.CurrencyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 共享偏好设置仓库类
 *
 * 负责管理应用程序的所有持久化偏好设置，包括：
 * - 钱包地址和网络配置
 * - 用户界面设置（主题、语言、全屏状态等）
 * - 应用行为配置（通知、分析、开发者选项等）
 * - 用户数据（登录时间、启动次数、版本信息等）
 *
 * 技术特点：
 * - 使用 Kotlin 协程支持异步操作
 * - 实现 PreferenceRepositoryType 接口
 * - 使用 Hilt 进行依赖注入
 * - 线程安全的偏好设置操作
 * - 支持类型安全的数据存储和检索
 *
 * @param context Android应用上下文
 * @author AlphaWallet Team
 * @since 2024
 */
@Singleton
class SharedPreferenceRepository @Inject constructor(
    context: Context
) : PreferenceRepositoryType {

    // ==================== 常量定义 ====================
    
    companion object {
        // 钱包和网络相关常量
        private const val CURRENT_ACCOUNT_ADDRESS_KEY = "current_account_address"
        private const val DEFAULT_NETWORK_NAME_KEY = "default_network_name"
        private const val NETWORK_FILTER_KEY = "network_filters"
        private const val CUSTOM_NETWORKS_KEY = "custom_networks"
        
        // 通知相关常量
        private const val TRANSACTION_NOTIFICATIONS_ENABLED = "transaction_notifications_enabled_"
        private const val POST_NOTIFICATIONS_PERMISSION_REQUESTED = "post_notifications_permission_requested_"
        
        // 界面设置常量
        private const val THEME_KEY = "theme"
        private const val LOCALE_KEY = "locale"
        private const val FULL_SCREEN_STATE = "full_screen"
        
        // 功能开关常量
        private const val DEFAULT_SET_KEY = "default_net_set"
        private const val EXPERIMENTAL_1559_TX = "ex_1559_tx"
        private const val USE_TOKENSCRIPT_VIEWER = "use_ts_viewer"
        private const val DEVELOPER_OVERRIDE = "developer_override"
        private const val TESTNET_ENABLED = "testnet_enabled"
        
        // 用户体验相关常量
        private const val BACKUP_WALLET_SHOWN = "backup_wallet_shown"
        private const val FIND_WALLET_ADDRESS_SHOWN = "find_wallet_address_shown"
        private const val RATE_APP_SHOWN = "rate_us_shown"
        private const val LAUNCH_COUNT = "launch_count"
        private const val MARSHMALLOW_SUPPORT_WARNING = "marshmallow_version_support_warning_shown"
        
        // 货币和本地化常量
        const val CURRENCY_CODE_KEY = "currency_locale"
        const val CURRENCY_SYMBOL_KEY = "currency_symbol"
        const val USER_LOCALE_PREF = "user_locale_pref"
        const val DEVICE_LOCALE = "device_locale"
        const val DEVICE_COUNTRY = "device_country"
        
        // 代币和交易相关常量
        const val HIDE_ZERO_BALANCE_TOKENS = "hide_zero_balance_tokens"
        const val PRICE_ALERTS = "price_alerts"
        
        // 系统和版本相关常量
        private const val SET_NETWORK_FILTERS = "set_filters"
        private const val SHOULD_SHOW_ROOT_WARNING = "should_show_root_warning"
        private const val UPDATE_WARNINGS = "update_warns"
        private const val INSTALL_TIME = "install_time"
        private const val LAST_FRAGMENT_ID = "lastfrag_id"
        private const val LAST_VERSION_CODE = "last_version_code"
        
        // 交换和分析相关常量
        private const val SELECTED_SWAP_PROVIDERS_KEY = "selected_exchanges"
        private const val ANALYTICS_KEY = "analytics_key"
        private const val CRASH_REPORTING_KEY = "crash_reporting_key"
        
        // 钱包状态相关常量
        private const val WALLET_LOGIN_TIME = "wallet_login_time_"
        private const val NEW_WALLET = "new_wallet_"
        private const val WATCH_ONLY = "watch_only"
        
        // Firebase相关常量
        private const val FIREBASE_MESSAGING_TOKEN = "firebase_messaging_token"
    }
    
    // ==================== 成员变量 ====================
    
    /**
     * SharedPreferences实例
     * 使用默认的共享偏好设置存储
     */
    private val pref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    
    // ==================== 钱包和网络配置 ====================
    
    /**
     * 当前钱包地址
     * 获取和设置当前活跃的钱包地址
     */
    override var currentWalletAddress: String?
        get() = pref.getString(CURRENT_ACCOUNT_ADDRESS_KEY, null)
        @SuppressLint("ApplySharedPref")
        set(value) {
            // 使用commit()确保值立即可用
            pref.edit().putString(CURRENT_ACCOUNT_ADDRESS_KEY, value).commit()
        }
    
    /**
     * 活跃浏览器网络ID
     * 处理历史版本兼容性问题（Integer/String -> Long）
     */
    override var activeBrowserNetwork: Long
        @SuppressLint("ApplySharedPref")
        get() {
            return try {
                pref.getLong(DEFAULT_NETWORK_NAME_KEY, 0)
            } catch (e: ClassCastException) {
                // 处理历史版本兼容性：之前使用Integer或String
                val selectedNetwork = try {
                    pref.getInt(DEFAULT_NETWORK_NAME_KEY, 0).toLong()
                } catch (stringException: ClassCastException) {
                    // 处理历史版本兼容性：之前使用String存储网络名称
                    // 如果无法转换，使用默认主网ID
                    com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
                }
                // 立即更新为Long类型
                pref.edit().putLong(DEFAULT_NETWORK_NAME_KEY, selectedNetwork).commit()
                selectedNetwork
            }
        }
        set(value) {
            pref.edit().putLong(DEFAULT_NETWORK_NAME_KEY, value).apply()
        }
    
    /**
     * 网络过滤器列表
     * 存储用户选择的网络过滤配置
     */
    override var networkFilterList: String?
        get() = pref.getString(NETWORK_FILTER_KEY, "")
        set(value) {
            pref.edit().putString(NETWORK_FILTER_KEY, value).apply()
        }
    
    /**
     * 自定义RPC网络配置
     * 存储用户添加的自定义网络信息
     */
    override var customRPCNetworks: String?
        get() = pref.getString(CUSTOM_NETWORKS_KEY, "")
        set(value) {
            pref.edit().putString(CUSTOM_NETWORKS_KEY, value).apply()
        }
    
    // ==================== 本地化和语言设置 ====================
    
    /**
     * 默认语言环境
     * 获取系统默认语言或用户设置的语言
     */
    override val defaultLocale: String
        get() = pref.getString(LOCALE_KEY, Locale.getDefault().language) ?: Locale.getDefault().language
    
    /**
     * 用户偏好语言环境
     * 用户手动设置的语言偏好
     */
    override var userPreferenceLocale: String?
        get() = pref.getString(USER_LOCALE_PREF, "")
        set(value) {
            pref.edit().putString(USER_LOCALE_PREF, value).apply()
        }
    
    // ==================== 界面显示设置 ====================
    
    /**
     * 查找钱包地址对话框显示状态
     * 记录是否已经显示过查找钱包地址的提示对话框
     */
    override var isFindWalletAddressDialogShown: Boolean
        get() = pref.getBoolean(FIND_WALLET_ADDRESS_SHOWN, false)
        set(value) {
            pref.edit().putBoolean(FIND_WALLET_ADDRESS_SHOWN, value).apply()
        }
    
    /**
     * 全屏状态
     * 应用是否处于全屏模式
     */
    override var fullScreenState: Boolean
        get() = pref.getBoolean(FULL_SCREEN_STATE, false)
        set(value) {
            pref.edit().putBoolean(FULL_SCREEN_STATE, value).apply()
        }
    
    /**
     * 应用主题设置
     * 存储用户选择的主题（自动/浅色/深色）
     */
    override var theme: Int
        get() = pref.getInt(THEME_KEY, C.THEME_AUTO)
        set(value) {
            pref.edit().putInt(THEME_KEY, value).apply()
        }
    
    // ==================== 货币和价格设置 ====================
    
    /**
     * 默认货币代码
     * 用户选择的默认货币代码（如USD、EUR等）
     */
    override val defaultCurrency: String
        get() = pref.getString(CURRENCY_CODE_KEY, C.DEFAULT_CURRENCY_CODE) ?: C.DEFAULT_CURRENCY_CODE
    
    /**
     * 默认货币符号
     * 对应货币代码的符号（如$、€等）
     */
    override val defaultCurrencySymbol: String
        get() = pref.getString(CURRENCY_SYMBOL_KEY, C.DEFAULT_CURRENCY_CODE) ?: C.DEFAULT_CURRENCY_CODE
    
    /**
     * 设置默认货币
     * 同时设置货币代码和符号
     * @param currency 货币项目，包含代码和符号
     */
    override fun setDefaultCurrency(currency: CurrencyItem?) {
        currency?.let {
            pref.edit()
                .putString(CURRENCY_CODE_KEY, it.code)
                .putString(CURRENCY_SYMBOL_KEY, it.symbol)
                .apply()
        }
    }
    
    /**
     * 价格提醒配置
     * 存储价格提醒的JSON配置数据
     */
    override var priceAlerts: String?
        get() = pref.getString(PRICE_ALERTS, "")
        set(value) {
            pref.edit().putString(PRICE_ALERTS, value).apply()
        }
    
    // ==================== 交易和网络功能设置 ====================
    
    /**
     * EIP-1559交易类型使用状态
     * 是否启用EIP-1559类型的交易（实验性功能）
     */
    override var use1559Transactions: Boolean
        get() = pref.getBoolean(EXPERIMENTAL_1559_TX, true)
        set(value) {
            pref.edit().putBoolean(EXPERIMENTAL_1559_TX, value).apply()
        }
    
    /**
     * 开发者覆盖模式
     * 是否启用开发者模式的特殊功能
     */
    override var developerOverride: Boolean
        get() = pref.getBoolean(DEVELOPER_OVERRIDE, false)
        set(value) {
            pref.edit().putBoolean(DEVELOPER_OVERRIDE, value).apply()
        }
    
    /**
     * 测试网络启用状态
     * 是否显示和使用测试网络
     */
    override var isTestnetEnabled: Boolean
        get() = pref.getBoolean(TESTNET_ENABLED, false)
        set(value) {
            pref.edit().putBoolean(TESTNET_ENABLED, value).apply()
        }
    
    /**
     * TokenScript查看器使用状态
     * 是否使用内置的TokenScript查看器
     */
    override var useTSViewer: Boolean
        get() = pref.getBoolean(USE_TOKENSCRIPT_VIEWER, true)
        set(value) {
            pref.edit().putBoolean(USE_TOKENSCRIPT_VIEWER, value).apply()
        }
    
    // ==================== 网络过滤器管理 ====================
    
    /**
     * 设置网络过滤器已配置标记
     * 标记用户已经设置过网络过滤器
     */
    override fun setHasSetNetworkFilters() {
        pref.edit().putBoolean(SET_NETWORK_FILTERS, true).apply()
    }
    
    /**
     * 检查是否已设置网络过滤器
     * @return 是否已经设置过网络过滤器
     */
    override fun hasSetNetworkFilters(): Boolean {
        return pref.getBoolean(SET_NETWORK_FILTERS, false)
    }
    
    /**
     * 清空网络过滤器设置标记
     * 重置网络过滤器配置状态
     */
    override fun blankHasSetNetworkFilters() {
        pref.edit().putBoolean(SET_NETWORK_FILTERS, false).apply()
    }
    
    // ==================== 应用生命周期和统计 ====================
    
    /**
     * 应用启动次数
     * 记录应用被启动的总次数
     */
    override val launchCount: Int
        get() = pref.getInt(LAUNCH_COUNT, 0)
    
    /**
     * 增加启动次数
     * 每次应用启动时调用
     */
    override fun incrementLaunchCount() {
        val prevLaunchCount = launchCount
        pref.edit().putInt(LAUNCH_COUNT, prevLaunchCount + 1).apply()
    }
    
    /**
     * 重置启动次数
     * 将启动次数重置为0
     */
    override fun resetLaunchCount() {
        pref.edit().putInt(LAUNCH_COUNT, 0).apply()
    }
    
    /**
     * 应用评分对话框显示状态
     * 是否已经显示过应用评分对话框
     */
    override val rateAppShown: Boolean
        get() = pref.getBoolean(RATE_APP_SHOWN, false)
    
    /**
     * 设置应用评分对话框已显示
     * 标记评分对话框已经显示过
     */
    override fun setRateAppShown() {
        pref.edit().putBoolean(RATE_APP_SHOWN, true).apply()
    }
    
    // ==================== 代币显示设置 ====================
    
    /**
     * 是否显示零余额代币
     * 控制是否在代币列表中显示余额为零的代币
     */
    override fun shouldShowZeroBalanceTokens(): Boolean {
        return pref.getBoolean(HIDE_ZERO_BALANCE_TOKENS, false)
    }
    
    /**
     * 设置零余额代币显示状态
     * @param shouldShow 是否显示零余额代币
     */
    override fun setShowZeroBalanceTokens(shouldShow: Boolean) {
        pref.edit().putBoolean(HIDE_ZERO_BALANCE_TOKENS, shouldShow).apply()
    }
    
    // ==================== 版本和更新管理 ====================
    
    /**
     * 更新警告计数
     * 记录显示更新警告的次数
     */
    override var updateWarningCount: Int
        get() = pref.getInt(UPDATE_WARNINGS, 0)
        set(value) {
            pref.edit().putInt(UPDATE_WARNINGS, value).apply()
        }
    
    /**
     * 应用安装时间
     * 记录应用首次安装的时间戳
     */
    override var installTime: Long
        get() = pref.getLong(INSTALL_TIME, 0)
        set(value) {
            pref.edit().putLong(INSTALL_TIME, value).apply()
        }
    
    /**
     * 设备唯一标识符
     * 用于分析和统计的设备唯一ID
     */
    override var uniqueId: String?
        get() = pref.getString(C.PREF_UNIQUE_ID, "")
        set(value) {
            pref.edit().putString(C.PREF_UNIQUE_ID, value).apply()
        }
    
    /**
     * Android 6.0支持警告显示状态
     * 是否已经显示过Android 6.0支持警告
     */
    override val isMarshMallowWarningShown: Boolean
        get() = pref.getBoolean(MARSHMALLOW_SUPPORT_WARNING, false)
    
    /**
     * 设置Android 6.0警告已显示
     * @param shown 是否已显示警告
     */
    override fun setMarshMallowWarning(shown: Boolean) {
        pref.edit().putBoolean(MARSHMALLOW_SUPPORT_WARNING, true).apply()
    }
    
    /**
     * 存储最后访问的页面ID
     * 记录用户最后访问的Fragment页面
     * @param ordinal 页面序号
     */
    override fun storeLastFragmentPage(ordinal: Int) {
        pref.edit().putInt(LAST_FRAGMENT_ID, ordinal).apply()
    }
    
    /**
     * 获取最后访问的页面ID
     * @return 最后访问的页面序号，-1表示未设置
     */
    override val lastFragmentPage: Int
        get() = pref.getInt(LAST_FRAGMENT_ID, -1)
    
    /**
     * 获取上次版本代码
     * 用于检测应用更新和显示新功能介绍
     * @param currentCode 当前版本代码
     * @return 上次记录的版本代码
     */
    override fun getLastVersionCode(currentCode: Int): Int {
        var versionCode = pref.getInt(LAST_VERSION_CODE, 0)
        if (versionCode == 0) {
            setLastVersionCode(currentCode)
            versionCode = Int.MAX_VALUE // 首次用户不会看到"新功能"，只有在首次更新时才开始显示
        }
        return versionCode
    }
    
    /**
     * 设置版本代码
     * @param code 版本代码
     */
    override fun setLastVersionCode(code: Int) {
        pref.edit().putInt(LAST_VERSION_CODE, code).apply()
    }
    
    // ==================== 钱包状态管理 ====================
    
    /**
     * 检查是否为新钱包
     * @param address 钱包地址
     * @return 是否为新创建的钱包
     */
    override fun isNewWallet(address: String?): Boolean {
        return address?.let {
            pref.getBoolean(getAddressKey(NEW_WALLET, it), false)
        } ?: false
    }
    
    /**
     * 设置新钱包状态
     * @param address 钱包地址
     * @param isNewWallet 是否为新钱包
     */
    override fun setNewWallet(address: String?, isNewWallet: Boolean) {
        address?.let {
            pref.edit().putBoolean(getAddressKey(NEW_WALLET, it), isNewWallet).apply()
        }
    }
    
    /**
     * 只读钱包状态
     * 当前钱包是否为只读模式（观察钱包）
     */
    override var isWatchOnly: Boolean
        get() = pref.getBoolean(WATCH_ONLY, false)
        set(value) {
            pref.edit().putBoolean(WATCH_ONLY, value).apply()
        }
    
    /**
     * 钱包登录时间
     * 获取指定钱包的最后登录时间
     * @param address 钱包地址
     * @return 登录时间戳，-1表示从未登录
     */
    override fun getLoginTime(address: String?): Long {
        return address?.let {
            pref.getLong(getAddressKey(WALLET_LOGIN_TIME, it), -1)
        } ?: -1
    }
    
    /**
     * 记录钱包登录
     * 更新指定钱包的登录时间为当前时间
     * @param address 钱包地址
     */
    override fun logIn(address: String?) {
        address?.let {
            pref.edit().putLong(
                getAddressKey(WALLET_LOGIN_TIME, it),
                System.currentTimeMillis() / 1000
            ).apply()
        }
    }
    
    // ==================== 交换服务提供商设置 ====================
    
    /**
     * 选中的交换服务提供商
     * 用户选择的去中心化交换服务提供商列表
     */
    override var selectedSwapProviders: Set<String?>?
        get() = pref.getStringSet(SELECTED_SWAP_PROVIDERS_KEY, HashSet())
        set(value) {
            pref.edit().putStringSet(SELECTED_SWAP_PROVIDERS_KEY, value).apply()
        }
    
    // ==================== 分析和崩溃报告设置 ====================
    
    /**
     * 分析功能启用状态
     * 是否允许收集使用分析数据
     */
    override var isAnalyticsEnabled: Boolean
        get() = pref.getBoolean(ANALYTICS_KEY, true)
        set(value) {
            pref.edit().putBoolean(ANALYTICS_KEY, value).apply()
        }
    
    /**
     * 崩溃报告启用状态
     * 是否允许发送崩溃报告
     */
    override var isCrashReportingEnabled: Boolean
        get() = pref.getBoolean(CRASH_REPORTING_KEY, true)
        set(value) {
            pref.edit().putBoolean(CRASH_REPORTING_KEY, value).apply()
        }
    
    // ==================== Firebase消息推送 ====================
    
    /**
     * Firebase消息推送令牌
     * 用于推送通知的Firebase令牌
     */
    override var firebaseMessagingToken: String?
        get() = pref.getString(FIREBASE_MESSAGING_TOKEN, "")
        set(value) {
            pref.edit().putString(FIREBASE_MESSAGING_TOKEN, value).apply()
        }
    
    /**
     * 检查交易通知是否启用
     * @param address 钱包地址
     * @return 是否启用交易通知
     */
    override fun isTransactionNotificationsEnabled(address: String?): Boolean {
        return address?.let {
            pref.getBoolean(getAddressKey(TRANSACTION_NOTIFICATIONS_ENABLED, it), true)
        } ?: true
    }
    
    /**
     * 设置交易通知启用状态
     * @param address 钱包地址
     * @param isEnabled 是否启用通知
     */
    override fun setTransactionNotificationEnabled(address: String?, isEnabled: Boolean) {
        address?.let {
            pref.edit().putBoolean(
                getAddressKey(TRANSACTION_NOTIFICATIONS_ENABLED, it),
                isEnabled
            ).apply()
        }
    }
    
    /**
     * 检查是否已请求推送通知权限
     * @param address 钱包地址
     * @return 是否已请求权限
     */
    override fun isPostNotificationsPermissionRequested(address: String?): Boolean {
        return address?.let {
            pref.getBoolean(getAddressKey(POST_NOTIFICATIONS_PERMISSION_REQUESTED, it), false)
        } ?: false
    }
    
    /**
     * 设置推送通知权限请求状态
     * @param address 钱包地址
     * @param hasRequested 是否已请求权限
     */
    override fun setPostNotificationsPermissionRequested(address: String?, hasRequested: Boolean) {
        address?.let {
            pref.edit().putBoolean(
                getAddressKey(POST_NOTIFICATIONS_PERMISSION_REQUESTED, it),
                hasRequested
            ).apply()
        }
    }
    
    // ==================== 系统操作方法 ====================
    
    /**
     * 强制提交所有待处理的更改
     * 确保设置被立即写入存储
     * 注意：这是一个同步操作，可能会阻塞UI线程
     */
    @SuppressLint("ApplySharedPref")
    override fun commit() {
        pref.edit().commit()
    }
    
    // ==================== 协程支持的异步操作 ====================
    
    /**
     * 异步获取当前钱包地址
     * 使用协程在IO线程中执行读取操作
     * @return 当前钱包地址
     */
    suspend fun getCurrentWalletAddressAsync(): String? = withContext(Dispatchers.IO) {
        currentWalletAddress
    }
    
    /**
     * 异步设置当前钱包地址
     * 使用协程在IO线程中执行写入操作
     * @param address 钱包地址
     */
    suspend fun setCurrentWalletAddressAsync(address: String?) = withContext(Dispatchers.IO) {
        currentWalletAddress = address
    }
    
    /**
     * 异步获取活跃浏览器网络
     * @return 网络ID
     */
    suspend fun getActiveBrowserNetworkAsync(): Long = withContext(Dispatchers.IO) {
        activeBrowserNetwork
    }
    
    /**
     * 异步设置活跃浏览器网络
     * @param networkId 网络ID
     */
    suspend fun setActiveBrowserNetworkAsync(networkId: Long) = withContext(Dispatchers.IO) {
        activeBrowserNetwork = networkId
    }
    
    /**
     * 异步获取自定义RPC网络配置
     * @return 网络配置JSON字符串
     */
    suspend fun getCustomRPCNetworksAsync(): String? = withContext(Dispatchers.IO) {
        customRPCNetworks
    }
    
    /**
     * 异步设置自定义RPC网络配置
     * @param networks 网络配置JSON字符串
     */
    suspend fun setCustomRPCNetworks(networks: String?) = withContext(Dispatchers.IO) {
        customRPCNetworks = networks
    }
    
    /**
     * 异步批量更新偏好设置
     * 在单个事务中更新多个设置项，提高性能
     * @param updates 更新操作的lambda函数
     */
    suspend fun batchUpdate(updates: SharedPreferences.Editor.() -> Unit) = withContext(Dispatchers.IO) {
        pref.edit().apply(updates).apply()
    }
    
    /**
     * 异步清除所有偏好设置
     * 警告：这将删除所有存储的用户偏好设置
     */
    suspend fun clearAllAsync() = withContext(Dispatchers.IO) {
        pref.edit().clear().apply()
    }
    
    /**
     * 异步检查某个键是否存在
     * @param key 偏好设置键
     * @return 是否存在该键
     */
    suspend fun containsKeyAsync(key: String): Boolean = withContext(Dispatchers.IO) {
        pref.contains(key)
    }
    
    // ==================== 私有辅助方法 ====================
    
    /**
     * 生成地址相关的键名
     * 将地址转换为小写并与基础键组合
     * @param key 基础键名
     * @param address 钱包地址
     * @return 完整的键名
     */
    private fun getAddressKey(key: String, address: String): String {
        return key + address.lowercase(Locale.ENGLISH)
    }
    
    /**
     * 安全的字符串获取方法
     * 处理可能的空值情况
     * @param key 键名
     * @param defaultValue 默认值
     * @return 字符串值或默认值
     */
    private fun getStringSafely(key: String, defaultValue: String): String {
        return try {
            pref.getString(key, defaultValue) ?: defaultValue
        } catch (e: ClassCastException) {
            // 处理类型转换异常，返回默认值
            defaultValue
        }
    }
    
    /**
     * 安全的布尔值获取方法
     * 处理可能的类型转换异常
     * @param key 键名
     * @param defaultValue 默认值
     * @return 布尔值或默认值
     */
    private fun getBooleanSafely(key: String, defaultValue: Boolean): Boolean {
        return try {
            pref.getBoolean(key, defaultValue)
        } catch (e: ClassCastException) {
            // 处理类型转换异常，返回默认值
            defaultValue
        }
    }
    
    /**
     * 安全的整数获取方法
     * 处理可能的类型转换异常
     * @param key 键名
     * @param defaultValue 默认值
     * @return 整数值或默认值
     */
    private fun getIntSafely(key: String, defaultValue: Int): Int {
        return try {
            pref.getInt(key, defaultValue)
        } catch (e: ClassCastException) {
            // 处理类型转换异常，返回默认值
            defaultValue
        }
    }
    
    /**
     * 安全的长整数获取方法
     * 处理可能的类型转换异常
     * @param key 键名
     * @param defaultValue 默认值
     * @return 长整数值或默认值
     */
    private fun getLongSafely(key: String, defaultValue: Long): Long {
        return try {
            pref.getLong(key, defaultValue)
        } catch (e: ClassCastException) {
            // 处理类型转换异常，返回默认值
            defaultValue
        }
    }
}
