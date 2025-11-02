package com.alphawallet.app.repository

interface KeyProvider {
    fun getBSCExplorerKey(): String

    fun getAnalyticsKey(): String

    fun getEtherscanKey(): String

    fun getPolygonScanKey(): String

    fun getAuroraScanKey(): String

    fun getCovalentKey(): String

    fun getKlaytnKey(): String

    fun getInfuraKey(): String

    fun getSecondaryInfuraKey(): String

    fun getTertiaryInfuraKey(): String

    fun getRampKey(): String

    fun getOpenSeaKey(): String

    fun getMailchimpKey(): String

    fun getCoinbasePayAppId(): String

    fun getWalletConnectProjectId(): String

    fun getInfuraSecret(): String

    fun getTSInfuraKey(): String

    fun getUnstoppableDomainsKey(): String

    fun getOkLinkKey(): String

    fun getOkLBKey(): String

    fun getBlockPiBaobabKey(): String

    fun getBlockPiCypressKey(): String

    fun getBlockNativeKey(): String

    fun getSmartPassKey(): String

    fun getSmartPassDevKey(): String

    fun getCoinGeckoKey(): String

    fun getBackupKey(): String
}
