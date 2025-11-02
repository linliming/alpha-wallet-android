package com.alphawallet.app.interact

import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.entity.AnalyticsProperties
import com.alphawallet.app.entity.CryptoFunctions.sigFromByteArray
import com.alphawallet.app.entity.MessagePair
import com.alphawallet.app.entity.SignaturePair
import com.alphawallet.app.entity.TransactionReturn
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.TransactionRepositoryType
import com.alphawallet.app.service.AnalyticsServiceType
import com.alphawallet.app.service.KeystoreAccountService
import com.alphawallet.app.service.TransactionSendHandlerInterface
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.hardware.SignatureReturnType
import com.alphawallet.token.entity.Signable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.crypto.transaction.type.Transaction1559
import java.math.BigInteger

/**
 * 创建交易交互类
 *
 * 负责处理交易的创建、签名和发送，包括：
 * 1. 交易签名请求
 * 2. 交易发送
 * 3. 交易重发
 * 4. 错误处理
 * 5. 分析事件追踪
 *
 * @param transactionRepository 交易仓库
 * @param analyticsService 分析服务
 *
 * @author AlphaWallet Team
 * @since 2024
 */
class CreateTransactionInteract(
    private val transactionRepository: TransactionRepositoryType,
    private val analyticsService: AnalyticsServiceType<AnalyticsProperties>
) {
    private var txInterface: TransactionSendHandlerInterface? = null

    /**
     * 缓存nonce以避免需要重新计算。硬件钱包在成功或取消之前会阻止所有功能，所以这样做是安全的
     * 注意：如果钱包升级为同时签署多个交易，则需要重新考虑这一点
     */
    private var nonceForHardwareSign: Long = 0

    /**
     * 签名消息对
     *
     * @param wallet 钱包
     * @param messagePair 消息对
     * @return 签名对
     */
    suspend fun sign(wallet: Wallet, messagePair: MessagePair): SignaturePair {
        val sig = transactionRepository.getSignature(wallet, messagePair)
        return SignaturePair(messagePair.selection, sig, messagePair.message)
    }

    /**
     * 签名可签名消息
     *
     * @param wallet 钱包
     * @param message 可签名消息
     * @return 签名结果
     */
    suspend fun sign(wallet: Wallet, message: Signable): SignatureFromKey {
        return transactionRepository.getSignature(wallet, message)
    }

    /**
     * 请求签名
     *
     * @param w3Tx Web3交易
     * @param wallet 钱包
     * @param chainId 链ID
     * @param txInterface 交易发送处理接口
     */
    fun requestSignature(w3Tx: Web3Transaction, wallet: Wallet, chainId: Long, txInterface: TransactionSendHandlerInterface) {
        this.txInterface = txInterface
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val signaturePackage = createWithSigId(wallet, w3Tx, chainId)
                withContext(Dispatchers.Main) {
                    completeSendTransaction(wallet, chainId, signaturePackage, w3Tx)
                }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) {
                    handleTransactionError(error, w3Tx)
                }
            }
        }
    }

    /**
     * 处理交易错误
     *
     * @param error 错误
     * @param w3Tx Web3交易
     */
    private fun handleTransactionError(error: Throwable, w3Tx: Web3Transaction) {
        txInterface?.transactionError(TransactionReturn(error, w3Tx))
        trackTransactionError(error.message ?: "Unknown error")
    }

    /**
     * 请求签名交易
     *
     * @param w3Tx Web3交易
     * @param wallet 钱包
     * @param chainId 链ID
     * @param txInterface 交易发送处理接口
     */
    fun requestSignTransaction(w3Tx: Web3Transaction, wallet: Wallet, chainId: Long, txInterface: TransactionSendHandlerInterface) {
        this.txInterface = txInterface
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val signaturePackage = createWithSigId(wallet, w3Tx, chainId)
                withContext(Dispatchers.Main) {
                    completeSignTransaction(chainId, signaturePackage, w3Tx)
                }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) {
                    handleTransactionError(error, w3Tx)
                }
            }
        }
    }

    /**
     * 创建带签名ID的交易
     *
     * @param from 发送钱包
     * @param w3Tx Web3交易
     * @param chainId 链ID
     * @return 签名和原始交易对
     */
    suspend fun createWithSigId(from: Wallet, w3Tx: Web3Transaction, chainId: Long): Pair<SignatureFromKey, RawTransaction> {
        return transactionRepository.signTransaction(from, w3Tx, chainId)
    }

    /**
     * 从硬件钱包返回并发送交易
     *
     * @param wallet 钱包
     * @param chainId 链ID
     * @param w3Tx Web3交易
     * @param sigData 签名数据
     */
    fun sendTransaction(wallet: Wallet, chainId: Long, w3Tx: Web3Transaction, sigData: SignatureFromKey) {
        val rtx = transactionRepository.formatRawTransaction(w3Tx, nonceForHardwareSign, chainId)
        sendTransaction(wallet, chainId, rtx, sigData, w3Tx)
    }

    /**
     * 发送交易
     *
     * @param wallet 钱包
     * @param chainId 链ID
     * @param rtx 原始交易
     * @param sigData 签名数据
     * @param w3Tx Web3交易
     */
    fun sendTransaction(wallet: Wallet, chainId: Long, rtx: RawTransaction, sigData: SignatureFromKey, w3Tx: Web3Transaction) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val txHash = transactionRepository.sendTransaction(wallet, rtx, rlpEncodeSignature(rtx, sigData, chainId), chainId)
                withContext(Dispatchers.Main) {
                    txInterface?.transactionFinalised(TransactionReturn(txHash, w3Tx))
                    trackTransactionCount(chainId)
                    val props = AnalyticsProperties().apply {
                        put(Analytics.PROPS_TRANSACTION_TYPE, "send")
                        put(Analytics.PROPS_TRANSACTION_CHAIN_ID, chainId.toString())
                    }
                    analyticsService.track(Analytics.Navigation.ACTION_SHEET_FOR_TRANSACTION_CONFIRMATION_SUCCESSFUL.getValue(), props)
                }
            } catch (error: Throwable) {
                withContext(Dispatchers.Main) {
                    handleTransactionError(error, w3Tx)
                }
            }
        }
    }

    /**
     * 签名交易
     *
     * @param chainId 链ID
     * @param w3Tx Web3交易
     * @param sigData 签名数据
     */
    fun signTransaction(chainId: Long, w3Tx: Web3Transaction, sigData: SignatureFromKey) {
        val rtx = transactionRepository.formatRawTransaction(w3Tx, nonceForHardwareSign, chainId)
        txInterface?.transactionSigned(rlpEncodeSignature(rtx, sigData, chainId), w3Tx)

        trackTransactionCount(chainId)
        val props = AnalyticsProperties().apply {
            put(Analytics.PROPS_TRANSACTION_TYPE, "sign")
            put(Analytics.PROPS_TRANSACTION_CHAIN_ID, chainId.toString())
        }
        analyticsService.track(Analytics.Navigation.ACTION_SHEET_FOR_TRANSACTION_CONFIRMATION_SUCCESSFUL.getValue(), props)
    }

    /**
     * 重发交易
     *
     * @param from 发送钱包
     * @param nonce 交易序号
     * @param to 接收地址
     * @param subunitAmount 金额
     * @param gasPrice 燃气价格
     * @param gasLimit 燃气限制
     * @param data 数据
     * @param chainId 链ID
     * @return 交易哈希
     */
    suspend fun resend(from: Wallet, nonce: BigInteger, to: String, subunitAmount: BigInteger, gasPrice: BigInteger, gasLimit: BigInteger, data: ByteArray, chainId: Long): String {
        return transactionRepository.resendTransaction(from, to, subunitAmount, nonce, gasPrice, gasLimit, data, chainId)
    }

    /**
     * 完成发送交易
     *
     * @param wallet 钱包
     * @param chainId 链ID
     * @param signaturePackage 签名包
     * @param w3Tx Web3交易
     */
    private fun completeSendTransaction(wallet: Wallet, chainId: Long, signaturePackage: Pair<SignatureFromKey, RawTransaction>, w3Tx: Web3Transaction) {
        when (signaturePackage.first.sigType) {
            SignatureReturnType.SIGNATURE_GENERATED -> {
                sendTransaction(wallet, chainId, signaturePackage.second, signaturePackage.first, w3Tx)
            }
            SignatureReturnType.SIGNING_POSTPONED -> {
                // 记录nonce
                nonceForHardwareSign = signaturePackage.second.nonce.toLong()
            }
            SignatureReturnType.KEY_FILE_ERROR,
            SignatureReturnType.KEY_AUTHENTICATION_ERROR,
            SignatureReturnType.KEY_CIPHER_ERROR -> {
                val errorMessage = signaturePackage.first.failMessage
                handleTransactionError(Throwable(errorMessage), w3Tx)
            }
            else -> {
                val message = "未实现的签名类型"
                trackTransactionError(message)
                throw RuntimeException(message)
            }
        }
    }

    /**
     * 完成签名交易
     *
     * @param chainId 链ID
     * @param signaturePackage 签名包
     * @param w3Tx Web3交易
     */
    private fun completeSignTransaction(chainId: Long, signaturePackage: Pair<SignatureFromKey, RawTransaction>, w3Tx: Web3Transaction) {
        when (signaturePackage.first.sigType) {
            SignatureReturnType.SIGNATURE_GENERATED -> {
                signTransaction(chainId, w3Tx, signaturePackage.first)
            }
            SignatureReturnType.SIGNING_POSTPONED -> {
                // 记录nonce
                nonceForHardwareSign = signaturePackage.second.nonce.toLong()
            }
            SignatureReturnType.KEY_FILE_ERROR,
            SignatureReturnType.KEY_AUTHENTICATION_ERROR,
            SignatureReturnType.KEY_CIPHER_ERROR -> {
                val errorMessage = signaturePackage.first.failMessage
                handleTransactionError(Throwable(errorMessage), w3Tx)
            }
            else -> {
                val message = "未实现的签名类型"
                trackTransactionError(message)
                throw RuntimeException(message)
            }
        }
    }

    /**
     * RLP编码签名
     *
     * @param rtx 原始交易
     * @param sigData 签名数据
     * @param chainId 链ID
     * @return 签名结果
     */
    private fun rlpEncodeSignature(rtx: RawTransaction, sigData: SignatureFromKey, chainId: Long): SignatureFromKey {
        if (rtx.transaction is Transaction1559) {
            sigData.signature = KeystoreAccountService.encode(rtx, sigFromByteArray(sigData.signature))
        } else {
            val sig = TransactionEncoder.createEip155SignatureData(sigFromByteArray(sigData.signature), chainId)
            sigData.signature = KeystoreAccountService.encode(rtx, sig)
        }

        return sigData
    }

    /**
     * 追踪交易错误
     *
     * @param message 错误消息
     */
    private fun trackTransactionError(message: String) {
        val props = AnalyticsProperties().apply {
            put(Analytics.PROPS_ERROR_MESSAGE, message)
        }
        analyticsService.track(Analytics.Navigation.ACTION_SHEET_FOR_TRANSACTION_CONFIRMATION_FAILED.getValue())
    }

    /**
     * 追踪交易计数
     *
     * @param chainId 链ID
     */
    private fun trackTransactionCount(chainId: Long) {
        analyticsService.increment(
            if (EthereumNetworkBase.hasRealValue(chainId))
                Analytics.UserProperties.TRANSACTION_COUNT.getValue()
            else
                Analytics.UserProperties.TRANSACTION_COUNT_TESTNET.getValue()
        )
    }
}
