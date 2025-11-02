package com.alphawallet.app.walletconnect.util

object WalletConnectHelper {
    @JvmStatic
    fun isWalletConnectV1(text: String): Boolean {
        return text.contains("@1")
    }

    @JvmStatic
    fun getChainId(chainId: String): Long {
        return chainId.split(":".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()[1].toLong()
    }
}
