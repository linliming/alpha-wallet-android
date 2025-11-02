package com.alphawallet.app.entity.tokens


import android.util.Pair
import com.alphawallet.app.R
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.LogOverflowException
import com.alphawallet.app.entity.SyncDef
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.opensea.AssetContract
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.repository.EventResult
import com.alphawallet.app.repository.TokenRepository.Companion.callSmartContractFunctionArray
import com.alphawallet.app.repository.TokenRepository.Companion.getWeb3jServiceForEvents
import com.alphawallet.app.repository.entity.RealmNFTAsset
import com.alphawallet.app.repository.entity.RealmToken
import com.alphawallet.app.service.TransactionsService
import com.alphawallet.app.util.Utils.getIdMap
import com.alphawallet.app.util.Utils.parseTokenId
import io.realm.Realm
import org.web3j.abi.EventEncoder
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.EthLog
import org.web3j.protocol.core.methods.response.Log
import org.web3j.tx.Contract.staticExtractEventParameters
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap

class ERC1155Token(
    tokenInfo: TokenInfo,
    balanceList: Map<BigInteger, NFTAsset>?,
    blancaTime: Long,
    networkName: String,
) : Token(
    tokenInfo,
    if (balanceList != null) BigDecimal.valueOf(balanceList.keys.size.toLong()) else BigDecimal.ZERO,
    blancaTime,
    networkName,
    ContractType.ERC1155,
) {

    private val assets: MutableMap<BigInteger, NFTAsset> =
        if (balanceList != null) ConcurrentHashMap(balanceList) else ConcurrentHashMap()

    var assetContract: AssetContract? = null

    init {
        setInterfaceSpec(ContractType.ERC1155)
        group = TokenGroup.NFT
    }

    override fun getTokenAssets(): Map<BigInteger, NFTAsset> = assets

    override fun hasPositiveBalance(): Boolean = assets.values.any { it.balance > BigDecimal.ZERO }

    override fun getAssetForToken(tokenId: BigInteger): NFTAsset? = assets[tokenId]

    override fun getAssetForToken(tokenIdStr: String): NFTAsset? = assets[parseTokenId(tokenIdStr)]

    override fun isNonFungible(): Boolean = true

    override fun getContractType(): Int = R.string.erc1155

    override fun addAssetToTokenBalanceAssets(tokenId: BigInteger, asset: NFTAsset) {
        assets[tokenId] = asset
        balance = BigDecimal.valueOf(assets.keys.size.toLong())
    }

    override fun getCollectionMap(): Map<BigInteger, NFTAsset> {
        val collectionBuilder = HashMap<BigInteger, BigInteger>()
        val collectionMap = HashMap<BigInteger, NFTAsset>()

        for (tokenId in assets.keys) {
            val baseTokenId = getBaseTokenId(tokenId)
            if (baseTokenId > BigInteger.ZERO) {
                val asset = assets[tokenId] ?: continue
                val key = collectionBuilder[baseTokenId]
                val targetAsset = if (key == null) {
                    collectionBuilder[baseTokenId] = tokenId
                    NFTAsset(asset)
                } else {
                    collectionMap[key] ?: NFTAsset(asset)
                }
                targetAsset.addCollectionToken(tokenId)
                collectionMap[collectionBuilder[baseTokenId]!!] = targetAsset
            } else {
                collectionMap[tokenId] = assets[tokenId]!!
            }
        }

        return collectionMap
    }

    override fun getBalanceRaw(): BigDecimal {
        var total = BigDecimal.ZERO
        for (asset in assets.values) {
            total = total.add(asset.balance)
        }
        return total
    }

    override fun getStandardFunctions(): List<Int> = listOf(R.string.action_transfer)

    override fun convertValue(prefix: String, vResult: EventResult, precision: Int): String {
        val adjustedPrecision = precision + 1
        val value = vResult?.value ?: "0"
        return if (value.length > adjustedPrecision) {
            prefix + "1"
        } else {
            "#$value"
        }
    }

    override fun getTransactionResultValue(transaction: Transaction, precision: Int): String {
        return if (isEthereum() && !transaction.hasInput()) {
            "${getTransactionValue(transaction, precision)} ${getSymbol()}"
        } else if (transaction.hasInput()) {
            transaction.getOperationResult(this, precision)
        } else {
            ""
        }
    }

    override fun getTransferBytes(
        to: String,
        transferData: ArrayList<Pair<BigInteger, NFTAsset>>,
    ): ByteArray {
        val function = getTransferFunction(to, transferData)
        val encodedFunction = FunctionEncoder.encode(function)
        return Numeric.hexStringToByteArray(Numeric.cleanHexPrefix(encodedFunction))
    }

    override fun hasGroupedTransfer(): Boolean = true

    fun getTransferFunction(
        to: String,
        transferData: ArrayList<Pair<BigInteger, NFTAsset>>,
    ): Function {
        val returnTypes = emptyList<TypeReference<*>>()
        return if (transferData.size == 1) {
            val single = transferData[0]
            val params = listOf<Type<*>>(
                Address(getWallet()),
                Address(to),
                Uint256(single.first),
                Uint256(single.second.selectedBalance.toBigInteger()),
                DynamicBytes(ByteArray(0)),
            )
            Function("safeTransferFrom", params, returnTypes)
        } else {
            val idList = ArrayList<Uint256>(transferData.size)
            val amounts = ArrayList<Uint256>(transferData.size)
            transferData.forEach { entry ->
                idList.add(Uint256(entry.first))
                amounts.add(Uint256(entry.second.selectedBalance.toBigInteger()))
            }
            val params = listOf<Type<*>>(
                Address(getWallet()),
                Address(to),
                DynamicArray(Uint256::class.java, idList),
                DynamicArray(Uint256::class.java, amounts),
                DynamicBytes(ByteArray(0)),
            )
            Function("safeBatchTransferFrom", params, returnTypes)
        }
    }

    override fun getTransferFunction(to: String, tokenIds: List<BigInteger>): Function {
        val returnTypes = emptyList<TypeReference<*>>()
        val idMap = getIdMap(tokenIds)
        return if (idMap.keys.size == 1) {
            val tokenId = tokenIds.first()
            val params = listOf<Type<*>>(
                Address(getWallet()),
                Address(to),
                Uint256(tokenId),
                Uint256(idMap[tokenId]),
                DynamicBytes(ByteArray(0)),
            )
            Function("safeTransferFrom", params, returnTypes)
        } else {
            val idList = ArrayList<Uint256>(idMap.keys.size)
            val amounts = ArrayList<Uint256>(idMap.keys.size)
            idMap.forEach { (tokenId, amount) ->
                idList.add(Uint256(tokenId))
                amounts.add(Uint256(amount))
            }
            val params = listOf<Type<*>>(
                Address(getWallet()),
                Address(to),
                DynamicArray(Uint256::class.java, idList),
                DynamicArray(Uint256::class.java, amounts),
                DynamicBytes(ByteArray(0)),
            )
            Function("safeBatchTransferFrom", params, returnTypes)
        }
    }

    override fun getAssetChange(oldAssetList: Map<BigInteger, NFTAsset>): Map<BigInteger, NFTAsset> {
        if (assetsUnchanged(oldAssetList)) return assets

        val combined = HashMap<BigInteger, NFTAsset>(oldAssetList)
        combined.putAll(assets)
        val tokenIds = combined.keys.toSet()
        val tokenIdList = tokenIds.toList()
        val balanceOfBatch = balanceOfBatch(getWallet(), tokenIdList)
        val balanceResponse = callSmartContractFunctionArray(
            tokenInfo.chainId,
            balanceOfBatch,
            getAddress(),
            getWallet(),
        )

        val updatedAssetMap = HashMap<BigInteger, NFTAsset>()
        when {
            balanceResponse == null -> {
                return assets
            }
            balanceResponse.isEmpty() -> {
                combined.forEach { (tokenId, asset) ->
                    val newAsset = NFTAsset(asset)
                    newAsset.balance = BigDecimal.ZERO
                    updatedAssetMap[tokenId] = newAsset
                }
            }
            else -> {
                val balances = balanceResponse.mapNotNull { element ->
                    when (element) {
                        is BigInteger -> element
                        is Uint256 -> element.value
                        is Number -> BigInteger.valueOf(element.toLong())
                        else -> null
                    }
                }
                var index = 0
                combined.forEach { (tokenId, asset) ->
                    val newAsset = NFTAsset(asset)
                    val balanceValue = balances.getOrNull(index) ?: BigInteger.ZERO
                    newAsset.balance = BigDecimal(balanceValue)
                    updatedAssetMap[tokenId] = newAsset
                    index++
                }
            }
        }

        return updatedAssetMap
    }

    private fun fetchBalances(tokenIds: List<BigInteger>): MutableList<BigInteger>? {
        val balanceOfBatch = balanceOfBatch(getWallet(), tokenIds)
        val response = callSmartContractFunctionArray(
            tokenInfo.chainId,
            balanceOfBatch,
            getAddress(),
            getWallet(),
        )

        return when {
            response == null -> null
            response.isEmpty() -> MutableList(tokenIds.size) { BigInteger.ZERO }
            else -> response.mapNotNull { element ->
                when (element) {
                    is BigInteger -> element
                    is Uint256 -> element.value
                    is Number -> BigInteger.valueOf(element.toLong())
                    else -> BigInteger.ZERO
                }
            }.toMutableList()
        }
    }

    override fun queryAssets(assetMap: Map<BigInteger, NFTAsset>): Map<BigInteger, NFTAsset> {
        val tokenIdList = assetMap.keys.toList()
        val balanceOfBatch = balanceOfBatch(getWallet(), tokenIdList)
        val response = callSmartContractFunctionArray(
            tokenInfo.chainId,
            balanceOfBatch,
            getAddress(),
            getWallet(),
        )

        val updatedAssets = HashMap<BigInteger, NFTAsset>()
        when {
            response == null -> return assetMap
            response.isEmpty() -> {
                assetMap.forEach { (tokenId, asset) ->
                    val newAsset = NFTAsset(asset)
                    newAsset.balance = BigDecimal.ZERO
                    updatedAssets[tokenId] = newAsset
                }
            }
            else -> {
                val balances = response.mapNotNull { element ->
                    when (element) {
                        is BigInteger -> element
                        is Uint256 -> element.value
                        is Number -> BigInteger.valueOf(element.toLong())
                        else -> BigInteger.ZERO
                    }
                }
                var index = 0
                assetMap.forEach { (tokenId, asset) ->
                    val newAsset = NFTAsset(asset)
                    val balanceValue = balances.getOrNull(index) ?: BigInteger.ZERO
                    newAsset.balance = BigDecimal(balanceValue)
                    updatedAssets[tokenId] = newAsset
                    index++
                }
            }
        }

        return updatedAssets
    }

    private fun assetsUnchanged(assetMap: Map<BigInteger, NFTAsset>): Boolean {
        for (tokenId in assetMap.keys) {
            if (!assets.containsKey(tokenId)) return false
        }
        for (tokenId in assets.keys) {
            if (!assetMap.containsKey(tokenId)) return false
        }
        return true
    }

    private fun updateRealmBalance(
        realm: Realm?,
        tokenIds: List<BigInteger>,
        balances: List<BigInteger>?,
    ) {
        var updated = false
        if (!balances.isNullOrEmpty()) {
            tokenIds.forEachIndexed { index, tokenId ->
                val balanceValue = BigDecimal(balances.getOrNull(index) ?: BigInteger.ZERO)
                val asset = assets[tokenId]
                if (asset == null) {
                    val newAsset = NFTAsset(tokenId)
                    newAsset.balance = balanceValue
                    assets[tokenId] = newAsset
                    updated = true
                } else if (asset.setBalance(balanceValue)) {
                    updated = true
                }

                if (realm == null && balanceValue.compareTo(BigDecimal.ZERO) == 0) {
                    assets.remove(tokenId)
                }
            }
            if (updated) {
                updateRealmBalances(realm)
            }
        }
    }

    private fun updateRealmBalances(realm: Realm?) {
        if (realm == null) return
        realm.executeTransaction { r ->
            val entries = ArrayList(assets.entries) // avoid concurrent modification
            for ((tokenId, asset) in entries) {
                val key = RealmNFTAsset.databaseKey(this, tokenId)
                var realmAsset = r.where(RealmNFTAsset::class.java)
                    .equalTo("tokenIdAddr", key)
                    .findFirst()

                if (realmAsset == null && asset.balance == BigDecimal.ZERO) continue

                if (realmAsset == null) {
                    realmAsset = r.createObject(RealmNFTAsset::class.java, key)
                    realmAsset.metaData = asset.jsonMetaData()
                }

                if (asset.balance == BigDecimal.ZERO) {
                    realmAsset?.deleteFromRealm()
                    assets.remove(tokenId)
                } else {
                    realmAsset?.setBalance(asset.balance)
                    r.insertOrUpdate(realmAsset)
                }
            }
        }
    }

    override fun checkRealmBalanceChange(realmToken: RealmToken): Boolean {
        val currentState = realmToken.balance ?: return true
        if (currentState != getBalanceRaw().toString()) return true
        for (asset in assets.values) {
            if (!asset.needsLoading() && !asset.requiresReplacement()) {
                return true
            }
        }
        return false
    }

    override fun checkBalanceChange(oldToken: Token): Boolean {
        if (super.checkBalanceChange(oldToken)) return true
        if (getTokenAssets().size != oldToken.getTokenAssets().size) return true
        for ((tokenId, asset) in assets) {
            val oldAsset = oldToken.getAssetForToken(tokenId)
            if (asset == null || oldAsset == null || asset != oldAsset) {
                return true
            }
        }
        return false
    }

    override fun updateBalance(realm: Realm): BigDecimal {
        val sync: SyncDef = eventSync.getSyncDef(realm) ?: return balance
        val startBlock: DefaultBlockParameter = DefaultBlockParameter.valueOf(sync.eventReadStartBlock)
        var endBlock: DefaultBlockParameter =
            DefaultBlockParameter.valueOf(sync.eventReadEndBlock)
        if (sync.eventReadEndBlock == BigInteger.valueOf(-1L)) {
            endBlock = DefaultBlockParameterName.LATEST
        }

        val currentBlock = TransactionsService.getCurrentBlock(tokenInfo.chainId)

        try {
            val web3j = getWeb3jServiceForEvents(tokenInfo.chainId)
            val singleRead = eventSync.processTransferEvents(
                web3j,
                balanceUpdateEvent,
                startBlock,
                endBlock,
                realm,
            )
            val batchRead = eventSync.processTransferEvents(
                web3j,
                batchBalanceUpdateEvent,
                startBlock,
                endBlock,
                realm,
            )

            val allTokenIds = HashSet<BigInteger>()
            allTokenIds.addAll(singleRead.second.first)
            allTokenIds.addAll(singleRead.second.second)
            allTokenIds.addAll(batchRead.second.first)
            allTokenIds.addAll(batchRead.second.second)
            allTokenIds.addAll(assets.keys)

            val orderedTokenIds = allTokenIds.toList()
            val balances = fetchBalances(orderedTokenIds)
            updateRealmBalance(realm, orderedTokenIds, balances)
            balance = getBalanceRaw()

            eventSync.updateEventReads(realm, sync, currentBlock, singleRead.first)
        } catch (e: LogOverflowException) {
            if (eventSync.handleEthLogError(e.error, startBlock, endBlock, sync, realm)) {
                updateBalance(realm)
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        return balance
    }

    override fun processLogsAndStoreTransferEvents(
        receiveLogs: EthLog,
        event: Event,
        txHashes: HashSet<String>,
        realm: Realm,
    ): HashSet<BigInteger> {
        val tokenIds = HashSet<BigInteger>()
        for (ethLog in receiveLogs.logs) {
            val log = ethLog.get() as Log
            val block = log.blockNumberRaw
            if (block.isNullOrEmpty()) continue
            val txHash = log.transactionHash
            val eventValues = staticExtractEventParameters(event, log)
            val idResult = eventSync.getEventIdResult(
                eventValues.nonIndexedValues[0],
                eventValues.nonIndexedValues[1],
            )
            tokenIds.addAll(idResult.first)
            val from = eventValues.indexedValues[1].value.toString()
            val to = eventValues.indexedValues[2].value.toString()
            eventSync.storeTransferData(realm, from, to, idResult, txHash)
            txHashes.add(txHash)
        }
        return tokenIds
    }

    override fun getReceiveBalanceFilter(
        event: Event,
        startBlock: DefaultBlockParameter,
        endBlock: DefaultBlockParameter,
    ): EthFilter {
        val filter = EthFilter(startBlock, endBlock, tokenInfo.address)
            .addSingleTopic(EventEncoder.encode(event))
        filter.addSingleTopic(null)
        filter.addSingleTopic(null)
        filter.addSingleTopic(
            Numeric.prependHexPrefix(
                TypeEncoder.encode(Address(getWallet())),
            ),
        )
        return filter
    }

    override fun getSendBalanceFilter(
        event: Event,
        startBlock: DefaultBlockParameter,
        endBlock: DefaultBlockParameter,
    ): EthFilter {
        val filter = EthFilter(startBlock, endBlock, tokenInfo.address)
            .addSingleTopic(EventEncoder.encode(event))
        filter.addSingleTopic(null)
        filter.addSingleTopic(
            Numeric.prependHexPrefix(
                TypeEncoder.encode(Address(getWallet())),
            ),
        )
        filter.addSingleTopic(null)
        return filter
    }

    private val balanceUpdateEvent: Event
        get() {
            val paramList = ArrayList<TypeReference<*>>()
            paramList.add(object : TypeReference<Address>(true) {})
            paramList.add(object : TypeReference<Address>(true) {})
            paramList.add(object : TypeReference<Address>(true) {})
            paramList.add(object : TypeReference<Uint256>(false) {})
            paramList.add(object : TypeReference<Uint256>(false) {})
            return Event("TransferSingle", paramList)
        }

    private val batchBalanceUpdateEvent: Event
        get() {
            val paramList = ArrayList<TypeReference<*>>()
            paramList.add(object : TypeReference<Address>(true) {})
            paramList.add(object : TypeReference<Address>(true) {})
            paramList.add(object : TypeReference<Address>(true) {})
            paramList.add(object : TypeReference<DynamicArray<Uint256>>(false) {})
            paramList.add(object : TypeReference<DynamicArray<Uint256>>(false) {})
            return Event("TransferBatch", paramList)
        }

    private fun balanceOfBatch(address: String, tokenIds: List<BigInteger>): Function {
        val addressList = tokenIds.map { Address(address) }
        val idList = tokenIds.map { Uint256(it) }
        return Function(
            "balanceOfBatch",
            listOf(
                DynamicArray(Address::class.java, addressList),
                DynamicArray(Uint256::class.java, idList),
            ),
            listOf(object : TypeReference<DynamicArray<Uint256>>() {}),
        )
    }

    private fun balanceOfSingle(address: String, tokenId: BigInteger): Function {
        return Function(
            "balanceOf",
            listOf(Address(address), Uint256(tokenId)),
            listOf(object : TypeReference<Uint256>() {}),
        )
    }

    override fun getFirstImageUrl(): String {
        val asset = assets.values.firstOrNull() ?: return ""
        return if (asset.hasImageAsset()) asset.thumbnail else ""
    }

    override fun isBatchTransferAvailable(): Boolean = true

    companion object {
        fun getBaseTokenId(tokenId: BigInteger): BigInteger {
            return tokenId.shiftRight(96)
        }

        fun getNFTTokenId(tokenId: BigInteger): BigInteger {
            return tokenId.and(Numeric.toBigInt("0xFFFFFFFFFF"))
        }

        fun isNFT(tokenId: BigInteger): Boolean {
            return getBaseTokenId(tokenId) > BigInteger.ZERO &&
                getNFTTokenId(tokenId) < BigInteger.valueOf(0xFFFF) &&
                getNFTTokenId(tokenId) > BigInteger.ZERO
        }
    }
}
