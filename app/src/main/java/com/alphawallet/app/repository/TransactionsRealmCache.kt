package com.alphawallet.app.repository

import android.util.LongSparseArray
import com.alphawallet.app.entity.ActivityMeta
import com.alphawallet.app.entity.EventMeta
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.TransactionMeta
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.repository.entity.Realm1559Gas
import com.alphawallet.app.repository.entity.RealmAuxData
import com.alphawallet.app.repository.entity.RealmNFTAsset
import com.alphawallet.app.repository.entity.RealmToken
import com.alphawallet.app.repository.entity.RealmTransaction
import com.alphawallet.app.repository.entity.RealmTransfer
import com.alphawallet.app.repository.entity.RealmWalletData
import com.alphawallet.app.service.RealmManager
import io.realm.Case
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.Sort
import timber.log.Timber
import java.io.File

/**
 * TransactionsRealmCache - 基于 Realm 的本地交易缓存
 *
 * 功能：
 * - 读写交易与事件元数据
 * - 查询待确认交易
 * - 标记交易区块高度
 * - 清理钱包相关数据与行情缓存
 */
class TransactionsRealmCache(private val realmManager: RealmManager) : TransactionLocalSource {

    override fun fetchTransaction(wallet: Wallet, hash: String): Transaction? = try {
        realmManager.getRealmInstance(wallet).use { instance ->
            val realmTx = instance.where(RealmTransaction::class.java)
                .equalTo("hash", hash)
                .findFirst()
            realmTx?.let { convert(it) }
        }
    } catch (e: Exception) { null }

    override fun fetchTxCompletionTime(wallet: Wallet, hash: String): Long = try {
        realmManager.getRealmInstance(wallet).use { instance ->
            val realmTx = instance.where(RealmTransaction::class.java)
                .equalTo("hash", hash)
                .findFirst()
            realmTx?.expectedCompletion ?: (System.currentTimeMillis() + 60_000)
        }
    } catch (e: Exception) { System.currentTimeMillis() + 60_000 }

    override fun fetchPendingTransactions(currentAddress: String): Array<Transaction> = try {
        val wallet = Wallet(currentAddress)
        realmManager.getRealmInstance(wallet).use { instance ->
            val pendingTxs = instance.where(RealmTransaction::class.java)
                .equalTo("blockNumber", "-2")
                .or()
                .equalTo("blockNumber", "0")
                .findAll()
            Array(pendingTxs.size) { idx -> convert(pendingTxs[idx]!!) }
        }
    } catch (e: Exception) { emptyArray() }

    override fun fetchActivityMetas(wallet: Wallet, chainId: Long, tokenAddress: String, historyCount: Int): Array<ActivityMeta> {
        val metas = mutableListOf<ActivityMeta>()
        try {
            realmManager.getRealmInstance(wallet).use { instance ->
                val txs = instance.where(RealmTransaction::class.java)
                    .sort("timeStamp", Sort.DESCENDING)
                    .equalTo("chainId", chainId)
                    .findAll()
                Timber.tag("TRC").d("Found %s TX Results", txs.size)
                for (item in txs) {
                    val tx = convert(item)
                    if (tx.isRelated(tokenAddress, wallet.address?:"")) {
                        metas.add(
                            TransactionMeta(
                                item.hash.orEmpty(),
                                item.timeStamp,
                                item.to.orEmpty(),
                                item.chainId,
                                item.blockNumber.orEmpty(),
                            ),
                        )
                        if (metas.size >= historyCount) break
                    }
                }
            }
        } catch (_: Exception) { }
        return metas.toTypedArray()
    }

    override fun fetchEventMetas(wallet: Wallet, networkFilters: List<Long>): Array<ActivityMeta> {
        val metas = mutableListOf<ActivityMeta>()
        try {
            realmManager.getRealmInstance(wallet.address).use { instance ->
                val evs = instance.where(RealmAuxData::class.java)
                    .endsWith("instanceKey", TokensRealmSource.EVENT_CARDS)
                    .findAll()
                Timber.tag("TRC").d("Found %s TX Results", evs.size)
                for (item in evs) {
                    if (!networkFilters.contains(item.chainId)) continue
                    metas.add(EventMeta(item.transactionHash, item.eventName, item.functionId, item.resultTime, item.chainId))
                }
            }
        } catch (_: Exception) { }
        return metas.toTypedArray()
    }

    override fun fetchActivityMetas(wallet: Wallet, networkFilters: List<Long>, fetchTime: Long, fetchLimit: Int): Array<ActivityMeta> {
        val metas = mutableListOf<ActivityMeta>()
        val elementCount = LongSparseArray<Int>()
        try {
            realmManager.getRealmInstance(wallet).use { instance ->
                val txs = generateRealmQuery(instance, fetchTime).findAll()
                Timber.tag("TRC").d("Found %s TX Results", txs.size)
                for (item in txs) {
                    val currentCount = elementCount.get(item.chainId, 0)
                    if (networkFilters.contains(item.chainId) && currentCount < fetchLimit && item.timeStamp > fetchTime) {
                        metas.add(
                            TransactionMeta(
                                item.hash.orEmpty(),
                                item.timeStamp,
                                item.to.orEmpty(),
                                item.chainId,
                                item.blockNumber.orEmpty(),
                            ),
                        )
                        elementCount.put(item.chainId, currentCount + 1)
                    }
                }
            }
        } catch (_: Exception) { }
        return metas.toTypedArray()
    }

