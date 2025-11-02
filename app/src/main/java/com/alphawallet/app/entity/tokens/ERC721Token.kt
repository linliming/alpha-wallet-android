package com.alphawallet.app.entity.tokens

import android.text.TextUtils
import android.util.Pair
import com.alphawallet.app.R
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.LogOverflowException
import com.alphawallet.app.entity.SyncDef
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.TransactionInput
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.EventResult
import com.alphawallet.app.repository.TokenRepository.Companion.balanceOf
import com.alphawallet.app.repository.TokenRepository.Companion.callSmartContractFunction
import com.alphawallet.app.repository.TokenRepository.Companion.getWeb3jService
import com.alphawallet.app.repository.TokenRepository.Companion.getWeb3jServiceForEvents
import com.alphawallet.app.repository.TokensRealmSource.Companion.databaseKey
import com.alphawallet.app.repository.entity.RealmNFTAsset
import com.alphawallet.app.repository.entity.RealmToken
import com.alphawallet.app.service.TransactionsService
import com.alphawallet.app.util.Utils.parseTokenId
import io.realm.Case
import io.realm.Realm
import org.web3j.abi.EventEncoder
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.Utils
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.BatchRequest
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction
import org.web3j.protocol.core.methods.response.EthCall
import org.web3j.protocol.core.methods.response.EthLog
import org.web3j.protocol.core.methods.response.Log
import org.web3j.tx.Contract.staticExtractEventParameters
import org.web3j.utils.Numeric
import timber.log.Timber
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class ERC721Token(
    tokenInfo: TokenInfo,
    balanceList: Map<BigInteger, NFTAsset>?,
    balance: BigDecimal,
    blancaTime: Long,
    networkName: String,
    type: ContractType
) : Token(tokenInfo, balance, blancaTime, networkName, type) {

    private val tokenBalanceAssets: MutableMap<BigInteger, NFTAsset> =
        if (balanceList != null) ConcurrentHashMap(balanceList) else ConcurrentHashMap()

    init {
        setInterfaceSpec(type)
        group = TokenGroup.NFT
    }

    override fun getTokenAssets(): Map<BigInteger, NFTAsset> = tokenBalanceAssets

    override fun addAssetToTokenBalanceAssets(tokenId: BigInteger, asset: NFTAsset) {
        tokenBalanceAssets[tokenId] = asset
    }

    override fun getAssetForToken(tokenIdStr: String): NFTAsset? {
        return tokenBalanceAssets[parseTokenId(tokenIdStr)]
    }

    override fun getAssetForToken(tokenId: BigInteger): NFTAsset? = tokenBalanceAssets[tokenId]

    override fun getContractType(): Int = R.string.erc721

    override fun getStringBalanceForUI(decimalPlaces: Int): String {
        return if (balance > BigDecimal.ZERO) balance.toPlainString() else "0"
    }

    override fun getTransferBytes(to: String,
        transferData: ArrayList<Pair<BigInteger, NFTAsset>>): ByteArray? {
        if (transferData.size != 1) {
            return Numeric.hexStringToByteArray("0x")
        }
        val transferFunction = getTransferFunction(to, listOf(transferData[0].first)) ?: return Numeric.hexStringToByteArray("0x")
        val encodedFunction = FunctionEncoder.encode(transferFunction)
        return Numeric.hexStringToByteArray(Numeric.cleanHexPrefix(encodedFunction))
    }

    @Throws(NumberFormatException::class)
    override fun getTransferFunction(to: String, tokenIds: List<BigInteger>): Function {
        if (tokenIds.size > 1) {
            throw NumberFormatException("ERC721Ticket can't handle batched transfers")
        }

        val tokenId = tokenIds[0]
        return if (tokenUsesLegacyTransfer()) {
            val params: List<Type<*>> = listOf(Address(to), Uint256(tokenId))
            Function("transfer", params, emptyList())
        } else {
            val params: List<Type<*>> = listOf(Address(getWallet()), Address(to), Uint256(tokenId))
            Function("safeTransferFrom", params, emptyList())
        }
    }

    override fun getTokenCount(): Int = balance.toInt()

    override fun getFullBalance(): String = balance.toPlainString()

    override fun isToken(): Boolean = false

    override fun hasArrayBalance(): Boolean = true

    override fun hasPositiveBalance(): Boolean = tokenBalanceAssets.isNotEmpty()

    override fun setRealmBalance(realmToken: RealmToken) {
        realmToken.balance = balance.toPlainString()
    }

    override fun isERC721(): Boolean = true

    override fun isNonFungible(): Boolean = true

    override fun getArrayBalance(): List<BigInteger> = ArrayList(tokenBalanceAssets.keys)

    override fun checkRealmBalanceChange(realmToken: RealmToken): Boolean {
        if (contractType == null || contractType.ordinal != realmToken.interfaceSpec) return true
        val currentState = realmToken.balance ?: return true
        if (lastTxTime > realmToken.lastTxTime) return true
        if (currentState != balance.toPlainString()) return true
        for (asset in tokenBalanceAssets.values) {
            if (!asset.needsLoading() && !asset.requiresReplacement()) return true
        }
        return false
    }

    override fun checkBalanceChange(oldToken: Token): Boolean {
        if (super.checkBalanceChange(oldToken)) return true
        val oldAssets = oldToken.getTokenAssets()
        if (tokenBalanceAssets.isNotEmpty() && oldAssets != null && tokenBalanceAssets.size != oldAssets.size) return true
        for ((tokenId, asset) in tokenBalanceAssets) {
            val oldAsset = oldToken.getAssetForToken(tokenId)
            if (asset == null || oldAsset == null || asset != oldAsset) {
                return true
            }
        }
        return false
    }

    override fun convertValue(prefix: String, vResult: EventResult, precision: Int): String {
        val adjustedPrecision = precision + 1
        val value = vResult.value ?: "0"
        return if (value.length > adjustedPrecision) {
            prefix + "1"
        } else {
            "#$value"
        }
    }

    override fun processLogsAndStoreTransferEvents(
        receiveLogs: EthLog,
        event: Event,
        txHashes: HashSet<String>,
        realm: Realm
    ): HashSet<BigInteger> {
        val tokenIds = HashSet<BigInteger>()
        for (ethLog in receiveLogs.logs) {
            val log = ethLog.get() as Log
            val block = log.blockNumberRaw
            if (block.isNullOrEmpty()) continue
            val txHash = log.transactionHash
            val eventValues = staticExtractEventParameters(event, log)
            val idResult = eventSync.getEventIdResult(eventValues.indexedValues[2], null)
            tokenIds.addAll(idResult.first)
            val from = eventValues.indexedValues[0].value.toString()
            val to = eventValues.indexedValues[1].value.toString()
            eventSync.storeTransferData(realm, from, to, idResult, txHash)
            txHashes.add(txHash)
        }
        return tokenIds
    }

    override fun updateBalance(realm: Realm): BigDecimal {
        val addressKey = tokenInfo.address?.lowercase(Locale.ROOT) ?: return balance
        if (balanceChecks.containsKey(addressKey)) {
            return balance
        }

        val web3j = getWeb3jServiceForEvents(tokenInfo.chainId)
        if (contractType == ContractType.ERC721_ENUMERABLE) {
            updateEnumerableBalance(web3j, realm)
            return balance
        }

        val sync: SyncDef = eventSync.getSyncDef(realm) ?: return balance
        val startBlock: DefaultBlockParameter = DefaultBlockParameter.valueOf(sync.eventReadStartBlock)
        val endBlock: DefaultBlockParameter = if (sync.eventReadEndBlock == BigInteger.valueOf(-1L)) {
            DefaultBlockParameterName.LATEST
        } else {
            DefaultBlockParameter.valueOf(sync.eventReadEndBlock)
        }

        try {
            val currentBlock = TransactionsService.getCurrentBlock(tokenInfo.chainId)
            balanceChecks[addressKey] = true

            val evRead = eventSync.processTransferEvents(
                web3j,
                transferEvent,
                startBlock,
                endBlock,
                realm
            )

            eventSync.updateEventReads(realm, sync, currentBlock, evRead.first)

            val allMovingTokens = HashSet<BigInteger>(evRead.second.first)
            allMovingTokens.addAll(evRead.second.second)

            if (allMovingTokens.isEmpty() && balance.toInt() != tokenBalanceAssets.size) {
                allMovingTokens.addAll(tokenBalanceAssets.keys)
            }

            if (allMovingTokens.isNotEmpty()) {
                val tokenIdsHeld = checkBalances(web3j, allMovingTokens)
                updateRealmBalance(realm, tokenIdsHeld, allMovingTokens)
            }
        } catch (e: LogOverflowException) {
            if (eventSync.handleEthLogError(e.error, startBlock, endBlock, sync, realm)) {
                balanceChecks.remove(addressKey)
                return updateBalance(realm)
            }
        } catch (e: Exception) {
            Timber.w(e)
        } finally {
            balanceChecks.remove(addressKey)
        }

        if (endBlock == DefaultBlockParameterName.LATEST && balance.compareTo(BigDecimal.valueOf(tokenBalanceAssets.size.toLong())) != 0) {
            eventSync.resetEventReads(realm)
        }

        return balance
    }

    private val transferEvent: Event
        get() {
            val paramList = ArrayList<TypeReference<*>>()
            paramList.add(object : TypeReference<Address>(true) {})
            paramList.add(object : TypeReference<Address>(true) {})
            paramList.add(object : TypeReference<Uint256>(true) {})
            return Event("Transfer", paramList)
        }

    private fun updateEnumerableBalance(web3j: Web3j, realm: Realm) {
        val tokenIdsHeld = HashSet<BigInteger>()
        val fetchBalance = checkBalance()
        val currentBalance = if (fetchBalance >= 0) fetchBalance else balance.toLong()

        try {
            val batchLimit = EthereumNetworkBase.getBatchProcessingLimit(tokenInfo.chainId)
            if (batchLimit > 0 && currentBalance > 1) {
                updateEnumerableBatchBalance(web3j, currentBalance, tokenIdsHeld)
            } else {
                for (tokenIndex in 0 until currentBalance) {
                    val tokenId = callSmartContractFunction(
                        tokenInfo.chainId,
                        tokenOfOwnerByIndex(BigInteger.valueOf(tokenIndex)),
                        getAddress(),
                        getWallet()
                    )
                    if (tokenId.isNullOrEmpty()) continue
                    tokenIdsHeld.add(BigInteger(tokenId))
                }
            }
            updateRealmForEnumerable(realm, tokenIdsHeld)
            balance = BigDecimal.valueOf(tokenIdsHeld.size.toLong())
        } catch (e: IOException) {
            Timber.e(e)
        }
    }

    @Throws(IOException::class)
    private fun updateEnumerableBatchBalance(
        web3j: Web3j,
        currentBalance: Long,
        tokenIdsHeld: HashSet<BigInteger>
    ) {
        var requests = web3j.newBatch()
        val batchLimit = EthereumNetworkBase.getBatchProcessingLimit(tokenInfo.chainId)
        for (tokenIndex in 0 until currentBalance) {
            requests.add(getContractCall(web3j, tokenOfOwnerByIndex(BigInteger.valueOf(tokenIndex)), getAddress()))
            if (requests.requests.size >= batchLimit) {
                handleEnumerableRequests(requests, tokenIdsHeld)
                requests = web3j.newBatch()
            }
        }
        if (requests.requests.isNotEmpty()) {
            handleEnumerableRequests(requests, tokenIdsHeld)
        }
    }

    private fun checkBalance(): Long {
        return try {
            val getBalance = balanceOf(getWallet())
            val responseRaw = callSmartContractFunction(tokenInfo.chainId, getBalance, tokenInfo.address.toString(), getWallet())
            if (!responseRaw.isNullOrEmpty()) BigDecimal(responseRaw).longValueExact() else -1
        } catch (e: Exception) {
            Timber.w(e)
            -1
        }
    }

    @Throws(IOException::class)
    private fun handleEnumerableRequests(requests: BatchRequest, tokenIdsHeld: HashSet<BigInteger>) {
        val responses = requests.send()
        if (responses.responses.size != requests.requests.size) {
            EthereumNetworkBase.setBatchProcessingError(tokenInfo.chainId)
            return
        }
        for (rsp in responses.responses) {
            val tokenId = getTokenId(rsp)
            if (tokenId != null) {
                tokenIdsHeld.add(tokenId)
            }
        }
    }

    private fun getTokenId(rsp: Response<*>): BigInteger? {
        val outputParams: List<TypeReference<Type<*>>> = Utils.convert(listOf(object : TypeReference<Uint256>() {}))
        val responseValues = FunctionReturnDecoder.decode((rsp as EthCall).value, outputParams)
        if (responseValues.isNotEmpty()) {
            val tokenIdStr = responseValues[0].value.toString()
            if (!TextUtils.isEmpty(tokenIdStr)) {
                return BigInteger(tokenIdStr)
            }
        }
        if (rsp.hasError()) {
            EthereumNetworkBase.setBatchProcessingError(tokenInfo.chainId)
        }
        return null
    }

    private fun updateRealmBalance(
        realm: Realm,
        tokenIds: Set<BigInteger>?,
        allMovingTokens: Set<BigInteger>
    ) {
        var updated = false
        val removedTokens = HashSet(allMovingTokens)
        if (!tokenIds.isNullOrEmpty()) {
            for (tokenId in tokenIds) {
                var asset = tokenBalanceAssets[tokenId]
                if (asset == null) {
                    asset = NFTAsset(tokenId)
                    tokenBalanceAssets[tokenId] = asset
                    updated = true
                }
                removedTokens.remove(tokenId)
            }
            if (updated) {
                updateRealmBalances(realm, tokenIds)
            }
        }
        removeRealmBalance(realm, removedTokens)
    }

    private fun removeRealmBalance(realm: Realm, removedTokens: Set<BigInteger>) {
        if (removedTokens.isEmpty()) return
        realm.executeTransaction { r ->
            for (tokenId in removedTokens) {
                val key = RealmNFTAsset.databaseKey(this, tokenId)
                r.where(RealmNFTAsset::class.java)
                    .equalTo("tokenIdAddr", key)
                    .findFirst()
                    ?.deleteFromRealm()
            }
        }
    }

    private fun updateRealmForEnumerable(realm: Realm, currentTokens: HashSet<BigInteger>) {
        val storedBalance = HashSet<BigInteger>()
        val results = realm.where(RealmNFTAsset::class.java)
            .like("tokenIdAddr", databaseKey(this) + "-*", Case.INSENSITIVE)
            .findAll()
        for (asset in results) {
            storedBalance.add(BigInteger(asset.getTokenId()))
        }
        if (currentTokens == storedBalance) return
        realm.executeTransaction { r ->
            results.deleteAllFromRealm()
            for (tokenId in currentTokens) {
                val key = RealmNFTAsset.databaseKey(this, tokenId)
                var realmAsset = r.where(RealmNFTAsset::class.java)
                    .equalTo("tokenIdAddr", key)
                    .findFirst()
                if (realmAsset == null) {
                    realmAsset = r.createObject(RealmNFTAsset::class.java, key)
                    realmAsset.metaData = NFTAsset(tokenId).jsonMetaData()
                    r.insertOrUpdate(realmAsset)
                }
            }
        }
    }

    private fun updateRealmBalances(realm: Realm, tokenIds: Set<BigInteger>) {
        realm.executeTransaction { r ->
            for (tokenId in tokenIds) {
                val key = RealmNFTAsset.databaseKey(this, tokenId)
                var realmAsset = r.where(RealmNFTAsset::class.java)
                    .equalTo("tokenIdAddr", key)
                    .findFirst()
                if (realmAsset == null) {
                    realmAsset = r.createObject(RealmNFTAsset::class.java, key)
                    realmAsset.metaData = tokenBalanceAssets[tokenId]?.jsonMetaData()
                    r.insertOrUpdate(realmAsset)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun checkBalances(web3j: Web3j, eventIds: HashSet<BigInteger>): HashSet<BigInteger> {
        val heldTokens = HashSet<BigInteger>()
        val batchLimit = EthereumNetworkBase.getBatchProcessingLimit(tokenInfo.chainId)
        if (batchLimit > 0 && eventIds.size > 1) {
            return checkBatchBalances(web3j, eventIds)
        }
        for (tokenId in eventIds) {
            val owner = callSmartContractFunction(tokenInfo.chainId, ownerOf(tokenId), getAddress(), getWallet())
            if (owner.isNullOrEmpty() || owner.equals(getWallet(), ignoreCase = true)) {
                heldTokens.add(tokenId)
            }
        }
        return heldTokens
    }

    @Throws(IOException::class)
    private fun checkBatchBalances(web3j: Web3j, eventIds: HashSet<BigInteger>): HashSet<BigInteger> {
        val heldTokens = HashSet<BigInteger>()
        val balanceIds = ArrayList<BigInteger>()
        var requests = web3j.newBatch()
        val batchLimit = EthereumNetworkBase.getBatchProcessingLimit(tokenInfo.chainId)
        for (tokenId in eventIds) {
            requests.add(getContractCall(web3j, ownerOf(tokenId), getAddress()))
            balanceIds.add(tokenId)
            if (requests.requests.size >= batchLimit) {
                handleRequests(requests, balanceIds, heldTokens)
                requests = web3j.newBatch()
            }
        }
        if (requests.requests.isNotEmpty()) {
            handleRequests(requests, balanceIds, heldTokens)
        }
        return heldTokens
    }

    @Throws(IOException::class)
    private fun handleRequests(
        requests: BatchRequest,
        balanceIds: MutableList<BigInteger>,
        heldTokens: HashSet<BigInteger>
    ) {
        var index = 0
        val responses = requests.send()
        if (responses.responses.size != requests.requests.size) {
            EthereumNetworkBase.setBatchProcessingError(tokenInfo.chainId)
            return
        }
        for (response in responses.responses) {
            val tokenId = balanceIds[index]
            if (isOwner(response, tokenId)) {
                heldTokens.add(tokenId)
            }
            index++
        }
        balanceIds.clear()
    }

    private fun isOwner(rsp: Response<*>, tokenId: BigInteger): Boolean {
        val response = rsp as EthCall
        val responseValues = FunctionReturnDecoder.decode(response.value, ownerOf(tokenId).outputParameters)
        if (responseValues.isNotEmpty()) {
            val owner = responseValues[0].value.toString()
            return owner.isNotEmpty() && owner.equals(getWallet(), ignoreCase = true)
        }
        return false
    }

    override fun getReceiveBalanceFilter(
        event: Event,
        startBlock: DefaultBlockParameter,
        endBlock: DefaultBlockParameter
    ): org.web3j.protocol.core.methods.request.EthFilter {
        val filter = org.web3j.protocol.core.methods.request.EthFilter(startBlock, endBlock, tokenInfo.address)
            .addSingleTopic(EventEncoder.encode(event))
        filter.addSingleTopic(null)
        filter.addSingleTopic("0x" + TypeEncoder.encode(Address(getWallet())))
        filter.addSingleTopic(null)
        return filter
    }

    override fun getSendBalanceFilter(
        event: Event,
        startBlock: DefaultBlockParameter,
        endBlock: DefaultBlockParameter
    ): org.web3j.protocol.core.methods.request.EthFilter {
        val filter = org.web3j.protocol.core.methods.request.EthFilter(startBlock, endBlock, tokenInfo.address)
            .addSingleTopic(EventEncoder.encode(event))
        filter.addSingleTopic("0x" + TypeEncoder.encode(Address(getWallet())))
        filter.addSingleTopic(null)
        filter.addSingleTopic(null)
        return filter
    }

    override fun getFirstImageUrl(): String {
        val asset = tokenBalanceAssets.values.firstOrNull() ?: return ""
        return if (asset.hasImageAsset()) asset.thumbnail else ""
    }

    fun getTransferID(tx: Transaction): String {
        val miscData = tx.transactionInput?.miscData ?: return "0"
        if (miscData.isEmpty()) return "0"
        return try {
            val tokenHex = miscData[0]
            if (tokenHex.isNotEmpty()) {
                val id = BigInteger(tokenHex, 16)
                val tokenIdStr = id.toString()
                if (tokenIdStr.length < 7) {
                    id.toString(16)
                } else {
                    "0"
                }
            } else {
                "0"
            }
        } catch (e: Exception) {
            "0"
        }
    }

    override fun getTransferValue(txInput: TransactionInput, transactionBalancePrecision: Int): String {
        val precision = transactionBalancePrecision + 1
        return try {
            val tokenId = BigInteger(txInput.miscData[0], 16)
            val tokenIdStr = tokenId.toString()
            if (tokenIdStr.length > precision) {
                "1"
            } else {
                "#${tokenId}"
            }
        } catch (e: Exception) {
            getTransferValueRaw(txInput).toString()
        }
    }

    override fun getTransferValueRaw(txInput: TransactionInput): BigInteger {
        return try {
            BigInteger(txInput.miscData[0], 16)
        } catch (e: Exception) {
            BigInteger.ONE
        }
    }

    override fun getBalanceRaw(): BigDecimal = balance

    override fun queryAssets(assetMap: Map<BigInteger, NFTAsset>): Map<BigInteger, NFTAsset> {
        val web3j = getWeb3jService(tokenInfo.chainId)
        val heldAssets = try {
            checkBalances(web3j, HashSet(assetMap.keys))
        } catch (_: Exception) {
            HashSet<BigInteger>()
        }

        val updatedAssets = HashMap<BigInteger, NFTAsset>()
        for ((id, asset) in assetMap) {
            val mutableAsset = asset
            mutableAsset.balance = if (heldAssets.contains(id)) BigDecimal.ONE else BigDecimal.ZERO
            updatedAssets[id] = mutableAsset
        }
        return updatedAssets
    }

    override fun getAssetChange(oldAssetList: Map<BigInteger, NFTAsset>): Map<BigInteger, NFTAsset> {
        val updatedAssets = HashMap<BigInteger, NFTAsset>()
        val changedAssetList = HashSet(tokenBalanceAssets.keys)
        changedAssetList.removeAll(oldAssetList.keys)

        val unchangedAssets = HashSet(tokenBalanceAssets.keys)
        unchangedAssets.removeAll(changedAssetList)

        val removedAssets = HashSet(oldAssetList.keys)
        removedAssets.removeAll(tokenBalanceAssets.keys)
        changedAssetList.addAll(removedAssets)

        val web3j = getWeb3jService(tokenInfo.chainId)
        val balanceAssets = try {
            if (changedAssetList.isEmpty()) HashSet<BigInteger>() else checkBalances(web3j, changedAssetList)
        } catch (_: Exception) {
            HashSet()
        }

        for (tokenId in changedAssetList) {
            val asset = tokenBalanceAssets[tokenId] ?: oldAssetList[tokenId]
            if (asset != null) {
                asset.balance = if (balanceAssets.contains(tokenId)) BigDecimal.ZERO else BigDecimal.ONE
                updatedAssets[tokenId] = asset
            }
        }

        for (tokenId in unchangedAssets) {
            val asset = tokenBalanceAssets[tokenId]
            if (asset != null) {
                updatedAssets[tokenId] = asset
            }
        }

        return updatedAssets
    }

    private fun getContractCall(web3j: Web3j, function: Function, contractAddress: String?): Request<*, EthCall> {
        val encodedFunction = FunctionEncoder.encode(function)
        val transaction = createEthCallTransaction(getWallet(), contractAddress, encodedFunction)
        return web3j.ethCall(transaction, DefaultBlockParameterName.LATEST)
    }

    private fun ownerOf(token: BigInteger): Function {
        return Function(
            "ownerOf",
            listOf(Uint256(token)),
            listOf(object : TypeReference<Address>() {})
        )
    }

    private fun tokenOfOwnerByIndex(index: BigInteger): Function {
        return Function(
            "tokenOfOwnerByIndex",
            listOf(Address(getWallet()), Uint256(index)),
            listOf(object : TypeReference<Uint256>() {})
        )
    }

    override fun getStandardFunctions(): List<Int> = listOf(R.string.action_transfer)

    private fun tokenUsesLegacyTransfer(): Boolean {
        val legacyAddress = tokenInfo.address?.lowercase(Locale.ROOT) ?: return false
        return legacyContracts.contains(legacyAddress)
    }


    companion object {
        private val balanceChecks = ConcurrentHashMap<String, Boolean>()

        private val legacyContracts = setOf(
            "0x06012c8cf97bead5deae237070f9587f8e7a266d",
            "0xabc7e6c01237e8eef355bba2bf925a730b714d5f",
            "0x71c118b00759b0851785642541ceb0f4ceea0bd5",
            "0x16baf0de678e52367adc69fd067e5edd1d33e3bf",
            "0x7fdcd2a1e52f10c28cb7732f46393e297ecadda1"
        )
    }
}
