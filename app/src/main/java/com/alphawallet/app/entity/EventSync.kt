package com.alphawallet.app.entity

import android.util.Pair
import androidx.core.util.component1
import androidx.core.util.component2
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.app.repository.entity.RealmAuxData
import com.alphawallet.app.repository.entity.RealmTransfer
import com.alphawallet.app.service.TransactionsService
import io.realm.Realm
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.BatchResponse
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.EthLog
import org.web3j.utils.Numeric
import timber.log.Timber
import java.io.IOException
import java.math.BigInteger
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates event log synchronisation for a specific token, tracking downward and upward scans to
 * gradually fetch transfer history while coping with RPC limits.
 */
class EventSync(private val token: Token) {

    companion object {
        const val BLOCK_SEARCH_INTERVAL = 100000L
        const val POLYGON_BLOCK_SEARCH_INTERVAL = 3000L
        const val OKX_BLOCK_SEARCH_INTERVAL = 2000L

        private const val TAG = "EVENT_SYNC"
        private const val EVENT_SYNC_DEBUGGING = false

        private val batchProcessingError = ConcurrentHashMap.newKeySet<Long>()
    }

    /**
     * Determines the next sync range based on the current block height and the persisted state.
     */
    fun getSyncDef(realm: Realm): SyncDef? {
        val currentBlock = TransactionsService.getCurrentBlock(token.tokenInfo.chainId)
        if (currentBlock == BigInteger.ZERO) return null

        var syncState = getCurrentTokenSyncState(realm)
        val lastBlockRead = BigInteger.valueOf(getLastEventRead(realm))
        val readBlockSize = getCurrentEventBlockSize(realm)
        var eventReadStartBlock: BigInteger
        var eventReadEndBlock: BigInteger
        var upwardSync = false

        when (syncState) {
            EventSyncState.DOWNWARD_SYNC_START -> {
                eventReadStartBlock = BigInteger.ONE
                eventReadEndBlock = BigInteger.valueOf(-1L)
                if (EthereumNetworkBase.isEventBlockLimitEnforced(token.tokenInfo.chainId)) {
                    syncState = EventSyncState.UPWARD_SYNC
                    eventReadStartBlock = currentBlock.subtract(
                        EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId)
                            .multiply(BigInteger.valueOf(3)),
                    )
                    eventDebug("Init Sync for restricted block RPC")
                } else {
                    writeStartSyncBlock(realm, currentBlock.toLong())
                }
            }

            EventSyncState.DOWNWARD_SYNC -> {
                eventReadStartBlock = lastBlockRead.subtract(BigInteger.valueOf(readBlockSize))
                eventReadEndBlock = lastBlockRead
                if (eventReadStartBlock <= BigInteger.ZERO) {
                    eventReadStartBlock = BigInteger.ONE
                    syncState = EventSyncState.DOWNWARD_SYNC_COMPLETE
                }
            }

            EventSyncState.UPWARD_SYNC_MAX -> {
                upwardSync = true
                if (
                    EthereumNetworkBase.isEventBlockLimitEnforced(token.tokenInfo.chainId) &&
                    upwardSyncStateLost(lastBlockRead, currentBlock)
                ) {
                    syncState = EventSyncState.UPWARD_SYNC
                    eventDebug("Switch back to sync scan")
                }
                eventReadStartBlock = lastBlockRead
                eventReadEndBlock = BigInteger.valueOf(-1L)
            }

            EventSyncState.UPWARD_SYNC -> {
                upwardSync = true
                eventReadStartBlock = lastBlockRead
                if (upwardSyncComplete(eventReadStartBlock, currentBlock)) {
                    eventReadEndBlock = BigInteger.valueOf(-1L)
                    syncState = EventSyncState.UPWARD_SYNC_MAX
                    eventDebug("Sync complete")
                } else {
                    eventReadEndBlock = lastBlockRead.add(BigInteger.valueOf(readBlockSize))
                }
            }

            else -> {
                eventReadStartBlock = BigInteger.ONE
                eventReadEndBlock = BigInteger.valueOf(-1L)
            }
        }

        eventReadEndBlock = adjustForLimitedBlockSize(eventReadStartBlock, eventReadEndBlock, currentBlock)

        if (eventReadStartBlock >= currentBlock) {
            eventReadStartBlock = currentBlock.subtract(BigInteger.ONE)
            eventReadEndBlock = BigInteger.valueOf(-1L)
            syncState = EventSyncState.UPWARD_SYNC_MAX
        }

