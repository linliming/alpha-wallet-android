package com.alphawallet.app.util

import com.alphawallet.app.entity.Wallet

object Wallets {
    fun filter(wallets: Array<Wallet>): Array<Wallet> {
        val list = ArrayList<Wallet>()
        for (w in wallets) {
            if (!w.watchOnly()) list.add(w)
        }

        return list.toTypedArray<Wallet>()
    }
}
