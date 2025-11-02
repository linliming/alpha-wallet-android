package com.alphawallet.app.entity.analytics

enum class QrScanResultType
    (val value: String) {
    ADDRESS_OR_EIP_681("addressOrEip681"),
    WALLET_CONNECT("walletConnect"),
    STRING("string"),
    URL("url"),
    PRIVATE_KEY("privateKey"),
    SEED_PHRASE("seedPhrase"),
    JSON("json"),
    ADDRESS("address");

    companion object {
        const val KEY: String = "resultType"
    }
}