        return SyncDef(eventReadStartBlock, eventReadEndBlock, syncState, upwardSync)
    }

    /**
     * Determines if the upward sync lost track (eg. due to block limit resets).
     */
    private fun upwardSyncStateLost(lastBlockRead: BigInteger, currentBlock: BigInteger): Boolean {
        return currentBlock.subtract(lastBlockRead)
            .compareTo(EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId)) >= 0
    }

    /**
     * Checks whether the current upward sync window now encompasses the tip.
     */
    private fun upwardSyncComplete(eventReadStartBlock: BigInteger, currentBlock: BigInteger): Boolean {
        val maxBlockRead = EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).subtract(BigInteger.ONE)
        val diff = currentBlock.subtract(eventReadStartBlock)
        return diff < maxBlockRead
    }

    /**
     * Adjusts the sync window when the RPC provider enforces an event block limit.
     */
    private fun adjustForLimitedBlockSize(
        eventReadStartBlock: BigInteger,
        eventReadEndBlock: BigInteger,
        currentBlock: BigInteger,
    ): BigInteger {
        if (EthereumNetworkBase.isEventBlockLimitEnforced(token.tokenInfo.chainId)) {
            val maxBlockRead = EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId)
            val diff = currentBlock.subtract(eventReadStartBlock).toLong()
            if (diff >= maxBlockRead.toLong()) {
                return eventReadStartBlock.add(maxBlockRead).subtract(BigInteger.ONE)
            }
        }
        return eventReadEndBlock
    }

    /**
     * Handles log-range errors by shrinking the query window and updating the sync state.
     */
    fun handleEthLogError(
        error: Response.Error,
        startBlock: DefaultBlockParameter,
        endBlock: DefaultBlockParameter,
        sync: SyncDef,
        realm: Realm,
    ): Boolean {
        return if (error.message.lowercase(Locale.ROOT).contains("block")) {
            val startBlockVal = Numeric.toBigInt(startBlock.value)
            val state: EventSyncState
            val newStartBlock: Long
            val newEndBlock: Long
            val blockSize: Long

            if (sync.upwardSync) {
                if (endBlock.value.equals("latest", ignoreCase = true)) {
                    newStartBlock = startBlockVal.toLong()
                    newEndBlock = newStartBlock + EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).toLong()
                    blockSize = EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).toLong()
                } else {
                    val endBlockVal = Numeric.toBigInt(endBlock.value)
                    val currentBlockScan = endBlockVal.subtract(startBlockVal).toLong()
                    newStartBlock = startBlockVal.toLong()
                    newEndBlock = newStartBlock + currentBlockScan / 2
                    blockSize = newEndBlock - newStartBlock
                }
                state = EventSyncState.UPWARD_SYNC
            } else {
                if (endBlock.value.equals("latest", ignoreCase = true)) {
                    val currentBlock = TransactionsService.getCurrentBlock(token.tokenInfo.chainId)
                    blockSize = EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).toLong()
                    newEndBlock = currentBlock.toLong()
                    newStartBlock = newEndBlock - blockSize
                } else {
                    newEndBlock = Numeric.toBigInt(endBlock.value).toLong()
                    newStartBlock = reduceBlockSearch(newEndBlock, startBlockVal)
                    blockSize = newEndBlock - newStartBlock
                }
                state = EventSyncState.DOWNWARD_SYNC
            }

            updateEventReads(realm, newEndBlock, blockSize, state)
            true
        } else {
            Timber.w("Event fetch error: %s", error.message)
            false
        }
    }

    /**
     * Reduces the search interval when a block-range error is encountered.
     */
    private fun reduceBlockSearch(currentBlock: Long, startBlock: BigInteger): Long {
        return if (startBlock == BigInteger.ONE) {
            currentBlock - EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).toLong()
        } else {
            currentBlock - (currentBlock - startBlock.toLong()) / 2
        }
    }

    /**
     * Retrieves the currently configured block interval for event reads.
     */
    private fun getCurrentEventBlockSize(realm: Realm): Long {
        val key = TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress())
        val rd = realm.where(RealmAuxData::class.java)
            .equalTo("instanceKey", key)
            .findFirst()
        return if (rd == null) {
            EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).toLong()
        } else {
            rd.resultReceivedTime.coerceAtMost(
                EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).toLong(),
            )
        }
    }

    /**
     * Reads and validates the persisted sync state, defaulting if missing.
     */
    internal fun getCurrentTokenSyncState(realm: Realm): EventSyncState {
        val key = TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress())
        val rd = realm.where(RealmAuxData::class.java)
            .equalTo("instanceKey", key)
            .findFirst()

        return if (rd == null) {
            writeCurrentTokenSyncState(realm, EventSyncState.DOWNWARD_SYNC_START)
            EventSyncState.DOWNWARD_SYNC_START
        } else {
            val state = rd.tokenId.orEmpty().toInt()
            if (state >= EventSyncState.DOWNWARD_SYNC_START.ordinal && state < EventSyncState.TOP_LIMIT.ordinal) {
                eventDebug("Read State: ${EventSyncState.values()[state]}")
                EventSyncState.values()[state]
            } else {
                writeCurrentTokenSyncState(realm, EventSyncState.DOWNWARD_SYNC_START)
                EventSyncState.DOWNWARD_SYNC_START
            }
        }
    }

    /**
     * Persists the new sync state to Realm.
     */
    fun writeCurrentTokenSyncState(realm: Realm?, newState: EventSyncState) {
        realm ?: return
        val key = TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress())
        realm.executeTransaction { r ->
            var rd = r.where(RealmAuxData::class.java)
                .equalTo("instanceKey", key)
                .findFirst()
            if (rd == null) {
                rd = r.createObject(RealmAuxData::class.java, key)
            }
            rd!!.tokenId = newState.ordinal.toString()
            r.insertOrUpdate(rd)
        }
    }

    /**
     * Returns the most recent block index read during sync.
     */
    protected fun getLastEventRead(realm: Realm): Long {
        val key = TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress())
        val rd = realm.where(RealmAuxData::class.java)
            .equalTo("instanceKey", key)
            .findFirst()
        return if (rd == null) {
            -1L
        } else {
            eventDebug("ReadEventSync: ${rd.resultTime}")
            rd.resultTime
        }
    }

    /**
     * Returns the starting block used for the original sync attempt.
     */
    private fun getSyncStart(realm: Realm): Long {
        val key = TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress())
        val rd = realm.where(RealmAuxData::class.java)
            .equalTo("instanceKey", key)
            .findFirst()
        return rd?.functionId?.toLong() ?: TransactionsService.getCurrentBlock(token.tokenInfo.chainId).toLong()
    }

    /**
     * Stores the block at which the sync was initiated.
     */
    protected fun writeStartSyncBlock(realm: Realm?, currentBlock: Long) {
        realm ?: return
        val key = TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress())
        realm.executeTransaction { r ->
            var rd = r.where(RealmAuxData::class.java)
                .equalTo("instanceKey", key)
                .findFirst()
            if (rd == null) {
                rd = r.createObject(RealmAuxData::class.java, key)
            }
            rd!!.functionId = currentBlock.toString()
            r.insertOrUpdate(rd)
        }
    }

    /**
     * Updates the sync window and state following a successful read.
     */
    fun updateEventReads(realm: Realm, sync: SyncDef, currentBlock: BigInteger, evReads: Int) {
        val adjustedSync = when (sync.state) {
            EventSyncState.UPWARD_SYNC_MAX,
            EventSyncState.DOWNWARD_SYNC_START -> SyncDef(
                BigInteger.ONE,
                currentBlock,
                EventSyncState.UPWARD_SYNC_MAX,
                true,
            )

            EventSyncState.DOWNWARD_SYNC_COMPLETE -> {
                updateEventReads(
                    realm,
                    getSyncStart(realm),
                    EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).toLong(),
                    EventSyncState.UPWARD_SYNC_MAX,
                )
                return
            }

            EventSyncState.DOWNWARD_SYNC,
            EventSyncState.UPWARD_SYNC,
            EventSyncState.TOP_LIMIT -> sync
        }

        updateEventReads(
            realm,
            adjustedSync.eventReadEndBlock.toLong(),
            calcNewIntervalSize(adjustedSync, evReads),
            adjustedSync.state,
        )
    }

    /**
     * Resets the stored sync progress forcing a fresh sweep.
     */
    fun resetEventReads(realm: Realm) {
        updateEventReads(realm, 0, 0, EventSyncState.DOWNWARD_SYNC_START)
    }

    /**
     * Persists the latest read interval and state to Realm.
     */
    private fun updateEventReads(realm: Realm?, lastRead: Long, readInterval: Long, state: EventSyncState) {
        realm ?: return
        val key = TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress())
        realm.executeTransaction { r ->
            var rd = r.where(RealmAuxData::class.java)
                .equalTo("instanceKey", key)
                .findFirst()
            if (rd == null) {
                rd = r.createObject(RealmAuxData::class.java, key)
            }
            rd!!.resultTime = lastRead
            rd.resultReceivedTime = readInterval
            rd.tokenId = state.ordinal.toString()
            eventDebug("WriteState: $state $lastRead")
            r.insertOrUpdate(rd)
        }
    }

    /**
     * Calculates the next interval size based on the volume of events found.
     */
    private fun calcNewIntervalSize(sync: SyncDef, evReads: Int): Long {
        if (sync.upwardSync) {
            return EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).toLong()
        }

        val endBlock = if (sync.eventReadEndBlock.toLong() == -1L) {
            TransactionsService.getCurrentBlock(token.tokenInfo.chainId).toLong()
        } else {
            sync.eventReadEndBlock.toLong()
        }

        var currentReadSize = endBlock - sync.eventReadStartBlock.toLong()
        val maxLogReads = EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).toLong()

        currentReadSize = when {
            evReads == 0 -> currentReadSize * 4
            evReads < 1000 -> currentReadSize * 2
            (maxLogReads - evReads.toLong()) > (maxLogReads * 0.25).toLong() ->
                currentReadSize + EthereumNetworkBase.getMaxEventFetch(token.tokenInfo.chainId).toLong()

            else -> currentReadSize
        }

        return currentReadSize
    }

    /**
     * Fetches and processes transfer logs for the specified block window.
     */
    @Throws(IOException::class, LogOverflowException::class)
    fun processTransferEvents(
        web3j: Web3j,
        transferEvent: Event,
        startBlock: DefaultBlockParameter,
        endBlock: DefaultBlockParameter,
        realm: Realm,
    ): Pair<Int, Pair<HashSet<BigInteger>, HashSet<BigInteger>>> {
        val txHashes = HashSet<String>()
        val receiveFilter = token.getReceiveBalanceFilter(transferEvent, startBlock, endBlock)
        val sendFilter = token.getSendBalanceFilter(transferEvent, startBlock, endBlock)

        val (receiveLogs, sendLogs) = getTxLogs(web3j, receiveFilter, sendFilter)

        var eventCount = receiveLogs.logs.size
        val rcvTokenIds = HashSet(
            token.processLogsAndStoreTransferEvents(receiveLogs, transferEvent, txHashes, realm),
        )

        if (sendLogs.logs.size > eventCount) {
            eventCount = sendLogs.logs.size
        }

        val sendTokenIds = token.processLogsAndStoreTransferEvents(sendLogs, transferEvent, txHashes, realm)

        txHashes.forEach { hash ->
            TransactionsService.addTransactionHashFetch(hash, token.tokenInfo.chainId, token.getWallet())
        }

        return Pair(eventCount, Pair(rcvTokenIds, sendTokenIds))
    }

    /**
     * Retrieves logs using batching when available, falling back to sequential requests on failure.
     */
    @Throws(IOException::class, LogOverflowException::class)
    private fun getTxLogs(
        web3j: Web3j,
        receiveFilter: EthFilter?,
        sendFilter: EthFilter?,
    ): Pair<EthLog, EthLog> {
        return if (EthereumNetworkBase.getBatchProcessingLimit(token.tokenInfo.chainId) > 0 &&
            !batchProcessingError.contains(token.tokenInfo.chainId)
        ) {
            getBatchTxLogs(web3j, receiveFilter, sendFilter)
        } else {
            val receiveLogs = web3j.ethGetLogs(receiveFilter).send()
            if (receiveLogs.hasError()) {
                throw LogOverflowException(receiveLogs.error)
            }
            val sentLogs = web3j.ethGetLogs(sendFilter).send()
            if (sentLogs.hasError()) {
                throw LogOverflowException(sentLogs.error)
            }
            Pair(receiveLogs, sentLogs)
        }
    }

    /**
     * Executes a batched log request, recording failures so we can fall back to single calls.
     */
    @Throws(IOException::class, LogOverflowException::class)
    private fun getBatchTxLogs(
        web3j: Web3j,
        receiveFilter: EthFilter?,
        sendFilter: EthFilter?,
    ): Pair<EthLog, EthLog> {
        val rsp: BatchResponse?
        rsp = try {
            web3j.newBatch()
                .add(web3j.ethGetLogs(receiveFilter))
                .add(web3j.ethGetLogs(sendFilter))
                .send()
        } catch (e: ClassCastException) {
            null
        }

        if (rsp == null || rsp.responses.size != 2) {
            batchProcessingError.add(token.tokenInfo.chainId)
            return getTxLogs(web3j, receiveFilter, sendFilter)
        }

        val receiveLogs = rsp.responses[0] as EthLog
        val sendLogs = rsp.responses[1] as EthLog

        if (receiveLogs.hasError()) {
            throw LogOverflowException(receiveLogs.error)
        } else if (sendLogs.hasError()) {
            throw LogOverflowException(sendLogs.error)
        }

        return Pair(receiveLogs, sendLogs)
    }

    /**
     * Converts a transfer direction and address into an activity label.
     */
    fun getActivityName(toAddress: String): String {
        return if (toAddress.equals(token.getWallet(), ignoreCase = true)) {
            "received"
        } else {
            "sent"
        }
    }

    /**
     * Builds a value string describing token IDs and counts for storage.
     */
    fun getIds(ids: Pair<List<BigInteger>, List<BigInteger>>): String {
        val firstBuilder = StringBuilder()
        val secondBuilder = StringBuilder()
        var firstVal = true
        if (ids.first.size != ids.second.size) return ""

        for (i in ids.first.indices) {
            if (!firstVal) {
                firstBuilder.append('-')
                secondBuilder.append('-')
            }
            firstBuilder.append(ids.first[i])
            secondBuilder.append(ids.second[i])
            firstVal = false
        }

        return if (ids.first.size == 1 && ids.second[0] == BigInteger.ONE) {
            firstBuilder.toString()
        } else {
            "${firstBuilder},value,uint256,$secondBuilder"
        }
    }

    /**
     * Extracts token IDs and amounts from decoded log parameters.
     */
    fun getEventIdResult(ids: Type<*>, values: Type<*>?): Pair<List<BigInteger>, List<BigInteger>> {
        val idList = ArrayList<BigInteger>()
        val countList = ArrayList<BigInteger>()

        if (values == null) {
            idList.add((ids as Uint256).value)
            countList.add(BigInteger.ONE)
        } else if (ids is Uint256) {
            idList.add(ids.value as BigInteger)
            countList.add(values.value as BigInteger)
        } else {
            (ids.value as? ArrayList<*>)?.forEach { valItem ->
                if (valItem is Uint256) {
                    idList.add(valItem.value)
                }
            }
            (values.value as? ArrayList<*>)?.forEach { valItem ->
                if (valItem is Uint256) {
                    countList.add(valItem.value)
                }
            }
        }

        return Pair(idList, countList)
    }

    /**
     * Generates a CSV structure used by the activity view to display transfer metadata.
     */
    private fun generateValueListForTransferEvent(to: String, from: String, tokenID: String): String {
        val valuesTemplate = "from,address,[FROM_ADDRESS],to,address,[TO_ADDRESS],amount,uint256,[AMOUNT_TOKEN]"
        return valuesTemplate
            .replace("[TO_ADDRESS]", to)
            .replace("[FROM_ADDRESS]", from)
            .replace("[AMOUNT_TOKEN]", tokenID)
    }

    /**
     * Stores processed transfer data for later display in the activity feed.
     */
    fun storeTransferData(
        realm: Realm,
        from: String,
        to: String,
        idResult: Pair<List<BigInteger>, List<BigInteger>>,
        txHash: String,
    ) {
        val activityName = getActivityName(to)
        val value = getIds(idResult)
        val valueList = generateValueListForTransferEvent(to, from, value)
        realm.executeTransaction { r ->
            storeTransferDataInternal(r, txHash, valueList, activityName)
        }
    }

    /**
     * Writes transfer details into Realm, deduplicating existing entries.
     */
    private fun storeTransferDataInternal(realm: Realm, hash: String, valueList: String, activityName: String) {
        if (activityName == "receive") {
            realm.where(RealmTransfer::class.java)
                .like("hash", RealmTransfer.databaseKey(token.tokenInfo.chainId, hash))
                .findAll()
                .deleteAllFromRealm()
        }

        var matchingEntry = realm.where(RealmTransfer::class.java)
            .equalTo("hash", RealmTransfer.databaseKey(token.tokenInfo.chainId, hash))
            .equalTo("tokenAddress", token.tokenInfo.address)
            .equalTo("eventName", activityName)
            .equalTo("transferDetail", valueList)
            .findFirst()

        if (matchingEntry == null) {
            matchingEntry = realm.createObject(RealmTransfer::class.java)
            matchingEntry.setHashKey(token.tokenInfo.chainId, hash)
            matchingEntry.setTokenAddress(token.tokenInfo.address)
        }

        matchingEntry?.setEventName(activityName)
        matchingEntry?.setTransferDetail(valueList)
        realm.insertOrUpdate(matchingEntry)
    }
    
    /**
     * Debug logger that respects the static toggle above.
     */
    private fun eventDebug(message: String) {
        if (EVENT_SYNC_DEBUGGING) {
            Timber.tag(TAG).i("${token.tokenInfo.chainId} ${token.tokenInfo.address}: $message")
        }
    }
}
