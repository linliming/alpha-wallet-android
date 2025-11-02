package com.alphawallet.app.entity

import android.util.Pair

/**
 * Created by JB on 2/12/2021.
 */
interface SyncCallback {
    fun syncUpdate(wallet: String?, value: Pair<Double?, Double?>?)
    fun syncCompleted(wallet: String?, value: Pair<Double?, Double?>?)
    fun syncStarted(wallet: String?, value: Pair<Double?, Double?>?)
}
