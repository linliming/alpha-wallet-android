package com.alphawallet.app.repository

import com.alphawallet.app.entity.CurrencyItem

interface PreferenceRepositoryType {

    var currentWalletAddress: String?


    var activeBrowserNetwork: Long


    var networkFilterList: String?


    var customRPCNetworks: String?


    val defaultLocale: String?

    var isFindWalletAddressDialogShown: Boolean


    val defaultCurrency: String?

    fun setDefaultCurrency(currency: CurrencyItem?)

    val defaultCurrencySymbol: String?


    var userPreferenceLocale: String?


    var fullScreenState: Boolean


    var use1559Transactions: Boolean


    var developerOverride: Boolean


    var isTestnetEnabled: Boolean


    var priceAlerts: String?

    fun setHasSetNetworkFilters()

    fun hasSetNetworkFilters(): Boolean

    fun blankHasSetNetworkFilters()

    fun commit()

    fun incrementLaunchCount()


    val launchCount: Int

    fun resetLaunchCount()

    fun setRateAppShown()


    val rateAppShown: Boolean

    fun setShowZeroBalanceTokens(shouldShow: Boolean)

    fun shouldShowZeroBalanceTokens(): Boolean

    var updateWarningCount: Int


    var installTime: Long

    var uniqueId: String?


    val isMarshMallowWarningShown: Boolean

    fun setMarshMallowWarning(shown: Boolean)

    fun storeLastFragmentPage(ordinal: Int)

    val lastFragmentPage: Int

    fun getLastVersionCode(currentCode: Int): Int

    fun setLastVersionCode(code: Int)


    var theme: Int

    fun isNewWallet(address: String?): Boolean

    fun setNewWallet(address: String?, isNewWallet: Boolean)


    var isWatchOnly: Boolean


    var selectedSwapProviders: Set<String?>?


    var isAnalyticsEnabled: Boolean


    var isCrashReportingEnabled: Boolean

    fun getLoginTime(address: String?): Long

    fun logIn(address: String?)


    var firebaseMessagingToken: String?

    fun isTransactionNotificationsEnabled(address: String?): Boolean

    fun setTransactionNotificationEnabled(address: String?, isEnabled: Boolean)

    fun isPostNotificationsPermissionRequested(address: String?): Boolean

    fun setPostNotificationsPermissionRequested(address: String?, hasRequested: Boolean)


    var useTSViewer: Boolean
}
