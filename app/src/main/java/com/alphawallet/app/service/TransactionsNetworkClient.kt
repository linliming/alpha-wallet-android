package com.alphawallet.app.service

import android.text.TextUtils
import android.util.Pair
import com.alphawallet.app.BuildConfig
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.CovalentTransaction
import com.alphawallet.app.entity.CovalentTransaction.Companion.toEtherscanEvents
import com.alphawallet.app.entity.CovalentTransaction.Companion.toRawEtherscanTransactions
import com.alphawallet.app.entity.EtherscanEvent
import com.alphawallet.app.entity.EtherscanTransaction
import com.alphawallet.app.entity.NetworkInfo
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.TransactionMeta
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.ERC1155Token
import com.alphawallet.app.entity.tokens.ERC721Token
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenInfo
import com.alphawallet.app.entity.transactionAPI.TransferFetchType
import com.alphawallet.app.entity.transactions.TransferEvent
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.KeyProviderFactory.get
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.app.repository.TransactionsRealmCache
import com.alphawallet.app.repository.entity.RealmAuxData
import com.alphawallet.app.repository.entity.RealmToken
import com.alphawallet.app.repository.entity.RealmTransaction
import com.alphawallet.app.repository.entity.RealmTransfer
import com.alphawallet.app.service.TransactionsService.Companion.addTransactionHashFetch
import com.alphawallet.app.service.TransactionsService.Companion.getCurrentBlock
import com.alphawallet.app.util.Utils
import com.alphawallet.token.entity.ContractAddress
import com.google.gson.Gson
import io.realm.Case
import io.realm.Realm
import io.realm.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Arrays
import java.util.Locale

