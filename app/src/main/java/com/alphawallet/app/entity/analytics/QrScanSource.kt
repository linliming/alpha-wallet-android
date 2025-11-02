package com.alphawallet.app.entity.analytics

enum class QrScanSource
    (val value: String) {
    ADDRESS_TEXT_FIELD("addressTextField"),
    BROWSER_SCREEN("browserScreen"),
    IMPORT_WALLET_SCREEN("importWalletScreen"),
    ADD_CUSTOM_TOKEN_SCREEN("addCustomTokenScreen"),
    WALLET_SCREEN("walletScreen"),
    SEND_FUNGIBLE_SCREEN("sendFungibleScreen"),
    QUICK_ACTION("quickAction");

    companion object {
        const val KEY: String = "source"
    }
}
