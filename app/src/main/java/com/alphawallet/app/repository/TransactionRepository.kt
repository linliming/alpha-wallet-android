package com.alphawallet.app.repository

import android.text.TextUtils
import com.alphawallet.app.entity.ActivityMeta
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.repository.TokenRepository.Companion.getWeb3jService
import com.alphawallet.app.repository.entity.RealmAuxData
import com.alphawallet.app.service.AccountKeystoreService
import com.alphawallet.app.service.TransactionsService
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.hardware.SignatureFromKey
import com.alphawallet.hardware.SignatureReturnType
import com.alphawallet.token.entity.Signable
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.transaction.type.ITransaction
import org.web3j.crypto.transaction.type.Transaction1559
import org.web3j.protocol.Web3j
import org.web3j.utils.Numeric
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 交易仓库实现类
 *
 * 负责处理与交易相关的操作，包括：
 * 1. 交易签名
 * 2. 交易发送
 * 3. 交易缓存
 * 4. 交易元数据获取
 * 5. 事件元数据获取
 *
 * @param networkRepository 网络仓库
 * @param accountKeystoreService 账户密钥库服务
 * @param inDiskCache 磁盘缓存
 * @param transactionsService 交易服务
 *
 * @author AlphaWallet Team
 * @since 2024
 */
