package com.alphawallet.app.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alphawallet.app.R
import com.alphawallet.app.entity.ActivityMeta
import com.alphawallet.app.entity.TransactionMeta
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.entity.RealmTransaction
import com.alphawallet.app.repository.entity.RealmTransfer
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.ui.widget.adapter.ActivityAdapter
import com.alphawallet.app.ui.widget.entity.TokenTransferData
import io.realm.Case
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.Sort

/**
 * ActivityHistoryList 是一个自定义 View，用于展示一个 Token 或钱包地址的交易历史记录。
 * 它负责：
 * - 从 Realm 数据库查询交易和代币转移记录。
 * - 监听数据库变更并自动更新 UI。
 * - 管理 RecyclerView 和其 Adapter 的生命周期。
 * - 显示加载状态和“无交易”的提示。
 */
class ActivityHistoryList @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val recentTransactionsView: RecyclerView
    private val noTxNotice: LinearLayout
    private val loadingTransactions: ProgressBar
    private val handler = Handler(Looper.getMainLooper())

    private var activityAdapter: ActivityAdapter? = null
    private var realm: Realm? = null
    private var realmTransactionUpdates: RealmResults<RealmTransaction>? = null

    init {
        inflate(context, R.layout.layout_activity_history, this)

        recentTransactionsView = findViewById(R.id.list)
        recentTransactionsView.layoutManager = LinearLayoutManager(getContext())
        loadingTransactions = findViewById(R.id.loading_transactions)
        noTxNotice = findViewById(R.id.layout_no_recent_transactions)
    }

    /**
     * 设置 RecyclerView 的 Adapter。
     * @param adapter 用于显示活动列表的 ActivityAdapter 实例。
     */
    fun setupAdapter(adapter: ActivityAdapter) {
        this.activityAdapter = adapter
        recentTransactionsView.adapter = adapter
    }

    /**
     * 启动对 Realm 数据库的监听，以获取并显示交易历史。
     * @param realm Realm 数据库实例。
     * @param wallet 当前用户的钱包。
     * @param token 当前关注的 Token。
     * @param svs TokensService 用于检查 Token 类型。
     * @param historyCount 要显示的最大历史记录数量。
     */
    fun startActivityListeners(realm: Realm, wallet: Wallet, token: Token, svs: TokensService, historyCount: Int) {
        this.realm = realm

        activityAdapter?.setItemLimit(historyCount)

        // 根据 Token 类型选择不同的查询策略
        val realmUpdateQuery = if (!token.isEthereum() || svs.isChainToken(token.tokenInfo.chainId, token.getAddress())) {
            getContractListener(token.tokenInfo.chainId, token.getAddress(), historyCount)
        } else {
            getEthListener(token.tokenInfo.chainId, wallet, historyCount)
        }

        // 移除旧的监听器并设置新的异步查询
        realmTransactionUpdates?.removeAllChangeListeners()
        realmTransactionUpdates = realmUpdateQuery.findAllAsync()
        realmTransactionUpdates?.addChangeListener { result ->
            handleRealmTransactions(result, wallet)
        }
    }

    /**
     * 清空 Adapter 中的数据。
     * @return 如果 Adapter 存在并被清空，返回 true；否则返回 false。
     */
    fun resetAdapter(): Boolean {
        return activityAdapter?.let {
            it.clear()
            true
        } ?: false
    }

    /**
     * 当 Realm 数据库中的交易数据发生变化时，处理这些数据。
     * @param realmTransactions 从 Realm 返回的交易结果集。
     * @param wallet 当前用户的钱包。
     */
    private fun handleRealmTransactions(realmTransactions: RealmResults<RealmTransaction>, wallet: Wallet) {
        val metas = mutableListOf<ActivityMeta>()
        var hasPending = false

        for (item in realmTransactions) {
            // 安全检查：如果关键字段为 null 或空，则跳过此条记录
            if (item.hash.isNullOrEmpty() || item.to.isNullOrEmpty() || item.blockNumber.isNullOrEmpty()) {
                continue
            }

            val tm = TransactionMeta(item.hash.orEmpty(), item.timeStamp, item.to.orEmpty(), item.chainId, item.blockNumber.orEmpty())
            metas.add(tm)
            metas.addAll(getRelevantTransfersForHash(tm, wallet))
            if (tm.isPending) hasPending = true
        }

        addItems(metas)

        // 如果有待处理的交易，设置一个延迟任务来刷新 Realm 状态，以检查更新
        if (hasPending) {
            handler.postDelayed({
                if (realm?.isClosed == false) {
                    realm?.refresh()
                }
            }, 5000)
        }
    }

    /**
     * 根据交易哈希查找相关的代币转移（Token Transfer）记录。
     * @param tm 交易元数据。
     * @param wallet 当前用户的钱包。
     * @return 相关的代币转移数据列表。
     */
    private fun getRelevantTransfersForHash(tm: TransactionMeta, wallet: Wallet): List<TokenTransferData> {
        val transferData = mutableListOf<TokenTransferData>()
        val transfers = realm?.where(RealmTransfer::class.java)
            ?.equalTo("hash", RealmTransfer.databaseKey(tm.chainId, tm.hash))
            ?.findAll() ?: return emptyList()

        if (transfers.isNotEmpty()) {
            // 如果只有一个转移，保持交易时间戳；否则，为每个转移微调时间戳以确保排序正确
            var nextTransferTime = if (transfers.size == 1) tm.timeStamp else tm.timeStamp - 1

            transfers.forEach { rt ->
                if (wallet.address?.let { rt.getTransferDetail()?.contains(it) } == true) {
                    val ttd = TokenTransferData(
                        rt.getHash(), tm.chainId,
                        rt.getTokenAddress(), rt.getEventName(), rt.getTransferDetail(), nextTransferTime
                    )
                    transferData.add(ttd)
                    nextTransferTime--
                }
            }

            // 如果一个交易包含多个转移事件，但只有一个与当前钱包相关，
            // 为清晰起见，使用原始交易时间戳，并隐藏原始的ETH交易记录。
            if (transfers.size > 1 && transferData.size == 1) {
                val oldTf = transferData.first()
                transferData.clear()
                transferData.add(
                    TokenTransferData(
                        oldTf.hash, tm.chainId, oldTf.tokenAddress,
                        oldTf.eventName, oldTf.transferDetail, tm.timeStamp
                    )
                )
            }
        }
        return transferData
    }

    /**
     * 构建用于监听合约交易的 Realm 查询。
     * @param chainId 链 ID。
     * @param tokenAddress 合约地址。
     * @param count 限制返回的记录数。
     * @return Realm 查询对象。
     */
    private fun getContractListener(chainId: Long, tokenAddress: String, count: Int): RealmQuery<RealmTransaction> {
        return realm!!.where(RealmTransaction::class.java)
            .sort("timeStamp", Sort.DESCENDING)
            .beginGroup().notEqualTo("input", "0x").and()
            .beginGroup().equalTo("to", tokenAddress, Case.INSENSITIVE)
            .or().equalTo("contractAddress", tokenAddress, Case.INSENSITIVE).endGroup().endGroup()
            .equalTo("chainId", chainId)
            .limit(count.toLong())
    }

    /**
     * 构建用于监听原生币（如 ETH）交易的 Realm 查询。
     * @param chainId 链 ID。
     * @param wallet 当前钱包。
     * @param count 限制返回的记录数。
     * @return Realm 查询对象。
     */
    private fun getEthListener(chainId: Long, wallet: Wallet, count: Int): RealmQuery<RealmTransaction> {
        return realm!!.where(RealmTransaction::class.java)
            .sort("timeStamp", Sort.DESCENDING)
            .beginGroup()
            .equalTo("input", "0x").or().equalTo("input", "")
            .endGroup()
            .beginGroup()
            .equalTo("to", wallet.address, Case.INSENSITIVE)
            .or()
            .equalTo("from", wallet.address, Case.INSENSITIVE)
            .endGroup()
            .equalTo("chainId", chainId)
            .limit(count.toLong())
    }

    /**
     * 将获取到的活动列表更新到 Adapter，并相应地更新 UI 可见性。
     * @param metas 要添加到 Adapter 的活动元数据列表。
     */
    private fun addItems(metas: List<ActivityMeta>) {
        handler.post {
            loadingTransactions.visibility = GONE
            val realmIsSafe = realm?.isClosed == false
            if (metas.isNotEmpty() && realmIsSafe) {
                activityAdapter?.updateActivityItems(metas.toTypedArray())
                recentTransactionsView.visibility = VISIBLE
                noTxNotice.visibility = GONE
            } else if (metas.isEmpty() && activityAdapter?.itemCount == 0 && realmIsSafe) {
                // 仅当适配器中也没有任何项目时，才显示“无交易”
                noTxNotice.visibility = VISIBLE
            }
        }
    }

    /**
     * 在 View 被销毁时调用，用于清理资源和移除监听器。
     */
    fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        realmTransactionUpdates?.removeAllChangeListeners()
        realm?.close()
        activityAdapter?.onDestroy(recentTransactionsView)
    }
}