    private fun generateRealmQuery(instance: Realm, fetchTime: Long): RealmQuery<RealmTransaction> {
        return if (fetchTime > 0) {
            instance.where(RealmTransaction::class.java)
                .sort("timeStamp", Sort.DESCENDING)
                .beginGroup()
                .lessThan("timeStamp", fetchTime)
                .endGroup()
        } else {
            instance.where(RealmTransaction::class.java)
                .sort("timeStamp", Sort.DESCENDING)
        }
    }

    override fun fetchEvent(walletAddress: String, eventKey: String): RealmAuxData? = try {
        realmManager.getRealmInstance(walletAddress).use { instance ->
            instance.where(RealmAuxData::class.java)
                .equalTo("instanceKey", eventKey)
                .findFirst()
        }
    } catch (e: Exception) { null }

    override fun putTransaction(wallet: Wallet, tx: Transaction): Transaction {
        try {
            realmManager.getRealmInstance(wallet).use { instance ->
                instance.executeTransaction { r ->
                    var realmTx = r.where(RealmTransaction::class.java)
                        .equalTo("hash", tx.hash)
                        .findFirst()
                    if (realmTx == null) {
                        realmTx = r.createObject(RealmTransaction::class.java, tx.hash)
                    }
                    fill(realmTx!!, tx)
                    r.insertOrUpdate(realmTx)
                }
            }
        } catch (e: Exception) {
            Timber.w(e)
        }
        return tx
    }

    override suspend fun deleteAllTickers(): Boolean {
        return try {
            realmManager.getRealmInstance(TokensRealmSource.TICKER_DB).use { instance ->
                instance.executeTransaction { r -> r.deleteAll() }
                instance.refresh()
            }
            true
        } catch (e: Exception) { false }
    }

    override suspend fun deleteAllForWallet(currentAddress: String): Boolean {
        var databaseFile: File? = null
        try {
            realmManager.getRealmInstance(Wallet(currentAddress)).use { instance ->
                databaseFile = File(instance.configuration.path)
                instance.executeTransaction { r ->
                    r.where(RealmToken::class.java).findAll().deleteAllFromRealm()
                    r.where(RealmTransaction::class.java).findAll().deleteAllFromRealm()
                    r.where(RealmAuxData::class.java).findAll().deleteAllFromRealm()
                    r.where(RealmNFTAsset::class.java).findAll().deleteAllFromRealm()
                    r.where(RealmTransfer::class.java).findAll().deleteAllFromRealm()
                    r.where(Realm1559Gas::class.java).findAll().deleteAllFromRealm()
                }
                instance.refresh()
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        if (databaseFile != null && databaseFile!!.exists()) {
            try { databaseFile!!.delete() } catch (e: Exception) { e.printStackTrace() }
        }

        try {
            realmManager.walletDataRealmInstance.use { walletRealm ->
                walletRealm.executeTransaction { r ->
                    val walletData = r.where(RealmWalletData::class.java)
                        .equalTo("address", currentAddress, Case.INSENSITIVE)
                        .findFirst()
                    if (walletData != null) {
                        walletData.balance = "0"
                        walletData.ENSName = ""
                        walletData.ENSAvatar = ""
                    }
                }
                walletRealm.refresh()
            }
        } catch (_: Exception) { }

        return true
    }

    override fun markTransactionBlock(walletAddress: String, hash: String, blockValue: Long) {
        try {
            realmManager.getRealmInstance(Wallet(walletAddress)).use { instance ->
                instance.executeTransactionAsync { r ->
                    val realmTx = r.where(RealmTransaction::class.java)
                        .equalTo("hash", hash)
                        .findFirst()
                    if (realmTx != null) {
                        realmTx.blockNumber = blockValue.toString()
                        realmTx.timeStamp = System.currentTimeMillis() / 1000
                    }
                }
            }
        } catch (_: Exception) { }
    }

    override fun getRealmInstance(wallet: Wallet): Realm = realmManager.getRealmInstance(wallet)

    companion object {
        fun fill(item: RealmTransaction, transaction: Transaction) {
            item.error = transaction.error
            item.blockNumber = transaction.blockNumber
            item.timeStamp = transaction.timeStamp
            item.nonce = transaction.nonce
            item.from = transaction.from
            item.to = transaction.to
            item.value = transaction.value
            item.gas = transaction.gas
            item.gasPrice = transaction.gasPrice
            item.maxFeePerGas = transaction.maxFeePerGas
            item.priorityFee = transaction.maxPriorityFee
            item.input = transaction.input
            item.gasUsed = transaction.gasUsed
            item.chainId = transaction.chainId
        }

        fun convert(rawItem: RealmTransaction): Transaction =
            Transaction(
                rawItem.hash,
                rawItem.error,
                rawItem.blockNumber,
                rawItem.timeStamp,
                rawItem.nonce,
                rawItem.from.orEmpty(),
                rawItem.to.orEmpty(),
                rawItem.value.orEmpty(),
                rawItem.gas.orEmpty(),
                rawItem.gasPrice.orEmpty(),
                rawItem.maxFeePerGas.orEmpty(),
                rawItem.priorityFee.orEmpty(),
                rawItem.input.orEmpty(),
                rawItem.gasUsed.orEmpty(),
                rawItem.chainId,
                rawItem.contractAddress,
            )
    }
}
