package com.alphawallet.app.repository

class KeyProviderJNIImpl : KeyProvider {
    init {
        System.loadLibrary("keys")
    }

    external override fun getInfuraKey(): String

    external override fun getTSInfuraKey(): String

    external override fun getSecondaryInfuraKey(): String

    external override fun getTertiaryInfuraKey(): String

    external override fun getBSCExplorerKey(): String

    external override fun getAnalyticsKey(): String

    external override fun getEtherscanKey(): String

    external override fun getPolygonScanKey(): String

    external override fun getAuroraScanKey(): String

    external override fun getCovalentKey(): String

    external override fun getKlaytnKey(): String

    external override fun getRampKey(): String

    external override fun getOpenSeaKey(): String

    external override fun getMailchimpKey(): String

    external override fun getCoinbasePayAppId(): String

    external override fun getWalletConnectProjectId(): String

    external override fun getInfuraSecret(): String

    external override fun getUnstoppableDomainsKey(): String

    external override fun getOkLinkKey(): String

    external override fun getOkLBKey(): String

    external override fun getBlockPiBaobabKey(): String

    external override fun getBlockPiCypressKey(): String

    external override fun getBlockNativeKey(): String

    external override fun getSmartPassKey(): String

    external override fun getSmartPassDevKey(): String

    external override fun getCoinGeckoKey(): String

    external override fun getBackupKey(): String
}
