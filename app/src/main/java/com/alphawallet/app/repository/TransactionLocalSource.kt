package com.alphawallet.app.repository

import com.alphawallet.app.entity.ActivityMeta
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.repository.entity.RealmAuxData
import io.realm.Realm

/**
 * TransactionLocalSource - 交易本地数据源接口
 *
 * 职责：
 * 1. 从本地数据库（Realm）读取/写入交易与事件元数据
 * 2. 提供交易查询、标记、清理等能力
 * 3. 为上层仓库与服务提供统一的本地访问接口
 *
 * 说明：
 * - 新接口以Kotlin定义；
 * - 大多数查询为同步返回以匹配现有调用；
 * - 擦除数据相关方法保留 RxJava Single 以兼容仍为 Java 的调用方。
 */
interface TransactionLocalSource {
    fun fetchTransaction(wallet: Wallet, hash: String): Transaction?
    fun putTransaction(wallet: Wallet, tx: Transaction): Transaction
    fun getRealmInstance(wallet: Wallet): Realm

    fun fetchActivityMetas(wallet: Wallet, networkFilters: List<Long>, fetchTime: Long, fetchLimit: Int): Array<ActivityMeta>
    fun fetchActivityMetas(wallet: Wallet, chainId: Long, tokenAddress: String, historyCount: Int): Array<ActivityMeta>
    fun fetchEventMetas(wallet: Wallet, networkFilters: List<Long>): Array<ActivityMeta>

    fun markTransactionBlock(walletAddress: String, hash: String, blockValue: Long)
    fun fetchPendingTransactions(currentAddress: String): Array<Transaction>

    fun fetchEvent(walletAddress: String, eventKey: String): RealmAuxData?
    fun fetchTxCompletionTime(wallet: Wallet, hash: String): Long

    suspend fun deleteAllForWallet(currentAddress: String): Boolean
    suspend fun deleteAllTickers(): Boolean
}


