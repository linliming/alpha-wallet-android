package com.alphawallet.app.entity.analytics

enum class FirstWalletAction
    (val value: String) {
    CREATE_WALLET("create"),
    IMPORT_WALLET("import"),
    WATCH_WALLET("watch");

    companion object {
        const val KEY: String = "type"
    }
}
