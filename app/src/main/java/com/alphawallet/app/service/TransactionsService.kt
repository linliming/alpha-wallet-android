package com.alphawallet.app.service

import android.text.TextUtils
import android.text.format.DateUtils
import android.util.LongSparseArray
import com.alphawallet.app.BuildConfig
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.TransactionMeta
import com.alphawallet.app.entity.TransactionType
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokenscript.EventUtils
import com.alphawallet.app.entity.transactionAPI.TransferFetchType
import com.alphawallet.app.entity.transactions.TransferEvent
import com.alphawallet.app.repository.EthereumNetworkRepositoryType
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.app.repository.TransactionLocalSource
import com.alphawallet.app.util.Utils
import com.alphawallet.token.entity.ContractAddress
import io.reactivex.Single
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.web3j.exceptions.MessageDecodingException
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.EthTransaction
import org.web3j.utils.Numeric
import timber.log.Timber
import java.io.IOException
import java.math.BigInteger
import java.util.AbstractMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * TransactionsService
 *
 * Coroutine driven implementation responsible for coordinating on-chain
 * transaction and transfer polling. The original RxJava pipelines have been
 * replaced with structured concurrency while the external API keeps the same
 * behaviour (with optional Rx wrappers for legacy callers).
 */
class TransactionsService(
    private val tokensService: TokensService,
    private val ethereumNetworkRepository: EthereumNetworkRepositoryType,
    private val transactionsClient: TransactionsNetworkClientType,
    private val transactionsCache: TransactionLocalSource,
    private val transactionNotificationService: TransactionNotificationService
) {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentChainIndex: Int = 0
    private var firstCycle: Boolean = true
    private var firstTxCycle: Boolean = true
    private var fromBackground: Boolean = false

    private val chainTransferCheckTimes = LongSparseArray<Long>()
    private val chainTransactionCheckTimes = LongSparseArray<Long>()
    private val apiFetchProgress = LongSparseArray<TransferFetchType>()

    private val transferCheckMutex = Mutex()
    private val transactionCheckMutex = Mutex()
    private val pendingCheckMutex = Mutex()

    private var transferCheckJob: Job? = null
    private var transactionCheckJob: Job? = null
    private var pendingCheckJob: Job? = null
    private var eventFetch: Job? = null
    private var transactionResolve: Job? = null

    /** Entry point used by background receivers. */
    fun fetchTransactionsFromBackground() {
        fromBackground = true
        fetchTransactions()
    }

    /** Starts the polling coroutines if the wallet is available. */
    private fun fetchTransactions() {
        if (tokensService.getCurrentAddress().isNullOrEmpty()) return

        currentChainIndex = 0
        firstCycle = true
        firstTxCycle = true

        tokensService.getCurrentAddress()?.let { transactionsClient.checkRequiresAuxReset(it) }

        launchTransactionCheckCycle()
        launchTransferCheckCycle()
        launchPendingCheckCycle()
    }

    /** Launches the coroutine that polls for new transactions. */
    private fun launchTransactionCheckCycle() {
        transactionCheckJob?.cancel()
        transactionCheckJob =
            serviceScope.launch {
                delaySeconds(START_CHECK_DELAY)
                while (isActive) {
                    checkTransactionQueueSafe()
                    val nextDelay =
                        if (firstTxCycle) CHECK_CYCLE_FAST else CHECK_CYCLE
                    delaySeconds(nextDelay)
                }
            }
    }

    /** Launches the coroutine that polls token/NFT transfer APIs. */
    private fun launchTransferCheckCycle() {
        transferCheckJob?.cancel()
        transferCheckJob =
            serviceScope.launch {
                var firstDelay = START_CHECK_DELAY
                while (isActive) {
                    delaySeconds(firstDelay)
                    firstDelay = if (firstCycle) START_CHECK_DELAY else CHECK_CYCLE
                    checkTransfersSafe()
                    if (firstCycle) {
                        // after first run subsequent loops use slower cadence
                        firstCycle = false
                    }
                }
            }
    }

    /** Launches the coroutine that rechecks pending transactions. */
    private fun launchPendingCheckCycle() {
        pendingCheckJob?.cancel()
        pendingCheckJob =
            serviceScope.launch {
                delaySeconds(CHECK_CYCLE)
                while (isActive) {
                    checkPendingTransactionsSafe()
                    delaySeconds(CHECK_CYCLE)
                }
            }
    }

    /** Applies thread-safety around transfer polling. */
    private suspend fun checkTransfersSafe() {
        transferCheckMutex.withLock {
            runCatching { checkTransfersInternal() }
                .onFailure { Timber.e(it, "Failed to check transfers") }
        }
    }

    /** Applies thread-safety around new transaction polling. */
    private suspend fun checkTransactionQueueSafe() {
        transactionCheckMutex.withLock {
            runCatching { checkTransactionQueueInternal() }
                .onFailure { Timber.e(it, "Failed to check transaction queue") }
        }
    }

    /** Applies thread-safety around pending transaction reconciliation. */
    private suspend fun checkPendingTransactionsSafe() {
        pendingCheckMutex.withLock {
            runCatching { checkPendingTransactionsInternal() }
                .onFailure { Timber.w(it, "Failed to reconcile pending transactions") }
        }
    }

    /**
     * Poll transfer APIs (ERC-20/721/1155) for the active network filters.
     * This mirrors the behaviour of the legacy Rx pipeline but executes within
     * a coroutine loop.
     */
    private suspend fun checkTransfersInternal() {
        val filters = tokensService.getNetworkFilters()
        val address = tokensService.getCurrentAddress()
        if (address.isNullOrEmpty() || filters.isEmpty()) return

        val chainId = filters[currentChainIndex]
        val initiateRead = readTokenMoves(chainId, address)
        if (initiateRead) {
            currentChainIndex = getNextChainIndex(currentChainIndex, chainId, filters)
        }
    }

    /**
     * Checks the transaction queue for each enabled chain. New transactions
     * are stored in Realm and forwarded to the notification service.
     */
    private suspend fun checkTransactionQueueInternal() {
        val currentAddress = tokensService.getCurrentAddress() ?: return
        val token = getRequiresTransactionUpdate() ?: return

        val pendingChains = getPendingChains()
        if (BuildConfig.DEBUG) {
            val tick =
                if (token.isEthereum() && pendingChains.contains(token.tokenInfo.chainId)) "*" else ""
            Timber.tag(TAG)
                .d(
                    "Transaction check for: %s (%s) %s",
                    token.tokenInfo.chainId,
                    token.getNetworkName(),
                    tick,
                )
        }

        val network = ethereumNetworkRepository.getNetworkByChain(token.tokenInfo.chainId)
        val newTransactions =
            transactionsClient.storeNewTransactions(tokensService, network, token.getAddress(), token.lastBlockCheck)

        if (newTransactions.isNotEmpty()) {
            Timber.tag(TAG)
                .d("Queried for %s : %s Network transactions", token.tokenInfo.name, newTransactions.size)
            checkTokens(newTransactions)
        }

        checkFirstCycleCompletion()
    }

    /**
     * Reconcile pending transactions by re-querying the chain and updating
     * their cached status.
     */
    private suspend fun checkPendingTransactionsInternal() {
        if (transactionResolveActive()) {
            // Let the queue drain before triggering another reconciliation.
            return
        }

        checkTransactionFetchQueue()

        val currentWallet = tokensService.getCurrentAddress() ?: return
        val pendingTransactions = fetchPendingTransactions()
        Timber.tag(TAG).d("Checking %s Transactions", pendingTransactions.size)

        for (tx in pendingTransactions) {
            val fetched = runCatching { doTransactionFetch(tx.hash.orEmpty(), tx.chainId) }.getOrNull()
            val blockNumber =
                if (fetched != null) storeTransactionIfValid(fetched, currentWallet) else null

            if ((blockNumber == null || blockNumber.isEmpty()) &&
                tx.blockNumber != TRANSACTION_SEEN.toString()
            ) {
                transactionsCache.markTransactionBlock(currentWallet, tx.hash.orEmpty(), TRANSACTION_SEEN.toLong())
                triggerTokenMoveCheck(tx)
            }
        }
    }

    /** Returns true if the queued transaction fetch coroutine is active. */
    private fun transactionResolveActive(): Boolean =
        transactionResolve?.isActive == true

    /**
     * Reads token moves via the etherscan compatible API. Returns true when
     * the next chain should be visited.
     */
    private suspend fun readTokenMoves(chainId: Long, walletAddress: String): Boolean {
        val info = ethereumNetworkRepository.getNetworkByChain(chainId)
        if (info == null || info.transferQueriesUsed.isEmpty()) {
            return true
        }

        if (eventFetch != null && eventFetch!!.isActive) {
            return false
        }

        val tfType = apiFetchProgress.get(chainId, TransferFetchType.ERC_20)
        if (tfType.ordinal > 0) {
            tokensService.checkingChain(chainId)
        }

        Timber.tag(TAG).d("Check transfers: %s : NFT=%s", chainId, tfType.value)

        eventFetch =
            serviceScope.launch {
                val transferMap =
                    transactionsClient.readTransfers(walletAddress, info, tokensService, tfType)
                handleMoveCheck(chainId, tfType.ordinal > 0, transferMap)
            }.also { job ->
                job.invokeOnCompletion { eventFetch = null }
            }

        return true
    }

    private suspend fun handleMoveCheck(
        chainId: Long,
        isNFT: Boolean,
        tfMap: Map<String, List<TransferEvent>>,
    ) {
        chainTransferCheckTimes.put(chainId, System.currentTimeMillis())
        if (isNFT) {
            tokensService.checkingChain(0)
        }

        checkForIncomingTransfers(chainId, tfMap)
    }

    private suspend fun checkForIncomingTransfers(
        chainId: Long,
        tfMap: Map<String, List<TransferEvent>>,
    ) {
        val currentAddress = tokensService.getCurrentAddress() ?: return

        tfMap.entries
            .asSequence()
            .filter { it.value.isNotEmpty() }
            .map { AbstractMap.SimpleEntry(it.value.first(), it.key) }
            .filter { it.key.activityName.equals("received", ignoreCase = true) }
            .forEach { entry ->
                val tx =
                    runCatching { doTransactionFetch(entry.value, chainId) }
                        .getOrNull()
                        ?: return@forEach
                onTransactionFetched(tx, entry.key)
            }
    }

    private fun onTransactionFetched(tx: Transaction, transferEvent: TransferEvent) {
        val token = tokensService.getToken(tx.chainId, transferEvent.contractAddress)
        showTransactionNotification(tx, token, transferEvent)
    }

    private fun getNextChainIndex(
        currentIndex: Int,
        chainId: Long,
        filters: List<Long>,
    ): Int {
        val info = ethereumNetworkRepository.getNetworkByChain(chainId) ?: return 0
        val availableTypes = info.transferQueriesUsed
        val tfType = apiFetchProgress.get(chainId, TransferFetchType.ERC_20)

        if (tfType.ordinal >= availableTypes.size - 1) {
            apiFetchProgress.put(chainId, TransferFetchType.ERC_20)
            return if (currentIndex + 1 >= filters.size) 0 else currentIndex + 1
        }

        apiFetchProgress.put(chainId, TransferFetchType.values()[tfType.ordinal + 1])
        return currentIndex
    }

    private fun setNextTransferCheck(chainId: Long, isNft: Boolean) {
        val filters = tokensService.getNetworkFilters()
        if (!filters.contains(chainId)) return

        currentChainIndex = filters.indexOf(chainId)
        val queries = ethereumNetworkRepository.getNetworkByChain(chainId).transferQueriesUsed
        val nftSelection =
            if (queries.size > 1) TransferFetchType.ERC_721 else TransferFetchType.ERC_20
        apiFetchProgress.put(chainId, if (isNft) nftSelection else TransferFetchType.ERC_20)
    }

    private suspend fun getRequiresTransactionUpdate(): Token? {
        val chains = tokensService.getNetworkFilters()
        var timeIndex = 1L
        var oldestCheck = Long.MAX_VALUE
        var checkChainId = 0L

        for (chainId in chains) {
            val info = ethereumNetworkRepository.getNetworkByChain(chainId)
            if (TextUtils.isEmpty(info.etherscanAPI)) continue

            val checkTime = chainTransactionCheckTimes.get(chainId, 0L)
            if (checkTime == 0L) {
                chainTransactionCheckTimes.put(chainId, timeIndex++)
            } else if (checkTime < oldestCheck) {
                oldestCheck = checkTime
                checkChainId = chainId
            }
        }

        return if (oldestCheck != Long.MAX_VALUE &&
            System.currentTimeMillis() - oldestCheck > 45 * DateUtils.SECOND_IN_MILLIS
        ) {
            chainTransactionCheckTimes.put(checkChainId, System.currentTimeMillis())
            tokensService.getServiceToken(checkChainId)
        } else {
            null
        }
    }

    private fun checkFirstCycleCompletion() {
        if (!firstTxCycle) return

        var completed = true
        for (i in 0 until chainTransactionCheckTimes.size()) {
            if (chainTransactionCheckTimes.valueAt(i) < ethereumNetworkRepository.getAvailableNetworkList().size + 1) {
                completed = false
                break
            }
        }

        if (completed) {
            Timber.tag(TAG).d("Completed first cycle of checks")
            firstTxCycle = false
        }
    }

    private suspend fun checkTokens(transactions: Array<Transaction?>) {
        for (tx in transactions) {
            tx?.let {
                val token = tokensService.getToken(it.chainId, it.to)
                val isSuccessfulContractTx = !it.hasError() && it.hasData()
                if (isSuccessfulContractTx && token == null) {
                    tokensService.addUnknownTokenToCheckPriority(ContractAddress(it.chainId, it.to))
                } else {
                    showTransactionNotification(it, token, null)
                }
            }

        }
    }

    private fun showTransactionNotification(
        transaction: Transaction,
        token: Token?,
        transferEvent: TransferEvent?,
    ) {
        if (token == null) return

        transactionNotificationService.showNotification(transaction, token, transferEvent)
        if (fromBackground && !tokensService.isOnFocus()) {
            fromBackground = false
            stopService()
        }
    }

    fun changeWallet(newWallet: Wallet?) {
        val newAddress = newWallet?.address ?: return
        val currentAddress = tokensService.getCurrentAddress()
        if (newAddress.equals(currentAddress, ignoreCase = true)) return

        stopAllChainUpdate()
        fetchTransactions()
    }

    fun restartService() {
        stopAllChainUpdate()
        tokensService.restartUpdateCycle()
        fetchTransactions()
    }

    fun stopService() {
        tokensService.stopUpdateCycle()
        stopAllChainUpdate()
        tokensService.walletOutOfFocus()
    }

    fun lostFocus() {
        tokensService.walletOutOfFocus()
    }

    fun resumeFocus() {
        tokensService.walletInFocus()
        if (!Utils.isAddressValid(tokensService.getCurrentAddress())) return

        if (transactionCheckJob?.isActive != true ||
            transferCheckJob?.isActive != true ||
            pendingCheckJob?.isActive != true
        ) {
            fetchTransactions()
        }
    }

    fun startUpdateCycle() {
        chainTransferCheckTimes.clear()
        chainTransactionCheckTimes.clear()
        apiFetchProgress.clear()
        tokensService.startUpdateCycle()
        fetchTransactions()
    }

    fun stopActivity() {
        tokensService.stopUpdateCycle()
        stopAllChainUpdate()
    }

    fun markPending(tx: Transaction) {
        Timber.tag(TAG).d("Marked Pending Tx Chain: %s", tx.chainId)
        tokensService.markChainPending(tx.chainId)
    }

    fun fetchAndStoreTransactions(chainId: Long, lastTxTime: Long): Single<Array<TransactionMeta>> =
        singleFrom { fetchAndStoreTransactionsSuspend(chainId, lastTxTime) }

    private suspend fun fetchAndStoreTransactionsSuspend(
        chainId: Long,
        lastTxTime: Long,
    ): Array<TransactionMeta> {
        val network = ethereumNetworkRepository.getNetworkByChain(chainId)
        return transactionsClient
            .fetchMoreTransactions(tokensService, network, lastTxTime)
            .filterNotNull()
            .toTypedArray()
    }

    fun fetchTransaction(currentAddress: String, chainId: Long, hash: String): Single<Transaction> =
        singleFrom { fetchTransactionSuspend(currentAddress, chainId, hash) }

    suspend fun fetchTransactionSuspend(
        currentAddress: String,
        chainId: Long,
        hash: String,
    ): Transaction {
        val fetched = doTransactionFetch(hash, chainId)
        transactionsCache.putTransaction(Wallet(currentAddress), fetched)
        return fetched
    }

    fun wipeDataForWallet(): Single<Boolean> = singleFrom { wipeDataForWalletSuspend() }

    suspend fun wipeDataForWalletSuspend(): Boolean {
        val currentAddress = tokensService.getCurrentAddress() ?: return false
        tokensService.stopUpdateCycle()
        stopAllChainUpdate()
        return transactionsCache.deleteAllForWallet(currentAddress)
    }

    fun wipeTickerData(): Single<Boolean> = singleFrom {
        transactionsCache.deleteAllTickers()
    }

    private fun stopAllChainUpdate() {
        transactionCheckJob?.cancel()
        transferCheckJob?.cancel()
        pendingCheckJob?.cancel()
        eventFetch?.cancel()
        transactionResolve?.cancel()

        transactionCheckJob = null
        transferCheckJob = null
        pendingCheckJob = null
        eventFetch = null
        transactionResolve = null

        serviceScope.coroutineContext.cancelChildren()

        tokensService.checkingChain(0)
        chainTransferCheckTimes.clear()
        chainTransactionCheckTimes.clear()
        currentChainIndex = 0
    }

    private suspend fun checkTransactionFetchQueue() {
        val txHashData = getNextUncachedTx() ?: run {
            transactionResolve = null
            return
        }

        val txData = txHashData.split("-")
        if (txData.size != 3) return

        val txHash = txData[0]
        val chainId = txData[1].toLongOrNull() ?: return
        val wallet = txData[2].lowercase()

        transactionResolve =
            serviceScope.launch {
                val fetched = doTransactionFetch(txHash, chainId)
                storeTransactionIfValid(fetched, wallet)
                delaySeconds(1)
                checkTransactionFetchQueue()
            }.also { job ->
                job.invokeOnCompletion { transactionResolve = null }
            }
    }


    private suspend fun doTransactionFetch(txHash: String, chainId: Long): Transaction {
        val web3j = TokenRepository.getWeb3jService(chainId)
        val txData = EventUtils.getTransactionDetails(txHash, web3j).await()
        val blockPair = getBlockNumber(txData)
        val joined = joinBlockTimestamp(blockPair, web3j)
        return formTransaction(joined, chainId)
    }

    private fun storeTransactionIfValid(transaction: Transaction, wallet: String): String? {
        if (!transaction.blockNumber.isNullOrEmpty()) {
            transactionsCache.putTransaction(Wallet(wallet), transaction)
        }
        return transaction.blockNumber
    }

    private fun getBlockNumber(etx: EthTransaction): Pair<EthTransaction, BigInteger> {
        val fetchedTx = etx.result
        val blockNumber =
            try {
                fetchedTx.blockNumber
            } catch (e: MessageDecodingException) {
                BigInteger.valueOf(-1)
            }
        return Pair(etx, blockNumber)
    }

    private suspend fun joinBlockTimestamp(
        txData: Pair<EthTransaction, BigInteger>,
        web3j: Web3j,
    ): Pair<EthTransaction, Long> {
        return if (txData.second.compareTo(BigInteger.ZERO) > 0) {
            val blockReceipt =
                EventUtils.getBlockDetails(txData.first.result.blockHash, web3j).await()
            Pair(txData.first, blockReceipt.block.timestamp.toLong())
        } else {
            Pair(txData.first, 0L)
        }
    }

    @Throws(IOException::class)
    private suspend fun formTransaction(
        txData: Pair<EthTransaction, Long>,
        chainId: Long,
    ): Transaction {
        return if (txData.second > 0L) {
                Transaction(
                    txData.first.result,
                    chainId,
                    checkTransactionReceipt(txData.first.result.hash, chainId),
                    txData.second,
                )
        } else {
            Transaction()
        }
    }

    private suspend fun checkTransactionReceipt(txHash: String, chainId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val web3j = TokenRepository.getWeb3jService(chainId)
            web3j.ethGetTransactionReceipt(txHash).send().result.isStatusOK
        }

    private fun triggerTokenMoveCheck(transaction: Transaction) {
        if (transaction.timeStamp == 0L) return
        val currentWallet = tokensService.getCurrentAddress()
        val token = tokensService.getToken(transaction.chainId, transaction.to)
        if (token != null && transaction.hasInput() && (token.isERC20() || token.isERC721())) {
            val type = transaction.getTransactionType(currentWallet.orEmpty())
            when (type) {
                TransactionType.TRANSFER_TO,
                TransactionType.RECEIVE_FROM,
                TransactionType.TRANSFER_FROM,
                TransactionType.RECEIVED,
                TransactionType.SEND,
                -> {
                    Timber.tag(TAG).d("Trigger check for %s", token.getFullName())
                    setNextTransferCheck(transaction.chainId, token.isNonFungible())
                }
                else -> Unit
            }
        }
    }

    private fun getPendingChains(): List<Long> {
        val pendingChains = mutableListOf<Long>()
        val pendingTransactions = fetchPendingTransactions()
        for (tx in pendingTransactions) {
            if (!pendingChains.contains(tx.chainId)) {
                pendingChains.add(tx.chainId)
            }
        }
        return pendingChains
    }

    private fun fetchPendingTransactions(): Array<Transaction> {
        val currentAddress = tokensService.getCurrentAddress()
        return if (!currentAddress.isNullOrEmpty()) {
            transactionsCache.fetchPendingTransactions(currentAddress)
        } else {
            emptyArray()
        }
    }

    private fun getNextUncachedTx(): String? {
        var txHashData = requiredTransactions.poll()
        while (txHashData != null) {
            val txData = txHashData.split("-")
            if (txData.size != 3) {
                txHashData = requiredTransactions.poll()
                continue
            }

            val wallet = txData[2].lowercase()
            val cached = transactionsCache.fetchTransaction(Wallet(wallet), txData[0])
            if (cached == null) {
                break
            } else {
                txHashData = requiredTransactions.poll()
            }
        }
        return txHashData
    }

    fun shutdown() {
        stopAllChainUpdate()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "TRANSACTION"
        private const val TRANSACTION_SEEN = -2
        private const val START_CHECK_DELAY = 3L
        private const val CHECK_CYCLE = 15L
        private const val CHECK_CYCLE_FAST = CHECK_CYCLE / 3

        private val currentBlocks = LongSparseArray<CurrentBlockTime>()
        private val requiredTransactions = ConcurrentLinkedQueue<String>()

        @JvmStatic
        fun addTransactionHashFetch(txHash: String, chainId: Long, wallet: String) {
            val hashDef = "$txHash-$chainId-$wallet"
            if (!requiredTransactions.contains(hashDef)) {
                requiredTransactions.add(hashDef)
            }
        }

        @JvmStatic
        fun getCurrentBlock(chainId: Long): BigInteger {
            var currentBlock = currentBlocks.get(chainId, CurrentBlockTime(BigInteger.ZERO))
            if (currentBlock.blockReadRequiresUpdate()) {
                currentBlock = CurrentBlockTime(fetchCurrentBlock(chainId).blockingGet())
                currentBlocks.put(chainId, currentBlock)
            }
            return currentBlock.blockNumber
        }

        private fun fetchCurrentBlock(chainId: Long): Single<BigInteger> =
            Single.fromCallable {
                val web3j = TokenRepository.getWeb3jService(chainId)
                val ethBlock =
                    web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send()
                val blockValStr = ethBlock.block.numberRaw
                if (!blockValStr.isNullOrEmpty() && blockValStr.length > 2) {
                    Numeric.toBigInt(blockValStr)
                } else {
                    currentBlocks.get(chainId, CurrentBlockTime(BigInteger.ZERO)).blockNumber
                }
            }
    }

    private class CurrentBlockTime(blockNo: BigInteger) {
        val readTime: Long = System.currentTimeMillis()
        val blockNumber: BigInteger = blockNo

        fun blockReadRequiresUpdate(): Boolean =
            blockNumber == BigInteger.ZERO ||
                System.currentTimeMillis() > readTime + 10 * DateUtils.SECOND_IN_MILLIS
    }

    private fun serviceScope() = serviceScope

    private suspend fun delaySeconds(seconds: Long) {
        if (seconds <= 0) return
        delay(seconds * 1000L)
    }

    private fun <T : Any> singleFrom(block: suspend () -> T): Single<T> =
        Single.create { emitter ->
            val job =
                serviceScope.launch {
                    try {
                        emitter.onSuccess(block())
                    } catch (cancellation: CancellationException) {
                        emitter.tryOnError(cancellation)
                    } catch (throwable: Throwable) {
                        emitter.tryOnError(throwable)
                    }
                }

            emitter.setCancellable { job.cancel() }
        }

    private suspend fun <T> Single<T>.await(): T =
        suspendCancellableCoroutine { continuation ->
            val disposable =
                subscribe(
                    { value -> continuation.resume(value) },
                    { error -> continuation.resumeWithException(error) },
                )
            continuation.invokeOnCancellation { disposable.dispose() }
        }
}
