package com.alphawallet.app.interact

import com.alphawallet.app.entity.ActivityMeta
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.TokenInfo
import com.alphawallet.app.repository.TokenRepositoryType
import com.alphawallet.app.repository.TransactionRepositoryType
import com.alphawallet.app.repository.entity.RealmAuxData
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 交易获取交互类
 *
 * 负责处理与交易相关的异步操作，使用协程替代RxJava。
 * 主要功能包括：
 * 1. 交易元数据获取
 * 2. 事件元数据获取
 * 3. 代币接口规范查询
 * 4. 交易缓存管理
 * 5. 交易服务重启
 *
 * @param transactionRepository 交易仓库
 * @param tokenRepository 代币仓库
 *
 * @author AlphaWallet Team
 * @since 2024
 */
class FetchTransactionsInteract(
    private val transactionRepository: TransactionRepositoryType,
    private val tokenRepository: TokenRepositoryType
) {

    /**
     * 获取交易元数据
     *
     * @param wallet 钱包信息
     * @param networkFilters 网络过滤器
     * @param fetchTime 获取时间
     * @param fetchLimit 获取限制
     * @return 活动元数据数组
     */
    suspend fun fetchTransactionMetas(
        wallet: Wallet,
        networkFilters: List<Long>,
        fetchTime: Long,
        fetchLimit: Int
    ): Array<ActivityMeta> = withContext(Dispatchers.IO) {
        transactionRepository.fetchCachedTransactionMetas(wallet, networkFilters, fetchTime, fetchLimit)
    }

    /**
     * 获取事件元数据
     *
     * @param wallet 钱包信息
     * @param networkFilters 网络过滤器
     * @return 活动元数据数组
     */
    suspend fun fetchEventMetas(
        wallet: Wallet,
        networkFilters: List<Long>
    ): Array<ActivityMeta> = withContext(Dispatchers.IO) {
        transactionRepository.fetchEventMetas(wallet, networkFilters)
    }

    /**
     * 查询接口规范
     * 可以通过余额检查和查看小数位来解析erc20、erc721、erc875和erc1155
     *
     * @param tokenInfo 代币信息
     * @return 合约类型
     */
    suspend fun queryInterfaceSpec(tokenInfo: TokenInfo): ContractType? = withContext(Dispatchers.IO) {
        tokenRepository.determineCommonType(tokenInfo)
    }

    /**
     * 获取缓存的交易
     *
     * @param walletAddress 钱包地址
     * @param hash 交易哈希
     * @return 交易对象
     */
    fun fetchCached(walletAddress: String, hash: String): Transaction? {
        return transactionRepository.fetchCachedTransaction(walletAddress, hash)
    }

    /**
     * 获取交易完成时间
     *
     * @param walletAddr 钱包地址
     * @param hash 交易哈希
     * @return 完成时间戳
     */
    fun fetchTxCompletionTime(walletAddr: String, hash: String): Long {
        return transactionRepository.fetchTxCompletionTime(walletAddr, hash)
    }

    /**
     * 获取Realm实例
     *
     * @param wallet 钱包信息
     * @return Realm实例
     */
    fun getRealmInstance(wallet: Wallet): Realm {
        return transactionRepository.getRealmInstance(wallet)
    }

    /**
     * 获取事件
     *
     * @param walletAddress 钱包地址
     * @param eventKey 事件键
     * @return Realm辅助数据
     */
    fun fetchEvent(walletAddress: String, eventKey: String): RealmAuxData? {
        return transactionRepository.fetchCachedEvent(walletAddress, eventKey)
    }

    /**
     * 重启交易服务
     */
    fun restartTransactionService() {
        transactionRepository.restartService()
    }

    /**
     * 从节点获取交易
     *
     * @param walletAddress 钱包地址
     * @param chainId 链ID
     * @param hash 交易哈希
     * @return 交易对象
     */
    suspend fun fetchFromNode(
        walletAddress: String,
        chainId: Long,
        hash: String
    ): Transaction = withContext(Dispatchers.IO) {
        transactionRepository.fetchTransactionFromNode(walletAddress, chainId, hash)
    }
}
