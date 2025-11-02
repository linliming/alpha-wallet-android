package com.alphawallet.app.entity.analytics

enum class TokenSwapEvent
    (@JvmField val value: String) {
    NATIVE_SWAP("Native Swap"),
    QUICKSWAP("Quick Swap"),
    ONEINCH("Oneinch"),
    HONEYSWAP("Honeyswap"),
    UNISWAP("Uniswap");

    companion object {
        const val KEY: String = "name"
    }
}
