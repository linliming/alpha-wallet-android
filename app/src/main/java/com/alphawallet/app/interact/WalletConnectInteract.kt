package com.alphawallet.app.interact

import android.text.TextUtils
import com.alphawallet.app.entity.walletconnect.WalletConnectSessionItem
import com.alphawallet.app.entity.walletconnect.WalletConnectV2SessionItem
import com.walletconnect.web3.wallet.client.Wallet
import com.walletconnect.web3.wallet.client.Web3Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * WalletConnect 会话交互类
 *
 * 负责提供 WalletConnect V2 会话的查询与拉取功能。
 * 将原回调式 API 改造为 Kotlin 协程 suspend API，并保留一层回调包装以兼容旧调用。
 *
 * 主要功能：
 * - 获取会话数量
 * - 获取会话列表（已按过期时间降序排序）
 * - 拉取会话（兼容旧回调 & 新协程接口）
 *
 * 技术特点：
 * - 使用 Hilt 依赖注入
 * - 使用协程在 IO 线程执行 WalletConnect SDK 调用
 * - 详尽的中文注释与错误处理
 */
class WalletConnectInteract @Inject constructor() {

    /**
     * 获取会话数量（同步计算）
     */
    fun getSessionsCount(): Int = getSessions().size

    /**
     * 获取 WalletConnect V2 会话列表（同步）
     * 注意：底层 SDK 调用为同步，若在主线程调用可能阻塞，建议在协程 IO 线程中使用 [fetchSessions]。
     */
    fun getSessions(): MutableList<WalletConnectSessionItem> {
        val result = getWalletConnectV2SessionItems().toMutableList()
        // 按活跃/新旧排序：过期时间从大到小
        result.sortWith { l, r -> java.lang.Long.compare(r.expiryTime, l.expiryTime) }
        return result
    }

    /**
     * 兼容旧代码的回调式拉取方法
     * 建议迁移为 [fetchSessions] 协程版本
     */
    fun fetchSessions(callback: SessionFetchCallback) {
        // 由于 SDK 为同步，直接包装即可；若未来改为耗时操作可改为协程调用
        callback.onFetched(getSessions())
    }

    /**
     * 协程版：拉取 WalletConnect 会话列表
     * 在 IO 线程中执行，返回按过期时间降序的结果
     */
    suspend fun fetchSessions(): List<WalletConnectSessionItem> = withContext(Dispatchers.IO) {
        getSessions()
    }

    /**
     * 内部：收集 WalletConnect V2 会话条目
     */
    private fun getWalletConnectV2SessionItems(): List<WalletConnectSessionItem> {
        val result = mutableListOf<WalletConnectSessionItem>()
        try {
            val listOfSettledSessions: List<Wallet.Model.Session> = Web3Wallet.getListOfActiveSessions()
            for (session in listOfSettledSessions) {
                val md = session.metaData
                if (md != null && !(TextUtils.isEmpty(md.name) && TextUtils.isEmpty(md.url))) {
                    result.add(WalletConnectV2SessionItem(session))
                }
            }
        } catch (e: IllegalStateException) {
            Timber.e(e)
        }
        return result
    }

    /**
     * 兼容旧代码的回调接口
     */
    interface SessionFetchCallback {
        fun onFetched(sessions: MutableList<WalletConnectSessionItem>)
    }
}
