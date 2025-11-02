package com.alphawallet.app.web3j

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthBlock
import org.web3j.protocol.core.methods.response.EthLog
import org.web3j.protocol.core.methods.response.TransactionReceipt
import timber.log.Timber
import java.math.BigInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Web3j 协程扩展类
 * 提供协程版本的 Web3j 方法，替代 RxJava 调用
 *
 * @author AlphaWallet Team
 * @since 2024
 */
class Web3jCoroutineExtensions(
    private val web3j: Web3j,
) {
    companion object {
        private const val TAG = "Web3jCoroutineExtensions"
    }

    /**
     * 获取账户余额
     *
     * @param address 账户地址
     * @return 余额（Wei）
     */
    suspend fun getBalance(address: String): BigInteger =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    web3j
                        .ethGetBalance(address, DefaultBlockParameterName.LATEST)
                        .sendAsync()
                        .whenComplete { response, throwable ->
                            if (throwable != null) {
                                Timber.e(throwable, "Error getting balance for address: $address")
                                continuation.resumeWithException(throwable)
                            } else {
                                continuation.resume(response.balance)
                            }
                        }
                } catch (e: Exception) {
                    Timber.e(e, "Error getting balance for address: $address")
                    continuation.resumeWithException(e)
                }
            }
        }

    /**
     * 调用智能合约（不消耗 gas）
     *
     * @param to 合约地址
     * @param data 调用数据
     * @param from 调用者地址（可选）
     * @return 调用结果
     */
    suspend fun callSmartContract(
        to: String,
        data: String,
        from: String? = null,
    ): String =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val request =
                        Transaction.createFunctionCallTransaction(
                            from,
                            null,
                            null,
                            null,
                            to,
                            null,
                            data,
                        )

                    web3j
                        .ethCall(request, DefaultBlockParameterName.LATEST)
                        .sendAsync()
                        .whenComplete { response, throwable ->
                            if (throwable != null) {
                                Timber.e(throwable, "Error calling smart contract: $to")
                                continuation.resumeWithException(throwable)
                            } else {
                                continuation.resume(response.value)
                            }
                        }
                } catch (e: Exception) {
                    Timber.e(e, "Error calling smart contract: $to")
                    continuation.resumeWithException(e)
                }
            }
        }

    /**
     * 获取当前区块号
     *
     * @return 区块号
     */
    suspend fun getBlockNumber(): BigInteger =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    web3j
                        .ethBlockNumber()
                        .sendAsync()
                        .whenComplete { response, throwable ->
                            if (throwable != null) {
                                Timber.e(throwable, "Error getting block number")
                                continuation.resumeWithException(throwable)
                            } else {
                                continuation.resume(response.blockNumber)
                            }
                        }
                } catch (e: Exception) {
                    Timber.e(e, "Error getting block number")
                    continuation.resumeWithException(e)
                }
            }
        }

    /**
     * 获取 Gas 价格
     *
     * @return Gas 价格（Wei）
     */
    suspend fun getGasPrice(): BigInteger =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    web3j
                        .ethGasPrice()
                        .sendAsync()
                        .whenComplete { response, throwable ->
                            if (throwable != null) {
                                Timber.e(throwable, "Error getting gas price")
                                continuation.resumeWithException(throwable)
                            } else {
                                continuation.resume(response.gasPrice)
                            }
                        }
                } catch (e: Exception) {
                    Timber.e(e, "Error getting gas price")
                    continuation.resumeWithException(e)
                }
            }
        }

    /**
     * 估算 Gas 消耗
     *
     * @param from 发送地址
     * @param to 接收地址
     * @param data 交易数据
     * @return 估算的 Gas 消耗
     */
    suspend fun estimateGas(
        from: String,
        to: String?,
        data: String? = null,
    ): BigInteger =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val request =
                        Transaction.createFunctionCallTransaction(
                            from,
                            null,
                            null,
                            null,
                            to,
                            null,
                            data ?: "",
                        )

                    web3j
                        .ethEstimateGas(request)
                        .sendAsync()
                        .whenComplete { response, throwable ->
                            if (throwable != null) {
                                Timber.e(throwable, "Error estimating gas")
                                continuation.resumeWithException(throwable)
                            } else {
                                continuation.resume(response.amountUsed)
                            }
                        }
                } catch (e: Exception) {
                    Timber.e(e, "Error estimating gas")
                    continuation.resumeWithException(e)
                }
            }
        }

    /**
     * 获取账户 Nonce
     *
     * @param address 账户地址
     * @return Nonce 值
     */
    suspend fun getTransactionCount(address: String): BigInteger =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    web3j
                        .ethGetTransactionCount(address, DefaultBlockParameterName.PENDING)
                        .sendAsync()
                        .whenComplete { response, throwable ->
                            if (throwable != null) {
                                Timber.e(throwable, "Error getting transaction count for: $address")
                                continuation.resumeWithException(throwable)
                            } else {
                                continuation.resume(response.transactionCount)
                            }
                        }
                } catch (e: Exception) {
                    Timber.e(e, "Error getting transaction count for: $address")
                    continuation.resumeWithException(e)
                }
            }
        }

    /**
     * 发送原始交易
     *
     * @param signedTransactionData 已签名的交易数据
     * @return 交易哈希
     */
    suspend fun sendRawTransaction(signedTransactionData: String): String =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    web3j
                        .ethSendRawTransaction(signedTransactionData)
                        .sendAsync()
                        .whenComplete { response, throwable ->
                            if (throwable != null) {
                                Timber.e(throwable, "Error sending raw transaction")
                                continuation.resumeWithException(throwable)
                            } else {
                                continuation.resume(response.transactionHash)
                            }
                        }
                } catch (e: Exception) {
                    Timber.e(e, "Error sending raw transaction")
                    continuation.resumeWithException(e)
                }
            }
        }

    /**
     * 获取交易收据
     *
     * @param transactionHash 交易哈希
     * @return 交易收据
     */
    suspend fun getTransactionReceipt(transactionHash: String): TransactionReceipt? =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    web3j
                        .ethGetTransactionReceipt(transactionHash)
                        .sendAsync()
                        .whenComplete { response, throwable ->
                            if (throwable != null) {
                                Timber.e(throwable, "Error getting transaction receipt: $transactionHash")
                                continuation.resumeWithException(throwable)
                            } else {
                                continuation.resume(response.transactionReceipt.orElse(null))
                            }
                        }
                } catch (e: Exception) {
                    Timber.e(e, "Error getting transaction receipt: $transactionHash")
                    continuation.resumeWithException(e)
                }
            }
        }

    /**
     * 获取事件日志
     *
     * @param filter 日志过滤器
     * @return 日志列表
     */
    suspend fun getLogs(filter: EthFilter): List<EthLog.LogResult<*>> =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    web3j
                        .ethGetLogs(filter)
                        .sendAsync()
                        .whenComplete { response, throwable ->
                            if (throwable != null) {
                                Timber.e(throwable, "Error getting logs")
                                continuation.resumeWithException(throwable)
                            } else {
                                continuation.resume(response.logs)
                            }
                        }
                } catch (e: Exception) {
                    Timber.e(e, "Error getting logs")
                    continuation.resumeWithException(e)
                }
            }
        }

    /**
     * 获取区块信息
     *
     * @param blockNumber 区块号
     * @param returnFullTransactionObjects 是否返回完整交易对象
     * @return 区块信息
     */
    suspend fun getBlockByNumber(
        blockNumber: DefaultBlockParameterName,
        returnFullTransactionObjects: Boolean = false,
    ): EthBlock.Block? =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                try {
                    web3j
                        .ethGetBlockByNumber(blockNumber, returnFullTransactionObjects)
                        .sendAsync()
                        .whenComplete { response, throwable ->
                            if (throwable != null) {
                                Timber.e(throwable, "Error getting block by number")
                                continuation.resumeWithException(throwable)
                            } else {
                                continuation.resume(response.block)
                            }
                        }
                } catch (e: Exception) {
                    Timber.e(e, "Error getting block by number")
                    continuation.resumeWithException(e)
                }
            }
        }
}
