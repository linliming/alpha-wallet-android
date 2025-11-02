package com.alphawallet.app.repository


import com.alphawallet.app.entity.ActivityMeta
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.repository.entity.RealmAuxData
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.token.entity.Signable
import io.realm.Realm
import org.web3j.crypto.RawTransaction
import java.math.BigInteger

/**
 * 交易仓库接口
 *
 * 负责处理与交易相关的操作，包括：
 * 1. 交易签名
 * 2. 交易发送
 * 3. 交易缓存
 * 4. 交易元数据获取
 * 5. 事件元数据获取
 *
 * @author AlphaWallet Team
 * @since 2024
 */
interface TransactionRepositoryType {
    /**
     * 获取签名
     *
     * @param wallet 钱包
     * @param message 可签名消息
     * @return 签名结果
     */
    suspend fun getSignature(wallet: Wallet, message: Signable): SignatureFromKey

    /**
     * 快速获取签名
     *
     * @param wallet 钱包
     * @param password 密码
     * @param message 消息
     * @return 签名字节数组
     */
    suspend fun getSignatureFast(wallet: Wallet, password: String, message: ByteArray): ByteArray

    /**
     * 获取缓存的交易
     *
     * @param walletAddr 钱包地址
     * @param hash 交易哈希
     * @return 交易对象
     */
    fun fetchCachedTransaction(walletAddr: String, hash: String): Transaction?

    /**
     * 获取交易完成时间
     *
     * @param walletAddr 钱包地址
     * @param hash 交易哈希
     * @return 完成时间戳
     */
    fun fetchTxCompletionTime(walletAddr: String, hash: String): Long

    /**
     * 重新发送交易
     *
     * @param from 发送钱包
     * @param to 接收地址
     * @param subunitAmount 金额
     * @param nonce 交易序号
     * @param gasPrice 燃料价格
     * @param gasLimit 燃料限制
     * @param data 数据
     * @param chainId 链ID
     * @return 交易哈希
     */
    suspend fun resendTransaction(
        from: Wallet,
        to: String,
        subunitAmount: BigInteger,
        nonce: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        data: ByteArray,
        chainId: Long
    ): String

    /**
     * 获取缓存的交易元数据
     *
     * @param wallet 钱包
     * @param networkFilters 网络过滤器
     * @param fetchTime 获取时间
     * @param fetchLimit 获取限制
     * @return 活动元数据数组
     */
    fun fetchCachedTransactionMetas(
        wallet: Wallet,
        networkFilters: List<Long>,
        fetchTime: Long,
        fetchLimit: Int
    ): Array<ActivityMeta>

    /**
     * 获取缓存的交易元数据
     *
     * @param wallet 钱包
     * @param chainId 链ID
     * @param tokenAddress 代币地址
     * @param historyCount 历史记录数量
     * @return 活动元数据数组
     */
    fun fetchCachedTransactionMetas(
        wallet: Wallet,
        chainId: Long,
        tokenAddress: String,
        historyCount: Int
    ): Array<ActivityMeta>

    /**
     * 获取事件元数据
     *
     * @param wallet 钱包
     * @param networkFilters 网络过滤器
     * @return 活动元数据数组
     */
    fun fetchEventMetas(
        wallet: Wallet,
        networkFilters: List<Long>
    ): Array<ActivityMeta>

    /**
     * 获取Realm实例
     *
     * @param wallet 钱包
     * @return Realm实例
     */
    fun getRealmInstance(wallet: Wallet): Realm

    /**
     * 获取缓存的事件
     *
     * @param walletAddress 钱包地址
     * @param eventKey 事件键
     * @return Realm辅助数据
     */
    fun fetchCachedEvent(walletAddress: String, eventKey: String): RealmAuxData?

    /**
     * 重启服务
     */
    fun restartService()

    /**
     * 签名交易
     *
     * @param from 发送钱包
     * @param w3Tx Web3交易
     * @param chainId 链ID
     * @return 签名和原始交易对
     */
    suspend fun signTransaction(from: Wallet, w3Tx: Web3Transaction, chainId: Long): Pair<SignatureFromKey, RawTransaction>

    /**
     * 格式化原始交易
     *
     * @param w3Tx Web3交易
     * @param nonce 交易序号
     * @param chainId 链ID
     * @return 原始交易
     */
    fun formatRawTransaction(w3Tx: Web3Transaction, nonce: Long, chainId: Long): RawTransaction

    /**
     * 发送交易
     *
     * @param from 发送钱包
     * @param rtx 原始交易
     * @param sigData 签名数据
     * @param chainId 链ID
     * @return 交易哈希
     */
    suspend fun sendTransaction(from: Wallet, rtx: RawTransaction, sigData: SignatureFromKey, chainId: Long): String

    /**
     * 从节点获取交易
     *
     * @param walletAddress 钱包地址
     * @param chainId 链ID
     * @param hash 交易哈希
     * @return 交易对象
     */
    suspend fun fetchTransactionFromNode(walletAddress: String, chainId: Long, hash: String): Transaction
}
