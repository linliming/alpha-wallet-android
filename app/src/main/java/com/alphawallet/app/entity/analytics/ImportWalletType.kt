package com.alphawallet.app.entity.analytics

enum class ImportWalletType
    (val value: String) {
    SEED_PHRASE("Seed Phrase"),
    KEYSTORE("Keystore"),
    PRIVATE_KEY("Private Key"),
    WATCH("Watch")
}