class TransactionsNetworkClient(
    private val httpClient: OkHttpClient,
    private val gson: Gson,
    private val realmManager: RealmManager
) : TransactionsNetworkClientType {
    private val PAGESIZE = 800
    private val SYNC_PAGECOUNT =
        2 //how many pages to read when we first sync the account - means we store the first 1600 transactions only
    private val TRANSFER_RESULT_MAX = 500

    //Note: if user wants to view transactions older than this, we fetch from etherscan on demand.
    //Generally this would only happen when watching extremely active accounts for curiosity
    private val BLOCK_ENTRY = "-erc20blockCheck-"
    private val AUX_DATABASE_ID =
        25 //increment this to do a one off refresh the AUX database, in case of changed design etc

    private val TRANSACTION_FETCH_LIMIT =
        20 //Limit on the number of transactions fetched when we receive transfer updates

    //Note that if the tx isn't fetched here, it is fetched automatically if the user scrolls down their activity list
    //This speeds up the first time account sync - potentially there may be hundreds of new transactions here
    private val DB_RESET = BLOCK_ENTRY + AUX_DATABASE_ID
    private val ETHERSCAN_API_KEY: String
    private val BSC_EXPLORER_API_KEY: String
    private val POLYGONSCAN_API_KEY: String
    private val AURORASCAN_API_KEY: String
    private val keyProvider = get()
    private val JSON_EMPTY_RESULT = "{\"result\":[]}"

    init {
        BSC_EXPLORER_API_KEY =
            if (keyProvider.getBSCExplorerKey().length > 0) "&apikey=" + keyProvider.getBSCExplorerKey() else ""
        ETHERSCAN_API_KEY = "&apikey=" + keyProvider.getEtherscanKey()
        POLYGONSCAN_API_KEY =
            if (keyProvider.getPolygonScanKey().length > 3) "&apikey=" + keyProvider.getPolygonScanKey() else ""
        AURORASCAN_API_KEY =
            if (keyProvider.getAuroraScanKey().length > 3) "&apikey=" + keyProvider.getAuroraScanKey() else ""
    }

    override fun checkRequiresAuxReset(walletAddr: String?) {
        //See if we require a refresh of transaction checks
        try {
            realmManager.getRealmInstance(Wallet(walletAddr)).use { instance ->
                instance.executeTransactionAsync { r: Realm ->
                    val checkMarker = r.where(RealmAuxData::class.java)
                        .like("instanceKey", "$BLOCK_ENTRY*")
                        .findFirst()
                    if (checkMarker?.getResult() != null && (checkMarker.getResult() != DB_RESET)) {
                        val realmEvents = r.where(RealmAuxData::class.java)
                            .findAll()
                        realmEvents.deleteAllFromRealm()
                        val realmTransfers = r.where(
                            RealmTransfer::class.java
                        )
                            .findAll()
                        realmTransfers.deleteAllFromRealm()
                    }
                }
            }
        } catch (e: Exception) {
            //
        }
    }

    /*
     *
     * Transaction sync strategy:
     *
     * 1. Always sync downward from latest block to the last scan to get newest transactions (latest transactions are the most important)
     *
     * 2. Check the count of synced transactions; sync up to 800
     * If we don't reach the previous fetch then execute a second fetch.
     *
     * On user scrolling down transaction list: if user is nearing bottom transaction limit begin fetching next 800
     *
     */
    /**
     * Scans the transactions for an address and stores new transactions in the database
     *
     * If first sync, sync from top and read 2 pages.
     * If not first sync, sync upward from existing entry.
     * If reading more than one page, blank database and start from first sync.
     *
     * SyncBlock: set to current lastBlock read if zero. If it is zero, then at finality remove all transactions before writing the new ones
     * Then write the lowest block we have read in.
     *
     *
     * @param svs
     * @param networkInfo
     * @param lastBlock
     * @return
     */
    override suspend fun storeNewTransactions(
        svs: TokensService,
        networkInfo: NetworkInfo,
        tokenAddress: String,
        lastBlock: Long,
    ): Array<Transaction?> =
        withContext(Dispatchers.IO) {
            var lastBlockNumber = lastBlock + 1
            var sortedTx: List<Transaction?>? = null
            try {
                realmManager.getRealmInstance(svs.getCurrentAddress()).use { instance ->
                    val syncToBlock = getTokenBlockRead(instance, networkInfo.chainId, TransferFetchType.ETHEREUM)
                    if (syncToBlock == 0L) {
                        lastBlockNumber = 0
                    }

                    sortedTx = syncDownwards(svs, networkInfo, tokenAddress, lastBlockNumber, 999999999)
                    if (!sortedTx.isNullOrEmpty()) {
                        val highestBlockStr = sortedTx!![sortedTx!!.size - 1]!!.blockNumber

                        storeLatestBlockRead(
                            svs.getCurrentAddress(),
                            networkInfo.chainId,
                            tokenAddress,
                            highestBlockStr,
                        )

                        if (syncToBlock == 0L || sortedTx!!.size == PAGESIZE * SYNC_PAGECOUNT) {
                            // blank all entries
                            eraseAllTransactions(instance, networkInfo.chainId)
                            writeTokenBlockRead(
                                instance,
                                networkInfo.chainId,
                                sortedTx!![0]!!.blockNumber.toLong(),
                                TransferFetchType.ETHEREUM,
                            )
                        }

                        // now write transactions
                        writeTransactions(instance, sortedTx!!)
                    }
                }
            } catch (e: JSONException) {
                // silent fail
            } catch (e: Exception) {
                Timber.e(e)
            }
            sortedTx?.toTypedArray() ?: emptyArray<Transaction?>()
        }

    /**
     * read PAGESIZE*2 transactions down from startingBlockNumber
     *
     * Note that this call is the only place that the 'earliest transaction' block can be written from.
     */
    @Throws(Exception::class)
    private fun syncDownwards(
        svs: TokensService,
        networkInfo: NetworkInfo,
        tokenAddress: String,
        lowBlockNumber: Long,
        highBlockNumber: Long
    ): List<Transaction?> {
        var page = 1
        val txMap = HashMap<String?, Transaction?>()
        var continueReading = true

        while (continueReading)  // only SYNC_PAGECOUNT pages at a time for each check, to avoid congestion
        {
            val myTxs = readTransactions(
                networkInfo,
                svs,
                tokenAddress,
                lowBlockNumber.toString(),
                highBlockNumber.toString(),
                false,
                page++
            )
            if (myTxs.size == 0) break
            populateTransactionMap(
                txMap,
                myTxs,
                networkInfo.chainId
            ) //use all transactions (wallet address null)

            if (page > SYNC_PAGECOUNT) continueReading = false

            if (myTxs.size < PAGESIZE) {
                continueReading = false
            }
        }

        return sortTransactions(txMap.values)
    }

    private fun populateTransactionMap(
        txMap: HashMap<String?, Transaction?>,
        myTxs: Array<EtherscanTransaction?>,
        chainId: Long
    ) {
        for (etx in myTxs) {
            val tx = etx?.createTransaction(null, chainId)
            if (tx != null) {
                txMap[tx.hash] = tx
            }
        }
    }

    @Throws(JSONException::class)
    private fun getEtherscanTransactions(response: String): Array<EtherscanTransaction?> {
        val stateData: JSONObject
        try {
            stateData = JSONObject(response)
        } catch (e: JSONException) {
            Timber.w(e)
            return arrayOfNulls(0)
        }

        val orders = stateData.getJSONArray("result")
        return gson.fromJson<Array<EtherscanTransaction?>>(
            orders.toString(),
            Array<EtherscanTransaction>::class.java
        )
    }

    @Throws(JSONException::class)
    private fun getCovalentTransactions(
        response: String?,
        walletAddress: String?,
    ): Array<CovalentTransaction> {
        if (response == null || response.length < 80) {
            return emptyArray()
        }
        val stateData = JSONObject(response)
        val data = stateData.getJSONObject("data")
        val orders = data.getJSONArray("items")
        val ctxs = gson.fromJson(
            orders.toString(),
            Array<CovalentTransaction>::class.java,
        )
        //reformat list to remove any transactions already seen
        val cvList: MutableList<CovalentTransaction> = ArrayList()
        realmManager.getRealmInstance(Wallet(walletAddress)).use { instance ->
            for (ctx in ctxs) {
                val realmTx =
                    instance
                        .where(RealmTransaction::class.java)
                        .equalTo("hash", ctx.tx_hash)
                        .findFirst()

                if (realmTx == null) {
                    cvList.add(ctx)
                }
            }
        }
        return cvList.toTypedArray()
    }

    @Throws(JSONException::class)
    private fun getEtherscanEvents(response: String): Array<EtherscanEvent> {
        val stateData = JSONObject(response)
        val orders = stateData.getJSONArray("result")
        return gson.fromJson(
            orders.toString(),
            Array<EtherscanEvent>::class.java
        )
    }

    @Throws(Exception::class)
    private fun writeTransactions(instance: Realm, txList: List<Transaction?>) {
        if (txList.size == 0) return

        instance.executeTransaction { r: Realm ->
            for (tx in txList) {
                var oldGasFeeMax = ""
                var oldPriorityFee = ""
                var realmTx = r.where(RealmTransaction::class.java)
                    .equalTo("hash", tx!!.hash)
                    .findFirst()

                if (realmTx == null) {
                    realmTx =
                        r.createObject(RealmTransaction::class.java, tx.hash)
                } else {
                    oldGasFeeMax =
                        if (!TextUtils.isEmpty(realmTx.maxFeePerGas)) realmTx.maxFeePerGas.toString() else tx.maxFeePerGas
                    oldPriorityFee =
                        if (!TextUtils.isEmpty(realmTx.priorityFee)) realmTx.priorityFee.toString() else tx.maxPriorityFee
                }

                TransactionsRealmCache.fill(realmTx!!, tx)
                realmTx.maxFeePerGas = oldGasFeeMax
                realmTx.setMaxPriorityFee(oldPriorityFee)
                r.insertOrUpdate(realmTx)
            }
        }
    }

    @Throws(JSONException::class)
    private fun readTransactions(
        networkInfo: NetworkInfo?,
        svs: TokensService,
        tokenAddress: String,
        lowBlock: String,
        highBlock: String,
        ascending: Boolean,
        page: Int
    ): Array<EtherscanTransaction?> {
        if (networkInfo == null) return arrayOfNulls(0)
        if (networkInfo.etherscanAPI!!.contains(EthereumNetworkBase.COVALENT)) {
            return readCovalentTransactions(
                svs,
                tokenAddress,
                networkInfo,
                ascending,
                page,
                PAGESIZE
            )
        } else if (networkInfo.chainId == com.alphawallet.ethereum.EthereumNetworkBase.OKX_ID) {
            return arrayOfNulls(0)
        }

        var result: String? = null
        val fullUrl: String

        var sort = "asc"
        if (!ascending) sort = "desc"

        if (!TextUtils.isEmpty(networkInfo.etherscanAPI)) {
            val sb = StringBuilder()
            sb.append(networkInfo.etherscanAPI)
            sb.append("module=account&action=txlist&address=")
            sb.append(tokenAddress)
            if (ascending) {
                sb.append("&startblock=")
                sb.append(lowBlock)
                sb.append("&endblock=999999999&sort=")
            } else {
                sb.append("&startblock=")
                sb.append(lowBlock)
                sb.append("&endblock=")
                sb.append(highBlock)
                sb.append("&sort=")
            }

            sb.append(sort)
            if (page > 0) {
                sb.append("&page=")
                sb.append(page)
                sb.append("&offset=")
                sb.append(PAGESIZE)
            }

            sb.append(getNetworkAPIToken(networkInfo))

            fullUrl = sb.toString()

            if (networkInfo.isCustom && !Utils.isValidUrl(networkInfo.etherscanAPI)) {
                return arrayOfNulls(0)
            }

            val request = Request.Builder()
                .url(fullUrl)
                .get()
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    if (response.body == null) return arrayOfNulls(0)
                    if (response.code / 200 == 1) {
                        result = response.body!!.string()
                        if (result!!.length >= 80 && !result!!.contains("No transactions found")) {
                            return getEtherscanTransactions(result!!)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        return arrayOfNulls(0)
    }

    /**
     * This is the function called when a user scrolls to the bottom of a transaction list.
     * First try to provide more transactions from the stored database. If there aren't any more then populate another page (800) from etherscan
     *
     * TODO: We should also check transfers over the same block range
     *
     * @param svs
     * @param network
     * @param lastTxTime
     * @return
     */
    override suspend fun fetchMoreTransactions(
        svs: TokensService,
        network: NetworkInfo,
        lastTxTime: Long,
    ): Array<TransactionMeta?> =
        withContext(Dispatchers.IO) {
            var txList = fetchOlderThan(svs.getCurrentAddress()!!, lastTxTime, network.chainId)
            if (txList.size < 800) {
                // fetch another page and return unless we already have the oldest Tx
                val oldestTxTime = if (txList.isNotEmpty()) txList[txList.size - 1]!!.timeStampSeconds else lastTxTime
                try {
                    realmManager.getRealmInstance(Wallet(svs.getCurrentAddress())).use { instance ->
                        val oldestBlockRead = getOldestBlockRead(instance, network.chainId, oldestTxTime)
                        val oldestPossibleBlock =
                            getFirstTransactionBlock(instance, network.chainId, svs.getCurrentAddress()!!)
                        Timber.d("DIAGNOSE: $oldestBlockRead : $oldestPossibleBlock")
                        if (oldestBlockRead > 0 && oldestBlockRead != oldestPossibleBlock) {
                            val syncTx =
                                syncDownwards(
                                    svs,
                                    network,
                                    svs.getCurrentAddress()!!,
                                    0,
                                    oldestBlockRead,
                                )
                            writeTransactions(instance, syncTx)
                        }

                        // now re-read last blocks from DB
                        txList = fetchOlderThan(
                            svs.getCurrentAddress()!!,
                            lastTxTime,
                            network.chainId,
                        )
                    }
                } catch (e: Exception) {
                    //
                }
            }
            txList.toTypedArray()
        }

    /**
     * Fetch the Token transfers observed on this wallet
     * @param walletAddress
     * @param networkInfo
     * @param svs
     * @param tfType
     * @return
     */
    override suspend fun readTransfers(
        walletAddress: String,
        networkInfo: NetworkInfo,
        svs: TokensService,
        tfType: TransferFetchType,
    ): Map<String, List<TransferEvent>> =
        withContext(Dispatchers.IO) {
            var tfMap: Map<String, MutableList<TransferEvent>> = HashMap()
            try {
                realmManager.getRealmInstance(Wallet(walletAddress)).use { instance ->
                    val events = fetchEvents(instance, walletAddress, networkInfo, tfType)
                    tfMap =
                        processEtherscanEvents(
                            instance,
                            walletAddress,
                            networkInfo,
                            svs,
                            events,
                            tfType,
                        )
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
            tfMap
        }

    @Throws(JSONException::class)
    private fun fetchEvents(
        instance: Realm,
        walletAddress: String,
        networkInfo: NetworkInfo,
        tfType: TransferFetchType
    ): Array<EtherscanEvent> {
        var events: Array<EtherscanEvent>
        var eventList: MutableList<EtherscanEvent> = ArrayList()
        //get oldest record
        val lastBlockFound = getTokenBlockRead(instance, networkInfo.chainId, tfType)

        if (EthereumNetworkBase.isOKX(networkInfo)) {
            events = OkLinkService.get(httpClient)
                .getEtherscanEvents(networkInfo.chainId, walletAddress, lastBlockFound, tfType)
            eventList = ArrayList(Arrays.asList(*events))
        } else {
            val currentBlock = getCurrentBlock(networkInfo.chainId).toLong()
            var upperBlock = if (currentBlock > 0L) currentBlock + 1 else 99999999L
            val lowerBlock = if (lastBlockFound == 0L) 1 else lastBlockFound

            while (true) {
                val fetchTransactions = readNextTxBatch(
                    walletAddress,
                    networkInfo,
                    upperBlock,
                    lowerBlock,
                    tfType.value
                )
                events = getEtherscanEvents(fetchTransactions)

                if (events.size == 0) {
                    break
                }

                upperBlock = events[events.size - 1].blockNumber!!.toLong() - 1
                eventList.addAll(Arrays.asList(*events))
                if (events.size == TRANSFER_RESULT_MAX && eventList.size > TRANSFER_RESULT_MAX) {
                    //If still above the last read, blank all following reads to avoid 'sync-holes'. The new events read above will be added on the return
                    //TODO: See above - need to sync the lowest block here to the lowest block in the transaction reads
                    //      This is so we can add a 'view all transactions' button which takes the user to the relevant Etherscan/Blockscout page.
                    blankTransferData(instance, networkInfo.chainId)
                }

                if (eventList.size > TRANSFER_RESULT_MAX || events.size < TRANSFER_RESULT_MAX) {
                    break
                }
            }
        }

        return eventList.toTypedArray<EtherscanEvent>()
    }

    @Throws(Exception::class)
    private fun processEtherscanEvents(
        instance: Realm,
        walletAddress: String,
        networkInfo: NetworkInfo,
        svs: TokensService,
        events: Array<EtherscanEvent>,
        tfType: TransferFetchType,
    ): Map<String, MutableList<TransferEvent>> {
        if (events.size == 0) {
            return HashMap()
        }

        val lastBlockChecked = getTokenBlockRead(instance, networkInfo.chainId, tfType)

        //Now update tokens if we don't already know this token
        val tokenTypes = writeTokens(walletAddress, networkInfo, events, svs, tfType)

        //we know all these events are relevant to the wallet, and they are all ERC20 events
        val txPair =
            writeEvents(instance, events, walletAddress, networkInfo, tokenTypes, lastBlockChecked)

        //and update the top block read
        writeTokenBlockRead(instance, networkInfo.chainId, txPair.first + 1, tfType)

        return txPair.second
    }

    private fun writeTokens(
        walletAddress: String,
        networkInfo: NetworkInfo,
        events: Array<EtherscanEvent>,
        svs: TokensService,
        tfType: TransferFetchType
    ): Map<String?, Boolean> {
        val eventMap = getEventMap(events)
        val tokenTypeMap: MutableMap<String?, Boolean> = HashMap()

        for ((contract, value) in eventMap) {
            val ev0 = value[0]
            var token = svs.getToken(networkInfo.chainId, contract)
            var newToken = false

            val tokenDecimal = calcTokenDecimals(ev0)

            if ((tfType == TransferFetchType.ERC_1155 || ev0.isERC1155(value)) &&
                (token == null || token.getInterfaceSpec() != ContractType.ERC1155)
            ) {
                token = createNewERC1155Token(value[0], networkInfo, walletAddress)
                Timber.tag(TAG)
                    .d("Discover ERC1155: " + ev0.tokenName + " (" + ev0.tokenSymbol + ")")
                newToken = true
            }
            if (tokenDecimal == -1 && (token == null ||
                        (token.getInterfaceSpec() != ContractType.ERC721 && token.getInterfaceSpec() != ContractType.ERC721_LEGACY && token.getInterfaceSpec() != ContractType.ERC721_TICKET && token.getInterfaceSpec() != ContractType.ERC721_UNDETERMINED && token.getInterfaceSpec() != ContractType.ERC1155))
            ) {
                token = createNewERC721Token(value[0], networkInfo, walletAddress, false)
                token.setTokenWallet(walletAddress)
                newToken = true
                Timber.tag(TAG).d("Discover NFT: " + ev0.tokenName + " (" + ev0.tokenSymbol + ")")
            } else if (tokenDecimal >= 0 && token == null) {
                val info = TokenInfo(
                    ev0.contractAddress,
                    ev0.tokenName,
                    ev0.tokenSymbol,
                    tokenDecimal,
                    true,
                    networkInfo.chainId
                )
                token = Token(
                    info, BigDecimal.ZERO, 0, networkInfo.shortName,
                    if (tokenDecimal > 0) ContractType.ERC20 else ContractType.MAYBE_ERC20
                )
                token.setTokenWallet(walletAddress)
                newToken = true
                Timber.tag(TAG).d("Discover ERC20: " + ev0.tokenName + " (" + ev0.tokenSymbol + ")")
            } else if (token == null) {
                svs.addUnknownTokenToCheck(
                    ContractAddress(
                        networkInfo.chainId,
                        ev0.contractAddress
                    )
                )
                Timber.tag(TAG)
                    .d("Discover unknown: " + ev0.tokenName + " (" + ev0.tokenSymbol + ")")
                continue
            }

            if (token.isNonFungible()) {
                writeAssets(eventMap, token, walletAddress, contract, svs, newToken, tfType)
            } else {
                //instruct tokensService to update balance
                svs.addBalanceCheck(token)
            }

            //Send to storage as soon as each token is done
            token.lastTxTime = System.currentTimeMillis()
            tokenTypeMap[contract] = token.isNonFungible()
        }

        return tokenTypeMap
    }

    private fun calcTokenDecimals(ev0: EtherscanEvent): Int {
        var tokenDecimal = if (!TextUtils.isEmpty(ev0.tokenDecimal) && Character.isDigit(
                ev0.tokenDecimal!![0]
            )
        ) ev0.tokenDecimal!!.toInt() else -1

        if (tokenDecimal < 1 &&
            (ev0.tokenID != null || ev0.tokenIDs != null) &&
            (ev0.tokenDecimal == null || ev0.tokenDecimal == "0")
        ) {
            tokenDecimal = -1
        }

        return tokenDecimal
    }

    private fun writeAssets(
        eventMap: Map<String?, MutableList<EtherscanEvent>>, token: Token, walletAddress: String,
        contractAddress: String?, svs: TokensService, newToken: Boolean, tfType: TransferFetchType
    ) {
        val additions = HashSet<BigInteger>()
        val removals = HashSet<BigInteger>()

        //run through addition/removal in chronological order
        for (ev in eventMap[contractAddress]!!) {
            val tokenId = getTokenId(ev.tokenID!!)

            if (tokenId.compareTo(BigInteger("-1")) == 0) continue

            if (ev.to.equals(walletAddress, ignoreCase = true)) {
                additions.add(tokenId)
                removals.remove(tokenId)
            } else {
                removals.add(tokenId)
                additions.remove(tokenId)
            }
        }

        if (additions.size > 0 && newToken) {
            if (token.getInterfaceSpec() != ContractType.ERC1155) {
                token.setInterfaceSpec(ContractType.ERC721)
            }
        }

        if (additions.size > 0 || removals.size > 0) {
            svs.updateAssets(token, ArrayList(additions), ArrayList(removals))
        }
    }

    private fun readNextTxBatch(
        walletAddress: String,
        networkInfo: NetworkInfo,
        upperBlock: Long,
        lowerBlock: Long,
        queryType: String
    ): String {
        var lowerBlock = lowerBlock
        if (TextUtils.isEmpty(networkInfo.etherscanAPI) || networkInfo.etherscanAPI!!.contains(
                EthereumNetworkBase.COVALENT
            )
        ) return JSON_EMPTY_RESULT //Covalent transfers are handled elsewhere

        var result = JSON_EMPTY_RESULT
        if (lowerBlock == 0L) lowerBlock = 1

        val fullUrl = networkInfo.etherscanAPI + "module=account&action=" + queryType +
                "&startblock=" + lowerBlock + "&endblock=" + upperBlock +
                "&address=" + walletAddress +
                "&page=1&offset=" + TRANSFER_RESULT_MAX +
                "&sort=desc" + getNetworkAPIToken(networkInfo)

        if (networkInfo.isCustom && !Utils.isValidUrl(networkInfo.etherscanAPI)) {
            return JSON_EMPTY_RESULT
        }

        val request = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", "Chrome/74.0.3729.169")
            .method("GET", null)
            .addHeader("Content-Type", "application/json")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.code / 200 == 1) {
                    result = response.body!!.string()
                    if (result.length < 80 && result.contains("No transactions found")) {
                        result = JSON_EMPTY_RESULT
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Timber.e(e)
        }

        return result
    }

    private fun getNetworkAPIToken(networkInfo: NetworkInfo): String {
        return if (networkInfo.etherscanAPI!!.contains("etherscan") /*|| networkInfo.etherscanAPI.contains("basescan.org")*/) {
            ETHERSCAN_API_KEY
        } else if (networkInfo.chainId == com.alphawallet.ethereum.EthereumNetworkBase.BINANCE_MAIN_ID) {
            BSC_EXPLORER_API_KEY
        } else if (networkInfo.chainId == com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_ID || networkInfo.chainId == com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_TEST_ID || networkInfo.chainId == com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_AMOY_ID) {
            POLYGONSCAN_API_KEY
        } else if (networkInfo.chainId == com.alphawallet.ethereum.EthereumNetworkBase.AURORA_MAINNET_ID || networkInfo.chainId == com.alphawallet.ethereum.EthereumNetworkBase.AURORA_TESTNET_ID) {
            AURORASCAN_API_KEY
        } else {
            ""
        }
    }

    @Throws(JSONException::class)
    private fun readCovalentTransactions(
        svs: TokensService,
        accountAddress: String,
        networkInfo: NetworkInfo,
        ascending: Boolean,
        page: Int,
        pageSize: Int
    ): Array<EtherscanTransaction?> {
        val covalent =
            "" + networkInfo.chainId + "/address/" + accountAddress.lowercase(Locale.getDefault()) + "/transactions_v2/?"
        val args =
            "block-signed-at-asc=" + (if (ascending) "true" else "false") + "&page-number=" + (page - 1) + "&page-size=" +
                    pageSize + "&key=" + keyProvider.getCovalentKey() //read logs to get all the transfers
        val fullUrl = networkInfo.etherscanAPI!!.replace(EthereumNetworkBase.COVALENT, covalent)
        var result: String? = null

        val request = Request.Builder()
            .url(fullUrl + args)
            .get()
            .addHeader("Content-Type", "application/json")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (response.body == null) return arrayOfNulls(0)
                result = response.body!!.string()
                if (result!!.length < 80 && result!!.contains("No transactions found")) {
                    return arrayOfNulls(0)
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
            return arrayOfNulls(0)
        }

        val covalentTransactions = getCovalentTransactions(result, svs.getCurrentAddress())

        val unhandledTxs = processCovalentEvents(covalentTransactions, svs, networkInfo)

        return unhandledTxs
    }

    private fun processCovalentEvents(
        covalentTransactions: Array<CovalentTransaction>,
        svs: TokensService,
        networkInfo: NetworkInfo
    ): Array<EtherscanTransaction?> {
        val events = toEtherscanEvents(covalentTransactions)
        try {
                realmManager.getRealmInstance(Wallet(svs.getCurrentAddress())).use { instance ->
                    processEtherscanEvents(
                        instance,
                        svs.getCurrentAddress().orEmpty(), networkInfo,
                        svs, events, TransferFetchType.ERC_20
                    )
             }
        } catch (e: Exception) {
            //
        }

        val rawTransactions: Array<EtherscanTransaction?> =
            toRawEtherscanTransactions(covalentTransactions, networkInfo)

        //List of transaction hashes that still need handling
        return rawTransactions
    }

    private fun getTokenBlockRead(instance: Realm, chainId: Long, tfType: TransferFetchType): Long {
        val rd = instance.where(RealmAuxData::class.java)
            .equalTo("instanceKey", BLOCK_ENTRY + chainId)
            .findFirst()

        return if (rd == null) {
            0L
        } else {
            when (tfType) {
                TransferFetchType.ETHEREUM -> rd.getBaseChainBlock()
                TransferFetchType.ERC_20 -> rd.getResultTime()
                TransferFetchType.ERC_721 -> rd.getResultReceivedTime()
                TransferFetchType.ERC_1155 -> rd.getChainId()
                else -> rd.getResultTime()
            }
        }
    }

    private fun writeTokenBlockRead(
        instance: Realm,
        chainId: Long,
        lastBlockChecked: Long,
        tfType: TransferFetchType
    ) {
        instance.executeTransaction { r: Realm ->
            var rd = r.where(RealmAuxData::class.java)
                .equalTo("instanceKey", BLOCK_ENTRY + chainId)
                .findFirst()
            if (rd == null) {
                rd = r.createObject(RealmAuxData::class.java, BLOCK_ENTRY + chainId)
                rd.setResult(DB_RESET)
            }
            when (tfType) {
                TransferFetchType.ETHEREUM -> rd!!.setBaseChainBlock(lastBlockChecked)
                TransferFetchType.ERC_20 -> rd!!.setResultTime(lastBlockChecked)
                TransferFetchType.ERC_721 -> rd!!.setResultReceivedTime(lastBlockChecked)
                TransferFetchType.ERC_1155 -> rd!!.setChainId(lastBlockChecked)
                else -> rd!!.setResultTime(lastBlockChecked)
            }
        }
    }

    private fun getOldestBlockRead(instance: Realm, chainId: Long, lastTxTime: Long): Long {
        var txBlockRead: Long = 0
        try {
            val txs = instance.where(
                RealmTransaction::class.java
            )
                .equalTo("chainId", chainId)
                .sort("timeStamp", Sort.ASCENDING)
                .limit(1)
                .findAll()

            if (txs != null && txs.size > 0) {
                val blockNumber = txs.first()!!.blockNumber
                txBlockRead = blockNumber!!.toLong()
            }
        } catch (e: Exception) {
            //
        }

        return txBlockRead
    }

    private fun getFirstTransactionBlock(
        instance: Realm,
        chainId: Long,
        walletAddress: String
    ): Long {
        var txBlockRead: Long = 0
        try {
            val realmToken = instance.where(RealmToken::class.java)
                .equalTo("address", TokensRealmSource.databaseKey(chainId, walletAddress))
                .findFirst()

            if (realmToken != null) {
                txBlockRead = realmToken.getEarliestTransactionBlock()
            }
        } catch (e: Exception) {
            //
        }

        return txBlockRead
    }

    private fun fetchOlderThan(
        walletAddress: String,
        fetchTime: Long,
        chainId: Long
    ): List<TransactionMeta?> {
        val metas: MutableList<TransactionMeta?> = ArrayList()
        try {
            realmManager.getRealmInstance(walletAddress.lowercase(Locale.getDefault()))
                .use { instance ->
                    val txs = instance.where(
                        RealmTransaction::class.java
                    )
                        .sort("timeStamp", Sort.DESCENDING)
                        .lessThan("timeStamp", fetchTime)
                        .equalTo("chainId", chainId)
                        .limit(PAGESIZE.toLong())
                        .findAll()
                    for (item in txs) {
                        val tm = TransactionMeta(
                            item.hash!!, item.timeStamp,
                            item.to!!, item.chainId, item.blockNumber!!
                        )
                        metas.add(tm)
                    }
                }
        } catch (e: Exception) {
            //
        }

        return metas
    }

    private fun storeLatestBlockRead(
        walletAddress: String?,
        chainId: Long,
        tokenAddress: String,
        lastBlockRead: String
    ) {
        try {
            realmManager.getRealmInstance(walletAddress).use { instance ->
                instance.executeTransactionAsync { r: Realm ->
                    val realmToken = r.where(RealmToken::class.java)
                        .equalTo(
                            "address",
                            TokensRealmSource.databaseKey(chainId, tokenAddress)
                        )
                        .findFirst()
                    if (realmToken != null) {
                        realmToken.setLastBlock(lastBlockRead.toLong())
                        realmToken.setLastTxTime(System.currentTimeMillis())
                    }
                }
            }
        } catch (e: Exception) {
            //
        }
    }

    private fun storeEarliestBlockRead(
        instance: Realm,
        chainId: Long,
        walletAddress: String,
        earliestBlock: Long
    ) {
        try {
            instance.executeTransaction { r: Realm ->
                val realmToken = r.where(RealmToken::class.java)
                    .equalTo(
                        "address",
                        TokensRealmSource.databaseKey(chainId, walletAddress)
                    )
                    .findFirst()
                realmToken?.setEarliestTransactionBlock(earliestBlock)
            }
        } catch (e: Exception) {
            //
        }
    }

    fun deleteAllChainTransactions(instance: Realm, chainId: Long, walletAddress: String) {
        try {
            instance.executeTransaction { r: Realm ->
                val txs = r.where(
                    RealmTransaction::class.java
                )
                    .equalTo("chainId", chainId)
                    .findAll()
                if (txs != null && txs.size > 0) {
                    txs.deleteAllFromRealm()
                }
                resetBlockRead(r, chainId, walletAddress)
            }
        } catch (e: Exception) {
            //
        }
    }

    private fun resetBlockRead(r: Realm, chainId: Long, walletAddress: String) {
        val realmToken = r.where(RealmToken::class.java)
            .equalTo("address", TokensRealmSource.databaseKey(chainId, walletAddress))
            .findFirst()

        if (realmToken != null) {
            realmToken.setEarliestTransactionBlock(0)
            realmToken.setLastBlock(0)
            realmToken.setLastTxTime(0)
        }
    }

    @Throws(Exception::class)
    private fun writeEvents(
        instance: Realm,
        events: Array<EtherscanEvent>,
        walletAddress: String,
        networkInfo: NetworkInfo,
        tokenTypes: Map<String?, Boolean>,
        lastBlockRead: Long,
    ): Pair<Long, Map<String, MutableList<TransferEvent>>> {
        val TO_TOKEN = "[TO_ADDRESS]"
        val FROM_TOKEN = "[FROM_ADDRESS]"
        val AMOUNT_TOKEN = "[AMOUNT_TOKEN]"
        val VALUES =
            "from,address,$FROM_TOKEN,to,address,$TO_TOKEN,amount,uint256,$AMOUNT_TOKEN"

        val txFetches = HashSet<String>()
        var highestBlockRead =
            lastBlockRead - 1 // -1 because we +1 when writing the value - if there's no events then keep value the same
        val transferEventMap: MutableMap<String, MutableList<TransferEvent>> = HashMap()
        val txWriteMap: MutableMap<String?, Transaction> = HashMap()
        //write event list
        for (ev in events) {
            val eventBlockNumber = ev.blockNumber!!.toLong()
            if (eventBlockNumber < lastBlockRead) {
                continue
            }

            val scanAsNFT = tokenTypes.getOrDefault(ev.contractAddress, false)
            val tx = if (scanAsNFT) ev.createNFTTransaction(networkInfo) else ev.createTransaction(
                networkInfo
            )

            //find tx name
            val activityName = tx.getEventName(walletAddress)
            //Etherscan sometimes interprets NFT transfers as FT's
            //TODO: Handle ERC1155 multiple token/batch transfers
            //For now; just use first token
            ev.patchFirstTokenID()

            //Sometimes the value for TokenID in Etherscan is in the value field.
            val tokenValue =
                if (scanAsNFT && ev.tokenID != null) ev.tokenID!! else (if (ev.value != null) ev.value else "0")!!

            var valueList = VALUES.replace(TO_TOKEN, ev.to!!).replace(
                FROM_TOKEN,
                ev.from!!
            ).replace(AMOUNT_TOKEN, tokenValue)
            if (!TextUtils.isEmpty(ev.tokenValue)) {
                valueList = valueList + "count,uint256," + ev.tokenValue
            }

            val txHash = tx.hash ?: continue
            val thisHashList = transferEventMap.computeIfAbsent(txHash) { ArrayList() }
            thisHashList.add(TransferEvent(valueList, activityName, ev.contractAddress, tokenValue))

            //add to transaction write list
            txWriteMap[ev.contractAddress] = tx

            if (eventBlockNumber > highestBlockRead) {
                highestBlockRead = eventBlockNumber
            }
        }

        instance.executeTransaction { r: Realm ->
            storeTransferData(r, networkInfo.chainId, transferEventMap)
            storeTransactions(
                r,
                txWriteMap,
                if (networkInfo.etherscanAPI!!.contains(EthereumNetworkBase.COVALENT)) null else txFetches
            ) //store the transaction data and initiate tx fetch if not already known
        }

        fetchRequiredTransactions(networkInfo.chainId, txFetches, walletAddress)
        return Pair(highestBlockRead, transferEventMap)
    }

    private fun storeTransactions(
        r: Realm,
        txWriteMap: Map<String?, Transaction>,
        txFetches: HashSet<String>?
    ) {
        for ((contractAddress, tx) in txWriteMap) {
            var realmTx = r.where(RealmTransaction::class.java)
                .equalTo("hash", tx.hash)
                .findFirst()

            if (realmTx == null) {
                realmTx = r.createObject(RealmTransaction::class.java, tx.hash)
                //fetch the actual transaction here
                txFetches?.add(tx.hash.toString())
            } else if (realmTx.contractAddress == null || !realmTx.contractAddress.equals(
                    contractAddress,
                    ignoreCase = true
                )
            ) {
                realmTx.contractAddress = contractAddress
            }

            if (realmTx!!.input == null || realmTx.input!!.length <= 10 || txFetches == null) {
                TransactionsRealmCache.fill(realmTx, tx)
                realmTx.contractAddress =
                    contractAddress //for indexing by contract (eg Token Activity)
            }
        }
    }

    private fun storeTransferData(
        instance: Realm,
        chainId: Long,
        transferEventMap: Map<String, MutableList<TransferEvent>>,
    ) {
        for ((key, value) in transferEventMap) {
            val realmPeek = instance.where(RealmTransfer::class.java)
                .equalTo("hash", RealmTransfer.databaseKey(chainId, key))
                .findFirst()

            if (realmPeek != null) continue

            //write each event set
            for (thisEvent in value) {
                val realmTransfer = instance.createObject(
                    RealmTransfer::class.java
                )
                realmTransfer.setHashKey(chainId, key)
                realmTransfer.setTokenAddress(thisEvent.contractAddress)
                realmTransfer.setEventName(thisEvent.activityName)
                realmTransfer.setTransferDetail(thisEvent.valueList)
            }
        }
    }

    private fun blankTransferData(instance: Realm, chainId: Long) {
        instance.executeTransaction { r: Realm ->
            val realmTx = r.where(
                RealmTransfer::class.java
            )
                .like("hash", "*-$chainId", Case.INSENSITIVE)
                .findAll()
            realmTx.deleteAllFromRealm()
        }
    }

    /**
     * Write the transaction to Realm
     *
     * @param instance Realm
     * @param tx Transaction formed initially procedurally from the event, then from Ethereum node if we didn't already have it
     * @param txFetches build list of transactions that need fetching
     */
    private fun writeTransaction(
        instance: Realm,
        tx: Transaction,
        contractAddress: String,
        txFetches: HashSet<String?>?
    ) {
        var realmTx = instance.where(RealmTransaction::class.java)
            .equalTo("hash", tx.hash)
            .findFirst()

        if (realmTx == null) {
            realmTx = instance.createObject(RealmTransaction::class.java, tx.hash)

            //fetch the actual transaction here
            txFetches?.add(tx.hash)
        } else if (realmTx.contractAddress == null || !realmTx.contractAddress.equals(
                contractAddress,
                ignoreCase = true
            )
        ) {
            realmTx.contractAddress = contractAddress
        }

        if (realmTx!!.input == null || realmTx.input!!.length <= 10 || txFetches == null) {
            TransactionsRealmCache.fill(realmTx, tx)
            realmTx.contractAddress = contractAddress //for indexing by contract (eg Token Activity)
        }
    }

    /**
     * This thread will execute in the background filling in transactions.
     * It doesn't have to be cancelled if we switch wallets because these transactions need to be fetched anyway
     * @param chainId networkId
     * @param txFetches map of transactions that need writing. Note we use a map to de-duplicate
     */
    private fun fetchRequiredTransactions(
        chainId: Long,
        txFetches: HashSet<String>,
        walletAddress: String
    ) {
        var txLimitCount = 0
        for (txHash in txFetches) {
            addTransactionHashFetch(txHash, chainId, walletAddress)
            if (txLimitCount++ > TRANSACTION_FETCH_LIMIT) {
                break
            }
        }
    }

    /**
     * These functions are experimental, for discovering and populating NFT's without opensea.
     * So far the experiment appears to be working correctly,
     *
     * Tested: rapid discovery and update of tokens without opensea.
     * Once opensea is on, tokens are updated correctly.
     *
     * If tokens already discovered from opensea then we don't replace them here.
     */
    private fun getEventMap(events: Array<EtherscanEvent>): Map<String?, MutableList<EtherscanEvent>> {
        val eventMap: MutableMap<String?, MutableList<EtherscanEvent>> = HashMap()
        for (ev in events) {
            var thisEventList = eventMap[ev.contractAddress]
            if (thisEventList == null) {
                thisEventList = ArrayList()
                eventMap[ev.contractAddress] = thisEventList
            }

            thisEventList.add(ev)
        }

        return eventMap
    }

    private fun getTokenId(tokenID: String): BigInteger {
        var tokenIdBI = try {
            BigInteger(tokenID)
        } catch (e: Exception) {
            BigInteger.valueOf(-1)
        }

        return tokenIdBI
    }

    private fun createNewERC721Token(
        ev: EtherscanEvent,
        networkInfo: NetworkInfo,
        walletAddress: String,
        knownERC721: Boolean
    ): ERC721Token {
        val info = TokenInfo(
            ev.contractAddress,
            ev.tokenName,
            ev.tokenSymbol,
            0,
            false,
            networkInfo.chainId
        )
        val newToken: ERC721Token = ERC721Token(
            info,
            null,
            BigDecimal.ZERO,
            0,
            networkInfo.shortName,
            if (knownERC721) ContractType.ERC721 else ContractType.ERC721_UNDETERMINED
        )
        newToken.setTokenWallet(walletAddress)
        return newToken
    }

    private fun createNewERC1155Token(
        ev: EtherscanEvent,
        networkInfo: NetworkInfo,
        walletAddress: String
    ): ERC1155Token {
        val info = TokenInfo(
            ev.contractAddress,
            ev.tokenName,
            ev.tokenSymbol,
            0,
            false,
            networkInfo.chainId
        )
        val newToken = ERC1155Token(info, null, 0, networkInfo.shortName)
        newToken.setTokenWallet(walletAddress)
        return newToken
    }

    private fun eraseAllTransactions(instance: Realm, chainId: Long) {
        instance.executeTransaction { r: Realm ->
            val realmTx = r.where(
                RealmTransaction::class.java
            )
                .equalTo("chainId", chainId)
                .findAll()
            realmTx.deleteAllFromRealm()
        }
    }

    private fun sortTransactions(txCollection: Collection<Transaction?>): List<Transaction?> =
        txCollection
            .filterNotNull()
            .sortedWith(compareBy<Transaction> { it.blockNumber.toLong() })


    companion object {
        private const val TAG = "TXNETCLIENT"
    }
}