@Singleton
class TransactionRepository @Inject constructor(
    private val networkRepository: EthereumNetworkRepositoryType,
    private val accountKeystoreService: AccountKeystoreService,
    private val inDiskCache: TransactionLocalSource,
    private val transactionsService: TransactionsService
) : TransactionRepositoryType {

    private val tag = "TREPO"

    override fun fetchCachedTransaction(walletAddr: String, hash: String): Transaction? {
        val wallet = Wallet(walletAddr)
        return inDiskCache.fetchTransaction(wallet, hash)
    }

    override fun fetchTxCompletionTime(walletAddr: String, hash: String): Long {
        val wallet = Wallet(walletAddr)
        return inDiskCache.fetchTxCompletionTime(wallet, hash)
    }

    override suspend fun signTransaction(from: Wallet, w3Tx: Web3Transaction, chainId: Long): Pair<SignatureFromKey, RawTransaction> = withContext(Dispatchers.IO) {

        val txNonce = getNonceForTransaction(getWeb3jService(chainId), from.address, w3Tx.nonce)
        val rtx = formatRawTransaction(w3Tx, txNonce.toLong(), chainId)
        val signature = accountKeystoreService.signTransaction(from, chainId, rtx)
        Pair(signature, rtx)
    }

    override fun formatRawTransaction(w3Tx: Web3Transaction, nonce: Long, chainId: Long): RawTransaction {
        val toAddress = w3Tx.transactionDestination?.toString().orEmpty()
        val amount = w3Tx.value ?: BigInteger.ZERO
        val gasLimit = w3Tx.gasLimit ?: BigInteger.ZERO

        return if (w3Tx.isLegacyTransaction) {
            val gasPrice = w3Tx.gasPrice ?: BigInteger.ZERO
            formatRawTransaction(
                toAddress,
                amount,
                gasPrice,
                gasLimit,
                nonce,
                if (!TextUtils.isEmpty(w3Tx.payload)) Numeric.hexStringToByteArray(w3Tx.payload) else ByteArray(0),
                w3Tx.isConstructor
            )
        } else {
            val maxPriorityFee = w3Tx.maxPriorityFeePerGas ?: BigInteger.ZERO
            val maxFee = w3Tx.maxFeePerGas ?: BigInteger.ZERO
            formatRawTransaction(
                toAddress,
                chainId,
                amount,
                gasLimit,
                maxPriorityFee,
                maxFee,
                nonce,
                if (!TextUtils.isEmpty(w3Tx.payload)) Numeric.hexStringToByteArray(w3Tx.payload) else ByteArray(0),
                w3Tx.isConstructor
            )
        }
    }

    override suspend fun sendTransaction(from: Wallet, rtx: RawTransaction, sigData: SignatureFromKey, chainId: Long): String = withContext(Dispatchers.IO) {
        if (sigData.sigType != SignatureReturnType.SIGNATURE_GENERATED) {
            throw Exception(sigData.failMessage)
        }
        val raw = getWeb3jService(chainId)
            .ethSendRawTransaction(Numeric.toHexString(sigData.signature))
            .send()
        if (raw.hasError()) {
            throw Exception(raw.error.message)
        }
        val txHash = raw.transactionHash
        storeUnconfirmedTransaction(from, txHash, rtx.transaction, chainId, if (rtx.data.length > 2) rtx.to else "")
    }

    override suspend fun resendTransaction(
        from: Wallet,
        to: String,
        subunitAmount: BigInteger,
        nonce: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        data: ByteArray,
        chainId: Long
    ): String = withContext(Dispatchers.IO) {
        val web3j = getWeb3jService(chainId)
        val useGasPrice = gasPriceForNode(chainId, gasPrice)

        val signedMessage = accountKeystoreService.signTransaction(from, to, subunitAmount, useGasPrice, gasLimit, nonce.toLong(), data, chainId)
        if (signedMessage.sigType != SignatureReturnType.SIGNATURE_GENERATED) {
            throw Exception(signedMessage.failMessage)
        }
        val raw = web3j
            .ethSendRawTransaction(Numeric.toHexString(signedMessage.signature))
            .send()
        if (raw.hasError()) {
            throw Exception(raw.error.message)
        }
        val txHash = raw.transactionHash
        storeUnconfirmedTransaction(from, txHash, to, subunitAmount, nonce, useGasPrice, gasLimit, chainId, if (data != null) Numeric.toHexString(data) else "0x")
    }

    private fun gasPriceForNode(chainId: Long, gasPrice: BigInteger): BigInteger {
        // 使用 networkRepository 实例而不是静态调用
        return if (networkRepository is EthereumNetworkBase && (networkRepository as EthereumNetworkBase).hasGasOverride(chainId)) {
            (networkRepository as EthereumNetworkBase).gasOverrideValue(chainId)
        } else {
            gasPrice
        }
    }

    private suspend fun storeUnconfirmedTransaction(from: Wallet, txHash: String, itx: ITransaction, chainId: Long, contractAddr: String): String = withContext(Dispatchers.IO) {
        val newTx: Transaction = if (itx is Transaction1559) {
            Transaction(
                txHash, "0", "0", System.currentTimeMillis() / 1000, itx.nonce.toInt(), from.address?:"",
                itx.to, itx.value.toString(10), "0", "0", itx.maxFeePerGas.toString(10),
                itx.maxPriorityFeePerGas.toString(10), itx.data,
                itx.gasLimit.toString(10), chainId, contractAddr
            )
        } else {
            Transaction(
                txHash, "0", "0", System.currentTimeMillis() / 1000, itx.nonce.toInt(), from.address?:"",
                itx.to, itx.value.toString(10), "0", itx.gasPrice.toString(10), itx.data,
                itx.gasLimit.toString(10), chainId, contractAddr, ""
            )
        }

        inDiskCache.putTransaction(from, newTx)
        transactionsService.markPending(newTx)

        txHash
    }

    private suspend fun storeUnconfirmedTransaction(
        from: Wallet,
        txHash: String,
        toAddress: String,
        value: BigInteger,
        nonce: BigInteger,
        gasPrice: BigInteger,
        gasLimit: BigInteger,
        chainId: Long,
        data: String
    ): String = withContext(Dispatchers.IO) {
        val newTx = Transaction(
            txHash, "0", "0", System.currentTimeMillis() / 1000, nonce.toInt(), from.address?:"",
            toAddress, value.toString(10), "0", gasPrice.toString(10), data,
            gasLimit.toString(10), chainId, "", ""
        )
        inDiskCache.putTransaction(from, newTx)
        transactionsService.markPending(newTx)

        txHash
    }

    override suspend fun getSignature(wallet: Wallet, message: Signable): SignatureFromKey = withContext(Dispatchers.IO) {
        accountKeystoreService.signMessage(wallet, message)
    }

    override suspend fun getSignatureFast(wallet: Wallet, password: String, message: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        accountKeystoreService.signMessageFast(wallet, password, message)
    }

    override fun fetchCachedTransactionMetas(wallet: Wallet, networkFilters: List<Long>, fetchTime: Long, fetchLimit: Int): Array<ActivityMeta> {
        return inDiskCache.fetchActivityMetas(wallet, networkFilters, fetchTime, fetchLimit)
    }

    override fun fetchCachedTransactionMetas(wallet: Wallet, chainId: Long, tokenAddress: String, historyCount: Int): Array<ActivityMeta> {
        return inDiskCache.fetchActivityMetas(wallet, chainId, tokenAddress, historyCount)
    }

    override fun fetchEventMetas(wallet: Wallet, networkFilters: List<Long>): Array<ActivityMeta> {
        return inDiskCache.fetchEventMetas(wallet, networkFilters)
    }

    override fun getRealmInstance(wallet: Wallet): Realm {
        return inDiskCache.getRealmInstance(wallet)
    }

    override fun fetchCachedEvent(walletAddress: String, eventKey: String): RealmAuxData? {
        return inDiskCache.fetchEvent(walletAddress, eventKey)
    }

    override fun restartService() {
        transactionsService.startUpdateCycle()
    }

    override suspend fun fetchTransactionFromNode(walletAddress: String, chainId: Long, hash: String): Transaction = withContext(Dispatchers.IO) {
        val tx = transactionsService.fetchTransactionSuspend(walletAddress, chainId, hash)
        inDiskCache.putTransaction(Wallet(walletAddress), tx)
    }

    private suspend fun getNonceForTransaction(web3j: Web3j, wallet: String?, nonce: Long): BigInteger = withContext(Dispatchers.IO) {
        if (nonce != -1L) { // 使用提供的nonce
            BigInteger.valueOf(nonce)
        } else {
            val walletAddress:String = wallet ?: ""
            networkRepository.getLastTransactionNonce(web3j, walletAddress)
        }
    }

    /**
     * 格式化传统交易
     */
    private fun formatRawTransaction(toAddress: String, amount: BigInteger, gasPrice: BigInteger, gasLimit: BigInteger, nonce: Long, data: ByteArray, isConstructor: Boolean): RawTransaction {
        val dataStr = if (data.isNotEmpty()) Numeric.toHexString(data) else ""

        return if (isConstructor) {
            RawTransaction.createContractTransaction(
                BigInteger.valueOf(nonce),
                gasPrice,
                gasLimit,
                amount,
                dataStr
            )
        } else {
            RawTransaction.createTransaction(
                BigInteger.valueOf(nonce),
                gasPrice,
                gasLimit,
                toAddress,
                amount,
                dataStr
            )
        }
    }

    /**
     * 格式化ERC1559交易
     */
    private fun formatRawTransaction(
        toAddress: String,
        chainId: Long,
        amount: BigInteger,
        gasLimit: BigInteger,
        maxPriorityFeePerGas: BigInteger,
        maxFeePerGas: BigInteger,
        nonce: Long,
        data: ByteArray,
        isConstructor: Boolean
    ): RawTransaction {
        val dataStr = if (data.isNotEmpty()) Numeric.toHexString(data) else ""

        return if (isConstructor) {
            RawTransaction.createTransaction(
                chainId,
                BigInteger.valueOf(nonce),
                gasLimit,
                "",
                amount,
                dataStr,
                maxPriorityFeePerGas,
                maxFeePerGas
            )
        } else {
            RawTransaction.createTransaction(
                chainId,
                BigInteger.valueOf(nonce),
                gasLimit,
                toAddress,
                amount,
                dataStr,
                maxPriorityFeePerGas,
                maxFeePerGas
            )
        }
    }
}
