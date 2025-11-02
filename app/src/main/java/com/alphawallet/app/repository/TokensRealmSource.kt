package com.alphawallet.app.repository


import android.util.Pair
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.ImageEntry
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.opensea.AssetContract
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.entity.tokendata.TokenTicker
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.entity.tokens.TokenFactory
import com.alphawallet.app.entity.tokens.TokenInfo
import com.alphawallet.app.repository.entity.RealmAttestation
import com.alphawallet.app.repository.entity.RealmAuxData
import com.alphawallet.app.repository.entity.RealmNFTAsset
import com.alphawallet.app.repository.entity.RealmToken
import com.alphawallet.app.repository.entity.RealmTokenTicker
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.service.RealmManager
import com.alphawallet.app.service.TickerService
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.util.Utils
import com.alphawallet.token.entity.ContractAddress
import io.realm.Case
import io.realm.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TokensRealmSource - 代币Realm数据源管理类
 *
 * 这是AlphaWallet中负责代币数据本地存储的核心组件，使用Realm数据库进行数据持久化。
 * 主要功能包括：
 * 1. 代币数据的增删改查操作
 * 2. NFT资产管理
 * 3. 代币价格信息存储
 * 4. 认证代币管理
 * 5. 代币图片URL管理
 * 6. 自定义代币设置管理
 *
 * 使用Kotlin协程替代RxJava，提供更好的异步处理性能和可读性。
 *
 * @param realmManager Realm数据库管理器
 * @param ethereumNetworkRepository 以太坊网络仓库
 * @param tokensMappingRepository 代币映射仓库
 *
 * @author AlphaWallet Team
 * @since 2024
 */
@Singleton
@Suppress("TooManyFunctions", "LargeClass")
class TokensRealmSource
    @Inject
    constructor(
        private val realmManager: RealmManager,
        private val ethereumNetworkRepository: EthereumNetworkRepositoryType,
        private val tokensMappingRepository: TokensMappingRepositoryType,
    ) : TokenLocalSource {
        companion object {
            /**
             * 日志标签
             */
            const val TAG = "TLS"

            /**
             * 图片数据库名称
             */
            const val IMAGES_DB = "image_urls_db"

            /**
             * 认证代币数据库名称
             */
            const val ATOKENS_DB = "a_tokens_db"

            /**
             * 价格数据库名称
             */
            const val TICKER_DB = "tickers_db"

            /**
             * 地址格式模式
             */
            const val ADDRESS_FORMAT = "0x????????????????????????????????????????-*"

            /**
             * 事件卡片后缀
             */
            const val EVENT_CARDS = "-eventName"

            /**
             * 生成数据库键值
             *
             * @param chainId 链ID
             * @param address 代币地址
             * @return 数据库键值
             */
            @JvmStatic
            fun databaseKey(
                chainId: Long,
                address: String,
            ): String = "${address.lowercase()}-$chainId"

            /**
             * 生成代币数据库键值
             *
             * @param token 代币对象
             * @return 数据库键值
             */
            @JvmStatic
            fun databaseKey(token: Token): String =
                databaseKey(token.tokenInfo.chainId, token.tokenInfo.address.orEmpty().lowercase())

            /**
             * 生成事件活动键值
             *
             * @param txHash 交易哈希
             * @param activityName 活动名称
             * @return 事件活动键值
             */
            @JvmStatic
            fun eventActivityKey(
                txHash: String,
                activityName: String,
            ): String = "$txHash-$activityName$EVENT_CARDS"

            /**
             * 生成扩展事件活动键值
             *
             * @param txHash 交易哈希
             * @param activityName 活动名称
             * @param extendedId 扩展ID
             * @return 扩展事件活动键值
             */
            @JvmStatic
            fun eventActivityKey(
                txHash: String,
                activityName: String,
                extendedId: Int,
            ): String = "$txHash-$activityName$EVENT_CARDS-$extendedId"

            /**
             * 生成事件区块键值
             *
             * @param chainId 链ID
             * @param eventAddress 事件地址
             * @param namedType 命名类型
             * @param filter 过滤器
             * @return 事件区块键值
             */
            @JvmStatic
            fun eventBlockKey(
                chainId: Long,
                eventAddress: String,
                namedType: String,
                filter: String,
            ): String = "${eventAddress.lowercase()}-$chainId-$namedType-$filter-eventBlock"

            /**
             * 生成认证数据库键值
             *
             * @param chainId 链ID
             * @param address 地址
             * @param attnId 认证ID
             * @return 认证数据库键值
             */
            @JvmStatic
            fun attestationDatabaseKey(
                chainId: Long,
                address: String,
                attnId: String,
            ): String = "${address.lowercase()}-$chainId-$attnId"

            /**
             * 生成认证数据库键值（会议和票据）
             *
             * @param chainId 链ID
             * @param address 地址
             * @param conferenceId 会议ID
             * @param ticketId 票据ID
             * @return 认证数据库键值
             */
            @JvmStatic
            fun attestationDatabaseKey(
                chainId: Long,
                address: String,
                conferenceId: BigInteger,
                ticketId: BigInteger,
            ): String = "${address.lowercase()}-$chainId-$conferenceId-$ticketId"

            /**
             * 转换字符串余额
             *
             * 根据合约类型转换余额字符串格式
             *
             * @param balance 余额字符串
             * @param type 合约类型
             * @return 转换后的余额字符串
             */
            @JvmStatic
            fun convertStringBalance(
                balance: String?,
                type: ContractType,
            ): String {
                if (balance.isNullOrEmpty() || balance == "0") {
                    return "0"
                }

                return when (type) {
                    ContractType.ERC721_TICKET,
                    ContractType.ERC875_LEGACY,
                    ContractType.ERC875,
                    -> zeroOrBalance(balance)
                    else -> balance
                }
            }

            /**
             * 检查余额是否为零或返回原余额
             *
             * @param balance 余额字符串
             * @return 处理后的余额字符串
             */
            private fun zeroOrBalance(balance: String): String {
                val ids = balance.split(",")

                for (id in ids) {
                    val trim = id.trim()
                    if (trim != "0") return balance
                }

                return "0"
            }
        }

        // 协程作用域
        private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        /**
         * 保存代币数组
         *
         * 将代币数组保存到本地数据库
         *
         * @param wallet 钱包信息
         * @param items 代币数组
         * @return 保存后的代币数组
         */
        override suspend fun saveTokens(
            wallet: Wallet?,
            items: Array<Token?>?,
        ): Array<Token?>? =
            withContext(Dispatchers.IO) {
                if (wallet == null || !Utils.isAddressValid(wallet.address)) {
                    return@withContext items
                }

                try {
                    realmManager.getRealmInstance(wallet).use { realm ->
                        realm.executeTransaction { r ->
                            items?.forEach { token ->
                                if (token?.tokenInfo?.name != null &&
                                    token.tokenInfo.name != TokensService.EXPIRED_CONTRACT &&
                                    token.tokenInfo.symbol != null
                                ) {
                                    saveTokenLocal(r, token)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "保存代币时发生错误")
                }

                items
            }

        /**
         * 存储代币URL
         *
         * @param entries 图片条目列表
         */
        override fun storeTokenUrl(entries: List<ImageEntry?>?) {
            if (entries == null) return

            try {
                realmManager.getRealmInstance(IMAGES_DB).use { realm ->
                    realm.executeTransaction { r ->
                        entries.forEach { entry ->
                            entry?.let {
                                val key = "${it.address.lowercase()}-${it.chainId}"
                                val realmData =
                                    r
                                        .where(RealmAuxData::class.java)
                                        .equalTo("instanceKey", key)
                                        .findFirst() ?: r.createObject(RealmAuxData::class.java, key)

                                realmData.result = it.imageUrl
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "存储代币URL时发生错误")
            }
        }

        /**
         * 删除Realm代币
         *
         * 从数据库中删除指定的代币元数据列表
         *
         * @param wallet 钱包信息
         * @param tcmList 代币卡片元数据列表
         */
        override fun deleteRealmTokens(
            wallet: Wallet?,
            tcmList: List<TokenCardMeta?>?,
        ) {
            if (wallet == null || tcmList == null) return

            try {
                realmManager.getRealmInstance(wallet).use { realm ->
                    realm.executeTransaction { r ->
                        tcmList.forEach { tcm ->
                            tcm?.let {
                                val dbKey = databaseKey(it.chain, it.address)
                                val realmToken =
                                    r
                                        .where(RealmToken::class.java)
                                        .equalTo("address", dbKey)
                                        .findFirst()

                                realmToken?.deleteFromRealm()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "删除代币时发生错误")
            }
        }

        /**
         * 获取Realm实例
         *
         * @param wallet 钱包信息
         * @return Realm数据库实例
         */
        override fun getRealmInstance(wallet: Wallet?): Realm? = wallet?.let { realmManager.getRealmInstance(it) }

        /**
         * 获取价格Realm实例
         *
         * @return 价格数据库实例
         */
        override val tickerRealmInstance: Realm?
            get() = realmManager.getRealmInstance(TICKER_DB)

        /**
         * 保存单个代币
         *
         * @param wallet 钱包信息
         * @param token 代币对象
         * @return 保存后的代币对象
         */
        override suspend fun saveToken(
            wallet: Wallet?,
            token: Token?,
        ): Token? =
            withContext(Dispatchers.IO) {
                if (wallet == null || token == null) {
                    return@withContext token
                }

                try {
                    realmManager.getRealmInstance(wallet).use { realm ->
                        realm.executeTransaction { r ->
                            saveTokenLocal(r, token)
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "保存单个代币时发生错误")
                }
                token
            }

        /**
         * 更新代币余额
         *
         * @param wallet 钱包信息
         * @param token 代币对象
         * @param balance 余额
         * @param balanceArray 余额数组
         * @return 更新是否成功
         */
        override fun updateTokenBalance(
            wallet: Wallet?,
            token: Token?,
            balance: BigDecimal?,
            balanceArray: List<BigInteger?>?,
        ): Boolean {
            if (wallet == null || token == null) return false

            return try {
                realmManager.getRealmInstance(wallet).use { realm ->
                    realm.executeTransaction { r ->
                        val dbKey = databaseKey(token)
                        val realmToken =
                            r
                                .where(RealmToken::class.java)
                                .equalTo("address", dbKey)
                                .findFirst()

                        realmToken?.let {
                            it.balance = convertStringBalance(balance?.toString() ?: "0", token.getInterfaceSpec())
                        }
                    }
                }
                true
            } catch (e: Exception) {
                Timber.w(e, "更新代币余额时发生错误")
                false
            }
        }

        /**
         * 获取代币图片URL
         *
         * @param chainId 链ID
         * @param address 代币地址
         * @return 图片URL字符串
         */
        override fun getTokenImageUrl(
            chainId: Long,
            address: String?,
        ): String? {
            if (address == null) return null

            val instanceKey = "${address.lowercase()}-$chainId"

            return try {
                realmManager.getRealmInstance(IMAGES_DB).use { realm ->
                    val instance =
                        realm
                            .where(RealmAuxData::class.java)
                            .equalTo("instanceKey", instanceKey)
                            .findFirst()

                    instance?.result
                }
            } catch (e: Exception) {
                Timber.w(e, "获取代币图片URL时发生错误")
                null
            }
        }

        /**
         * 获取代币
         *
         * @param chainId 链ID
         * @param wallet 钱包信息
         * @param address 代币地址
         * @return 代币对象，如果不存在则返回null
         */
        override fun fetchToken(
            chainId: Long,
            wallet: Wallet?,
            address: String?,
        ): Token? {
            return try {
                if (wallet == null || address == null) return null

                realmManager.getRealmInstance(wallet).use { realm ->
                    val realmItem =
                        realm
                            .where(RealmToken::class.java)
                            .equalTo("address", databaseKey(chainId, address))
                            .findFirst()

                    var token = convertSingle(realmItem, realm, null, wallet)

                    // 如果代币不存在且地址是钱包地址，创建以太坊基础代币
                    if (token == null && address.equals(wallet.address, ignoreCase = true)) {
                        val info = ethereumNetworkRepository.getNetworkByChain(chainId)
                        if (info != null) {
                            val tokenInfo =
                                TokenInfo(
                                    wallet.address,
                                    info.name,
                                    info.symbol,
                                    18,
                                    true,
                                    chainId,
                                )
                            val tokenFactory = TokenFactory()
                            token =
                                tokenFactory.createToken(
                                    tokenInfo,
                                    BigDecimal.ZERO,
                                    null,
                                    0,
                                    ContractType.ETHEREUM,
                                    info.shortName,
                                    0,
                                )
                        }
                    }

                    token
                }
            } catch (e: Exception) {
                Timber.w(e, "获取代币时发生错误")
                null
            }
        }

        /**
         * 获取认证代币
         *
         * @param chainId 链ID
         * @param wallet 钱包信息
         * @param address 代币地址
         * @param attnId 认证ID
         * @return 认证代币对象
         */
        override fun fetchAttestation(
            chainId: Long,
            wallet: Wallet?,
            address: String?,
            attnId: String?,
        ): Token? {
            return try {
                if (address == null || attnId == null || wallet == null) return null

                realmManager.getRealmInstance(ATOKENS_DB).use { realm ->
                    val key = attestationDatabaseKey(chainId, address, attnId)
                    val realmItem =
                        realm
                            .where(RealmAttestation::class.java)
                            .equalTo("address", key)
                            .findFirst()

                    realmItem?.let { convertAttestation(it, wallet) }
                }
            } catch (e: Exception) {
                Timber.w(e, "获取认证代币时发生错误")
                null
            }
        }

        /**
         * 获取认证代币列表
         *
         * @param chainId 链ID
         * @param walletAddress 钱包地址
         * @param tokenAddress 代币地址
         * @return 认证代币列表
         */
        override fun fetchAttestations(
            chainId: Long,
            walletAddress: String?,
            tokenAddress: String?,
        ): List<Token?>? {
            if (walletAddress == null || tokenAddress == null) return null

            val tokens = mutableListOf<Token?>()

            try {
                realmManager.getRealmInstance(ATOKENS_DB).use { realm ->
                    val attestations =
                        realm
                            .where(RealmAttestation::class.java)
                            .beginsWith("address", "$tokenAddress-$chainId", Case.INSENSITIVE)
                            .findAll()

                    val wallet = Wallet(walletAddress)
                    for (attestation in attestations) {
                        convertAttestation(attestation, wallet)?.let { tokens.add(it) }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "获取认证代币列表时发生错误")
            }

            return tokens
        }

        /**
         * 获取认证代币列表（重载方法）
         *
         * @param walletAddress 钱包地址
         * @return 认证代币列表
         */
        override fun fetchAttestations(walletAddress: String?): List<Token?>? {
            if (walletAddress == null) return null

            val tokens = mutableListOf<Token?>()

            try {
                realmManager.getRealmInstance(ATOKENS_DB).use { realm ->
                    val attestations =
                        realm
                            .where(RealmAttestation::class.java)
                            .findAll()

                    val wallet = Wallet(walletAddress)
                    for (attestation in attestations) {
                        convertAttestation(attestation, wallet)?.let { tokens.add(it) }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "获取认证代币列表时发生错误")
            }

            return tokens
        }

        /**
         * 设置代币启用状态
         *
         * @param wallet 钱包信息
         * @param cAddr 合约地址
         * @param isEnabled 是否启用
         */
        override fun setEnable(
            wallet: Wallet?,
            cAddr: ContractAddress?,
            isEnabled: Boolean,
        ) {
            if (wallet == null || cAddr == null) return

            try {
                realmManager.getRealmInstance(wallet).use { realm ->
                    realm.executeTransaction { r ->
                        val dbKey = databaseKey(cAddr.chainId, cAddr.address)
                        val realmToken =
                            r
                                .where(RealmToken::class.java)
                                .equalTo("address", dbKey)
                                .findFirst()

                        realmToken?.isEnabled = isEnabled
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "设置代币启用状态时发生错误")
            }
        }

        /**
         * 获取代币启用状态
         *
         * @param token 代币对象
         * @return 是否启用
         */
        override fun getEnabled(token: Token?): Boolean {
            return try {
                if (token == null) return false

                val wallet = Wallet(token.getWallet() ?: "")
                realmManager.getRealmInstance(wallet).use { realm ->
                    val dbKey = databaseKey(token)
                    val realmToken =
                        realm
                            .where(RealmToken::class.java)
                            .equalTo("address", dbKey)
                            .findFirst()

                    realmToken?.isEnabled ?: token.tokenInfo.isEnabled
                }
            } catch (e: Exception) {
                Timber.w(e, "获取代币启用状态时发生错误")
                token?.tokenInfo?.isEnabled ?: false
            }
        }

        /**
         * 存储NFT资产
         *
         * @param wallet 钱包地址
         * @param token 代币对象
         * @param tokenId 代币ID
         * @param asset NFT资产
         */
        override fun storeAsset(
            wallet: String?,
            token: Token?,
            tokenId: BigInteger?,
            asset: NFTAsset?,
        ) {
            if (token == null || tokenId == null || asset == null) return

            try {
                realmManager.getRealmInstance(IMAGES_DB).use { realm ->
                    realm.executeTransaction { r ->
                        val key = "${token.tokenInfo.address.orEmpty().lowercase()}-${token.tokenInfo.chainId}-$tokenId"

                        val realmAsset =
                            r
                                .where(RealmNFTAsset::class.java)
                                .equalTo("tokenIdAddr", key)
                                .findFirst() ?: r.createObject(RealmNFTAsset::class.java, key)

                        realmAsset.apply {
                            metaData = asset.jsonMetaData()
                            setBalance(asset.balance)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "存储NFT资产时发生错误")
            }
        }

        /**
         * 获取当前代币价格信息
         *
         * @param token 代币对象
         * @return 代币价格信息
         */
        override fun getCurrentTicker(token: Token?): TokenTicker? {
            if (token?.tokenInfo == null) return null

            return try {
                realmManager.getRealmInstance(TICKER_DB).use { realm ->
                    val key = "${token.tokenInfo.address.orEmpty().lowercase()}-${token.tokenInfo.chainId}"
                    val realmTicker =
                        realm
                            .where(RealmTokenTicker::class.java)
                            .equalTo("contract", key)
                            .findFirst()

                    realmTicker?.let {
                        val currencySymbol = it.currencySymbol ?: "USD"
                        val priceString = it.price
                        val percentChange24h = it.percentChange24h
                        val currentTime = System.currentTimeMillis()

                        // 检查价格是否过期
                        val isStale = currentTime > (it.updatedTime + TickerService.TICKER_TIMEOUT)
                        val isVeryStale = currentTime > (it.updatedTime + TickerService.TICKER_STALE_TIMEOUT)

                        if (!isVeryStale && priceString != null) {
                            TokenTicker(
                                priceString,
                                percentChange24h,
                                currencySymbol,
                                "",
                                it.updatedTime,
                            )
                        } else {
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "获取代币价格信息时发生错误")
                null
            }
        }

        /**
         * 获取当前代币价格信息（重载方法）
         *
         * @param key 代币键值
         * @return 代币价格信息
         */
        override fun getCurrentTicker(key: String?): TokenTicker? {
            if (key == null) return null

            return try {
                realmManager.getRealmInstance(TICKER_DB).use { realm ->
                    val realmTicker =
                        realm
                            .where(RealmTokenTicker::class.java)
                            .equalTo("contract", key)
                            .findFirst()

                    realmTicker?.let {
                        val currencySymbol = it.currencySymbol ?: "USD"
                        val priceString = it.price
                        val percentChange24h = it.percentChange24h
                        val currentTime = System.currentTimeMillis()

                        // 检查价格是否过期
                        val isStale = currentTime > (it.updatedTime + TickerService.TICKER_TIMEOUT)
                        val isVeryStale = currentTime > (it.updatedTime + TickerService.TICKER_STALE_TIMEOUT)

                        if (!isVeryStale && priceString != null) {
                            TokenTicker(
                                priceString,
                                percentChange24h,
                                currencySymbol,
                                "",
                                it.updatedTime,
                            )
                        } else {
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "获取代币价格信息时发生错误")
                null
            }
        }

        /**
         * 获取代币分组
         *
         * @param chainId 链ID
         * @param address 代币地址
         * @param type 合约类型
         * @return 代币分组信息
         */
        override fun getTokenGroup(
            chainId: Long,
            address: String?,
            type: ContractType?,
        ): TokenGroup =
            try {
                when (type) {
                    ContractType.ATTESTATION -> TokenGroup.ATTESTATION
                    ContractType.ERC721,
                    ContractType.ERC721_ENUMERABLE,
                    ContractType.ERC721_LEGACY,
                    ContractType.ERC721_TICKET,
                    ContractType.ERC721_UNDETERMINED,
                    ContractType.ERC875,
                    ContractType.ERC875_LEGACY,
                    ContractType.ERC1155,
                    -> TokenGroup.NFT
                    else ->
                        tokensMappingRepository.getTokenGroup(chainId, address, type)
                            ?: fallbackTokenGroup(type)
                }
            } catch (e: Exception) {
                Timber.w(e, "获取代币分组时发生错误")
                fallbackTokenGroup(type)
            }

        private fun fallbackTokenGroup(type: ContractType?): TokenGroup =
            when (type) {
                ContractType.ATTESTATION -> TokenGroup.ATTESTATION
                ContractType.ERC721,
                ContractType.ERC721_ENUMERABLE,
                ContractType.ERC721_LEGACY,
                ContractType.ERC721_TICKET,
                ContractType.ERC721_UNDETERMINED,
                ContractType.ERC875,
                ContractType.ERC875_LEGACY,
                ContractType.ERC1155,
                -> TokenGroup.NFT
                else -> TokenGroup.ASSET
            }

        /**
         * 初始化NFT资产
         *
         * @param wallet 钱包信息
         * @param tokens 代币对象
         * @return 处理后的代币对象
         */
        override fun initNFTAssets(
            wallet: Wallet?,
            tokens: Token?,
        ): Token? {
            // 这里应该实现NFT资产的初始化逻辑
            // 暂时返回原代币对象
            return tokens
        }

        /**
         * 获取代币元数据
         *
         * @param wallet 钱包信息
         * @param networkFilters 网络过滤器
         * @param svs 资产定义服务
         * @return 代币卡片元数据数组
         */
        override suspend fun fetchTokenMetas(
            wallet: Wallet?,
            networkFilters: List<Long?>?,
            svs: AssetDefinitionService?,
        ): Array<TokenCardMeta?>? =
            withContext(Dispatchers.IO) {
                if (wallet == null) return@withContext null

                try {
                    realmManager.getRealmInstance(wallet).use { realm ->
                        val tokenMetas = mutableListOf<TokenCardMeta?>()

                        realm.executeTransaction { r ->
                            val realmTokens =
                                r
                                    .where(RealmToken::class.java)
                                    .findAll()
                                    .filter { token ->
                                        networkFilters.isNullOrEmpty() ||
                                            networkFilters.contains(token.chainId)
                                    }

                            for (realmToken in realmTokens) {
                                realmToken?.let { token ->
                                    if (token.isEnabled) {
                                        val tokenGroup = getTokenGroup(token.chainId, token.tokenAddress, token.getContractType())
                                        val meta =
                                            TokenCardMeta(
                                                token.chainId,
                                                token.tokenAddress,
                                                token.balance ?: "0",
                                                token.getUpdateTime(),
                                                token.lastTxTime,
                                                token.getContractType(),
                                                tokenGroup,
                                            )
                                        meta.isEnabled = token.isEnabled
                                        tokenMetas.add(meta)
                                    }
                                }
                            }
                        }

                        tokenMetas.toTypedArray()
                    }
                } catch (e: Exception) {
                    Timber.w(e)
                    null
                }
            }

        /**
         * 获取所有代币元数据
         *
         * @param wallet 钱包信息
         * @param networkFilters 网络过滤器
         * @param searchTerm 搜索词
         * @return 代币卡片元数据数组
         */
        override suspend fun fetchAllTokenMetas(
            wallet: Wallet?,
            networkFilters: List<Long?>?,
            searchTerm: String?,
        ): Array<TokenCardMeta?>? =
            withContext(Dispatchers.IO) {
                if (wallet == null) return@withContext null

                try {
                    realmManager.getRealmInstance(wallet).use { realm ->
                        val tokenMetas = mutableListOf<TokenCardMeta?>()

                        realm.executeTransaction { r ->
                            var realmTokens =
                                r
                                    .where(RealmToken::class.java)
                                    .findAll()
                                    .filter { token ->
                                        networkFilters.isNullOrEmpty() ||
                                            networkFilters.contains(token.chainId)
                                    }

                            // 如果有搜索词，进行过滤
                            if (!searchTerm.isNullOrEmpty()) {
                                realmTokens =
                                    realmTokens.filter { token ->
                                        token.name?.contains(searchTerm, ignoreCase = true) == true ||
                                            token.symbol?.contains(searchTerm, ignoreCase = true) == true
                                    }
                            }

                            for (realmToken in realmTokens) {
                                realmToken?.let { token ->
                                    val tokenGroup = getTokenGroup(token.chainId, token.tokenAddress, token.getContractType())
                                    val meta =
                                        TokenCardMeta(
                                            token.chainId,
                                            token.tokenAddress,
                                            token.balance ?: "0",
                                            token.getUpdateTime(),
                                            token.lastTxTime,
                                            token.getContractType(),
                                            tokenGroup,
                                        )
                                    meta.isEnabled = token.isEnabled
                                    tokenMetas.add(meta)
                                }
                            }
                        }

                        tokenMetas.toTypedArray()
                    }
                } catch (e: Exception) {
                    Timber.w(e)
                    null
                }
            }

        /**
         * 获取需要更新的代币元数据
         *
         * @param wallet 钱包信息
         * @param networkFilters 网络过滤器
         * @return 代币卡片元数据数组
         */
        override fun fetchTokenMetasForUpdate(
            wallet: Wallet?,
            networkFilters: List<Long?>?,
        ): Array<TokenCardMeta?>? {
            if (wallet == null) return null

            return try {
                realmManager.getRealmInstance(wallet).use { realm ->
                    val tokenMetas = mutableListOf<TokenCardMeta?>()

                    realm.executeTransaction { r ->
                        val realmTokens =
                            r
                                .where(RealmToken::class.java)
                                .findAll()
                                .filter { token ->
                                    networkFilters.isNullOrEmpty() ||
                                        networkFilters.contains(token.chainId)
                                }.filter { token ->
                                    // 只返回需要更新的代币（有可见性变更或最近更新的）
                                    token.isVisibilityChanged() ||
                                        (System.currentTimeMillis() - token.getUpdateTime()) < 5 * 60 * 1000 // 5分钟内更新的
                                }

                        for (realmToken in realmTokens) {
                            val tokenGroup = getTokenGroup(realmToken.chainId, realmToken.tokenAddress, realmToken.getContractType())
                            val meta =
                                TokenCardMeta(
                                    realmToken.chainId,
                                    realmToken.tokenAddress,
                                    realmToken.balance ?: "0",
                                    realmToken.getUpdateTime(),
                                    realmToken.lastTxTime,
                                    realmToken.getContractType(),
                                    tokenGroup,
                                )
                            meta.isEnabled = realmToken.isEnabled
                            tokenMetas.add(meta)
                        }
                    }

                    tokenMetas.toTypedArray()
                }
            } catch (e: Exception) {
                Timber.w(e)
                null
            }
        }

        /**
         * 获取所有有名称问题的代币
         *
         * @param walletAddress 钱包地址
         * @param networkFilters 网络过滤器
         * @return 代币数组
         */
        override suspend fun fetchAllTokensWithNameIssue(
            walletAddress: String?,
            networkFilters: List<Long?>?,
        ): Array<Token?>? =
            withContext(Dispatchers.IO) {
                if (walletAddress.isNullOrEmpty()) return@withContext null

                try {
                    val wallet = Wallet(walletAddress)
                    realmManager.getRealmInstance(wallet).use { realm ->
                        val tokens = mutableListOf<Token?>()

                        realm.executeTransaction { r ->
                            val realmTokens =
                                r
                                    .where(RealmToken::class.java)
                                    .findAll()
                                    .filter { token ->
                                        networkFilters.isNullOrEmpty() ||
                                            networkFilters.contains(token.chainId)
                                    }.filter { token ->
                                        // 有名称问题的代币：名称为空或符号为空
                                        token.name.isNullOrEmpty() || token.symbol.isNullOrEmpty()
                                    }

                            for (realmToken in realmTokens) {
                                realmToken?.let { token ->
                                    val convertedToken = convertSingle(token, r, null, wallet)
                                    if (convertedToken != null) {
                                        tokens.add(convertedToken)
                                    }
                                }
                            }
                        }

                        tokens.toTypedArray()
                    }
                } catch (e: Exception) {
                    Timber.w(e)
                    null
                }
            }

        /**
         * 获取所有名称空白的代币
         *
         * @param walletAddress 钱包地址
         * @param networkFilters 网络过滤器
         * @return 合约地址数组
         */
        override suspend fun fetchAllTokensWithBlankName(
            walletAddress: String?,
            networkFilters: List<Long?>?,
        ): Array<ContractAddress?>? =
            withContext(Dispatchers.IO) {
                if (walletAddress.isNullOrEmpty()) return@withContext null

                try {
                    val wallet = Wallet(walletAddress)
                    realmManager.getRealmInstance(wallet).use { realm ->
                        val contractAddresses = mutableListOf<ContractAddress?>()

                        realm.executeTransaction { r ->
                            val realmTokens =
                                r
                                    .where(RealmToken::class.java)
                                    .findAll()
                                    .filter { token ->
                                        networkFilters.isNullOrEmpty() ||
                                            networkFilters.contains(token.chainId)
                                    }.filter { token ->
                                        // 名称空白的代币：名称为空
                                        token.name.isNullOrEmpty()
                                    }

                            for (realmToken in realmTokens) {
                                realmToken?.let { token ->
                                    contractAddresses.add(ContractAddress(token.chainId, token.tokenAddress))
                                }
                            }
                        }

                        contractAddresses.toTypedArray()
                    }
                } catch (e: Exception) {
                    Timber.w(e)
                    null
                }
            }

        /**
         * 修复完整名称
         *
         * @param wallet 钱包信息
         * @param svs 资产定义服务
         * @return 修复的数量
         */
        override suspend fun fixFullNames(
            wallet: Wallet?,
            svs: AssetDefinitionService?,
        ): Int? =
            withContext(Dispatchers.IO) {
                if (wallet == null) return@withContext 0

                try {
                    realmManager.getRealmInstance(wallet).use { realm ->
                        var fixedCount = 0

                        realm.executeTransaction { r ->
                            val realmTokens =
                                r
                                    .where(RealmToken::class.java)
                                    .findAll()
                                    .filter { token ->
                                        // 需要修复的代币：名称为空或符号为空
                                        token.name.isNullOrEmpty() || token.symbol.isNullOrEmpty()
                                    }

                            for (realmToken in realmTokens) {
                                realmToken?.let { token ->
                                    // 这里可以添加从网络获取代币信息的逻辑
                                    // 暂时只是标记为已处理
                                    if (token.name.isNullOrEmpty()) {
                                        token.name = "Unknown Token"
                                        fixedCount++
                                    }
                                    if (token.symbol.isNullOrEmpty()) {
                                        token.symbol = "UNKNOWN"
                                        fixedCount++
                                    }
                                }
                            }
                        }

                        fixedCount
                    }
                } catch (e: Exception) {
                    Timber.w(e)
                    0
                }
            }

        /**
         * 更新以太坊价格信息
         *
         * @param ethTickers 以太坊价格映射
         */
     override  fun updateEthTickers(ethTickers: MutableMap<Long?, TokenTicker?>?) {
            if (ethTickers.isNullOrEmpty()) return

            val tickerUpdates = mutableListOf<ContractAddress>()
            try {
                realmManager.getRealmInstance(TICKER_DB).use { realm ->
                    realm.executeTransaction { r ->
                        for ((chainId, ticker) in ethTickers) {
                            if (chainId != null && ticker != null && writeTickerToRealm(r, ticker, chainId, "eth")) {
                                tickerUpdates.add(ContractAddress(chainId, "eth"))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e)
            }

            // 触发钱包代币更新
            updateWalletTokens(tickerUpdates)
        }

        /**
         * 更新钱包代币
         */
        private fun updateWalletTokens(tickerUpdates: List<ContractAddress>) {
            val currentWallet = ethereumNetworkRepository.getCurrentWalletAddress()
            if (currentWallet.isNullOrEmpty()) return

            try {
                realmManager.getRealmInstance(currentWallet).use { realm ->
                    realm.executeTransaction { r ->
                        for (contract in tickerUpdates) {
                            val contractAddress = if (contract.address == "eth") currentWallet else contract.address
                            val realmToken =
                                r
                                    .where(RealmToken::class.java)
                                    .equalTo("address", databaseKey(contract.chainId, contractAddress))
                                    .findFirst()

                            if (realmToken != null && realmToken.isEnabled) {
                                realmToken.setUpdateTime(System.currentTimeMillis())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e)
            }
        }

        /**
         * 更新ERC20代币价格信息
         *
         * @param chainId 链ID
         * @param erc20Tickers ERC20代币价格映射
         */
        override fun updateERC20Tickers(chainId: Long, erc20Tickers: MutableMap<String?, TokenTicker?>?) {
            if (erc20Tickers.isNullOrEmpty()) return

            val tickerUpdates = mutableListOf<ContractAddress>()
            try {
                realmManager.getRealmInstance(TICKER_DB).use { realm ->
                    realm.executeTransaction { r ->
                        for ((tokenAddress, ticker) in erc20Tickers) {
                            if (tokenAddress != null && ticker != null && writeTickerToRealm(r, ticker, chainId, tokenAddress)) {
                                tickerUpdates.add(ContractAddress(chainId, tokenAddress))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e)
            }

            updateWalletTokens(tickerUpdates)
        }

        /**
         * 移除过期的价格信息
         */
        override fun removeOutdatedTickers() {
            try {
                realmManager.getRealmInstance(TICKER_DB).use { realm ->
                    realm.executeTransaction { r ->
                        val cutoffTime = System.currentTimeMillis() - TickerService.TICKER_TIMEOUT
                        val outdatedTickers =
                            r
                                .where(RealmTokenTicker::class.java)
                                .lessThan("updatedTime", cutoffTime)
                                .findAll()

                        outdatedTickers.deleteAllFromRealm()
                    }
                }
            } catch (e: Exception) {
                Timber.w(e)
            }
        }

        /**
         * 设置可见性变更
         *
         * @param wallet 钱包信息
         * @param cAddr 合约地址
         */
        override fun setVisibilityChanged(
            wallet: Wallet?,
            cAddr: ContractAddress?,
        ) {
            if (wallet == null || cAddr == null) return

            try {
                realmManager.getRealmInstance(wallet).use { realm ->
                    realm.executeTransaction { r ->
                        val realmToken =
                            r
                                .where(RealmToken::class.java)
                                .equalTo("address", databaseKey(cAddr.chainId, cAddr.address))
                                .findFirst()

                        realmToken?.let { token ->
                            token.setVisibilityChanged(true)
                            token.setUpdateTime(System.currentTimeMillis())
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e)
            }
        }

        /**
         * 更新NFT资产
         *
         * @param wallet 钱包地址
         * @param erc721Token ERC721代币
         * @param additions 添加的资产列表
         * @param removals 移除的资产列表
         */
        override fun updateNFTAssets(
            wallet: String?,
            erc721Token: Token?,
            additions: List<BigInteger?>?,
            removals: List<BigInteger?>?,
        ) {
            if (wallet == null || erc721Token == null) return

            try {
                realmManager.getRealmInstance(wallet).use { realm ->
                    realm.executeTransaction { r ->
                        val realmToken =
                            r
                                .where(RealmToken::class.java)
                                .equalTo("address", databaseKey(erc721Token))
                                .findFirst()

                        realmToken?.let { token ->
                            // 添加新的NFT资产
                            additions?.filterNotNull()?.forEach { tokenId ->
                                val asset = erc721Token.getAssetForToken(tokenId)
                                if (asset != null) {
                                    writeAsset(r, erc721Token, tokenId, asset)
                                }
                            }

                            // 删除指定的NFT资产
                            removals?.filterNotNull()?.forEach { tokenId ->
                                deleteAssets(r, erc721Token, listOf(tokenId))
                            }

                            token.setUpdateTime(System.currentTimeMillis())
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e)
            }
        }

        /**
         * 写入NFT资产到Realm
         */
        private fun writeAsset(
            realm: Realm,
            token: Token,
            tokenId: BigInteger,
            asset: NFTAsset,
        ) {
            val key = RealmNFTAsset.databaseKey(token, tokenId)
            val realmAsset =
                realm
                    .where(RealmNFTAsset::class.java)
                    .equalTo("tokenIdAddr", key)
                    .findFirst()

            if (realmAsset == null) {
                realm.createObject(RealmNFTAsset::class.java, key)
            } else if (asset.equals(realmAsset)) {
                return
            }

            realmAsset?.apply {
                setMetaData(asset.jsonMetaData())
                setBalance(asset.balance)
            }

            realm.insertOrUpdate(realmAsset)
        }

        /**
         * 删除指定的NFT资产
         */
        private fun deleteAssets(
            realm: Realm,
            token: Token,
            assetIds: List<BigInteger>,
        ) {
            for (tokenId in assetIds) {
                val realmAsset =
                    realm
                        .where(RealmNFTAsset::class.java)
                        .equalTo("tokenIdAddr", RealmNFTAsset.databaseKey(token, tokenId))
                        .findFirst()

                realmAsset?.deleteFromRealm()
                val assets = token.getTokenAssets()
                if (assets is MutableMap<BigInteger, NFTAsset>) {
                    assets.remove(tokenId)
                }
            }
        }

        /**
         * 获取总价值
         *
         * @param currentAddress 当前地址
         * @param networkFilters 网络过滤器
         * @return 总价值对
         */
        override suspend fun getTotalValue(
            currentAddress: String?,
            networkFilters: List<Long?>?,
        ): Pair<Double?, Double?>? =
            withContext(Dispatchers.IO) {
                if (currentAddress.isNullOrEmpty()) return@withContext null

                try {
                    val wallet = Wallet(currentAddress)
                    val tokenMetas = fetchTokenMetas(wallet, networkFilters, null)

                    if (tokenMetas.isNullOrEmpty()) {
                        return@withContext Pair(0.0, 0.0)
                    }

                    var totalValue = 0.0
                    var totalChange = 0.0

                    for (meta in tokenMetas) {
                        meta?.let { tokenMeta ->
                            try {
                                val balance = tokenMeta.balance.toDoubleOrNull() ?: 0.0
                                val ticker = getCurrentTicker(tokenMeta.address)

                                if (ticker != null) {
                                    val price = ticker.price.toDoubleOrNull() ?: 0.0
                                    val change = ticker.percentChange24h.toDoubleOrNull() ?: 0.0

                                    totalValue += balance * price
                                    totalChange += change
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "计算代币价值时发生错误")
                            }
                        }
                    }

                    Pair(totalValue, totalChange)
                } catch (e: Exception) {
                    Timber.w(e)
                    null
                }
            }

        /**
         * 获取价格时间映射
         *
         * @param chainId 链ID
         * @param erc20Tokens ERC20代币列表
         * @return 价格时间映射
         */
        override fun getTickerTimeMap(
            chainId: Long,
            erc20Tokens: List<TokenCardMeta?>?,
        ): Map<String?, Long?>? {
            if (erc20Tokens.isNullOrEmpty()) return emptyMap()

            val tickerMap = mutableMapOf<String?, Long?>()
            try {
                realmManager.getRealmInstance(TICKER_DB).use { realm ->
                    for (token in erc20Tokens) {
                        if (token != null) {
                            val databaseKey = databaseKey(chainId, token.address)
                            val realmTicker =
                                realm
                                    .where(RealmTokenTicker::class.java)
                                    .equalTo("contract", databaseKey)
                                    .findFirst()

                            tickerMap[token.address] = realmTicker?.updatedTime
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e)
            }

            return tickerMap
        }

        /**
         * 删除价格信息
         */
        override fun deleteTickers() {
            try {
                realmManager.getRealmInstance(TICKER_DB).use { realm ->
                    realm.executeTransaction { r ->
                        val allTickers = r.where(RealmTokenTicker::class.java).findAll()
                        allTickers.deleteAllFromRealm()
                    }
                }
            } catch (e: Exception) {
                Timber.w(e)
            }
        }

        /**
         * 获取价格更新列表
         *
         * @param networkFilter 网络过滤器
         * @return 价格更新列表
         */
        override suspend fun getTickerUpdateList(networkFilter: List<Long?>?): List<String?>? =
            withContext(Dispatchers.IO) {
                try {
                    realmManager.getRealmInstance(TICKER_DB).use { realm ->
                        val updateList = mutableListOf<String?>()

                        realm.executeTransaction { r ->
                            val realmTickers =
                                r
                                    .where(RealmTokenTicker::class.java)
                                    .findAll()
                                    .filter { ticker ->
                                        // 过滤网络
                                        if (!networkFilter.isNullOrEmpty()) {
                                            val chainId = extractChainIdFromKey(ticker.contract)
                                            networkFilter.contains(chainId)
                                        } else {
                                            true
                                        }
                                    }.filter { ticker ->
                                        // 只返回最近更新的价格
                                        val cutoffTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000 // 24小时内
                                        ticker.updatedTime > cutoffTime
                                    }

                            for (realmTicker in realmTickers) {
                                realmTicker?.let { ticker ->
                                    updateList.add(ticker.contract)
                                }
                            }
                        }

                        updateList
                    }
                } catch (e: Exception) {
                    Timber.w(e)
                    null
                }
            }

        /**
         * 从数据库键中提取链ID
         */
        private fun extractChainIdFromKey(databaseKey: String?): Long? {
            if (databaseKey.isNullOrEmpty()) return null

            return try {
                val parts = databaseKey.split("-")
                if (parts.size >= 2) {
                    parts.last().toLongOrNull()
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.w(e)
                null
            }
        }

        /**
         * 更新价格信息
         *
         * @param chainId 链ID
         * @param address 代币地址
         * @param ticker 价格信息
         */
        override fun updateTicker(
            chainId: Long,
            address: String?,
            ticker: TokenTicker?,
        ) {
            if (address == null || ticker == null) return

            try {
                realmManager.getRealmInstance(TICKER_DB).use { realm ->
                    realm.executeTransaction { r ->
                        writeTickerToRealm(r, ticker, chainId, address)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e)
            }
        }

        /**
         * 存储代币信息
         *
         * @param wallet 钱包信息
         * @param tInfo 代币信息
         * @param type 合约类型
         * @return 存储的代币信息
         */
        override suspend fun storeTokenInfo(
            wallet: Wallet?,
            tInfo: TokenInfo?,
            type: ContractType?,
        ): TokenInfo? =
            withContext(Dispatchers.IO) {
                if (wallet == null || tInfo == null || type == null) return@withContext null

                try {
                    realmManager.getRealmInstance(wallet).use { realm ->
                        realm.executeTransaction { r ->
                            val databaseKey = databaseKey(tInfo.chainId, tInfo.address.orEmpty().lowercase())
                            var realmToken = r
                                    .where(RealmToken::class.java)
                                    .equalTo("address", databaseKey)
                                    .findFirst()

                            if (realmToken == null) {
                                realmToken = r.createObject(RealmToken::class.java, databaseKey)
                                // addedTime 字段会在创建时自动设置
                            }

                            realmToken?.apply {
                                name = tInfo.name
                                symbol = tInfo.symbol
                                decimals = tInfo.decimals
                                chainId = tInfo.chainId
                                interfaceSpec = type.ordinal
                                isEnabled = tInfo.isEnabled
                                setUpdateTime(System.currentTimeMillis())
                            }

                            r.insertOrUpdate(realmToken)
                        }
                    }
                    tInfo
                } catch (e: Exception) {
                    Timber.w(e)
                    null
                }
            }

        /**
         * 清理资源
         *
         * 取消协程作用域
         */
        fun cleanup() {
            repositoryScope.cancel()
        }

        // 私有辅助方法

        /**
         * 保存代币到本地数据库
         *
         * @param realm Realm实例
         * @param token 代币对象
         */
        private fun saveTokenLocal(
            realm: Realm,
            token: Token,
        ) {
            when (token.getInterfaceSpec()) {
                ContractType.ETHEREUM,
                ContractType.ERC20,
                ContractType.DYNAMIC_CONTRACT,
                ContractType.ERC875_LEGACY,
                ContractType.ERC875,
                ContractType.CURRENCY,
                ContractType.ERC721_TICKET,
                ContractType.MAYBE_ERC20,
                ContractType.ERC721,
                ContractType.ERC721_LEGACY,
                ContractType.ERC721_ENUMERABLE,
                ContractType.ERC1155,
                -> saveToken(realm, token)

                // 不保存的类型
                ContractType.NOT_SET,
                ContractType.OTHER,
                ContractType.CREATION,
                -> { /* 不执行任何操作 */ }

                else -> Timber.d("未知的代币合约类型")
            }
        }

        /**
         * 保存代币到Realm
         *
         * @param realm Realm实例
         * @param token 代币对象
         */
        private fun saveToken(
            realm: Realm,
            token: Token,
        ) {
            val dbKey = databaseKey(token)
            var realmToken =
                realm
                    .where(RealmToken::class.java)
                    .equalTo("address", dbKey)
                    .findFirst()

            val interfaceOrdinal = token.getInterfaceSpec().ordinal
            var wasNewToken = false

            if (realmToken == null) {
                Timber.tag(TAG).d(
                    "Save New Token: %s :%s : %s",
                    token.getFullName() ?: "",
                    token.tokenInfo.address,
                    token.balance.toPlainString(),
                )
                val createdToken =
                    realm.createObject(RealmToken::class.java, dbKey).apply {
                        name = token.tokenInfo.name
                        symbol = token.tokenInfo.symbol
                        decimals = token.tokenInfo.decimals
                        isEnabled = token.tokenInfo.isEnabled
                        chainId = token.tokenInfo.chainId
                        interfaceSpec = interfaceOrdinal
                        setLastBlock(token.lastBlockCheck)
                        setLastTxTime(token.lastTxTime)
                        setBalance(convertStringBalance(token.balance.toPlainString(), token.getInterfaceSpec()))
                    }
                realm.insertOrUpdate(createdToken)
                realmToken = createdToken
                wasNewToken = true
            } else {
                Timber.tag(TAG).d("Update Token: %s", token.getFullName() ?: "")
                val existingToken = realmToken
                existingToken.updateTokenInfoIfRequired(token.tokenInfo)

                val oldToken =
                    convertSingle(
                        existingToken,
                        realm,
                        null,
                        Wallet(token.getWallet()),
                    )

                val balanceChanged = oldToken == null || token.checkBalanceChange(oldToken)
                if (balanceChanged) {
                    existingToken.interfaceSpec = interfaceOrdinal
                    existingToken.setBalance(convertStringBalance(token.balance.toPlainString(), token.getInterfaceSpec()))
                    existingToken.setLastBlock(token.lastBlockCheck)
                    existingToken.setLastTxTime(token.lastTxTime)
                    writeAssetContract(realm, token)
                }

                if (oldToken == null || oldToken.getInterfaceSpec() != token.getInterfaceSpec()) {
                    existingToken.interfaceSpec = interfaceOrdinal
                }

                checkNameUpdate(existingToken, token)
                realm.insertOrUpdate(existingToken)
            }

            val managedToken = realmToken ?: return

            val tokenGroup =
                getTokenGroup(
                    token.tokenInfo.chainId,
                    token.tokenInfo.address ?: "",
                    ContractType.NOT_SET,
                )

            when {
                tokenGroup == TokenGroup.SPAM -> {
                    token.tokenInfo.isEnabled = false
                    managedToken.isEnabled = false
                }
                token.hasPositiveBalance() && !managedToken.getEnabled() && !managedToken.isVisibilityChanged() -> {
                    if (wasNewToken) {
                        Timber.tag(TAG).d("Save New Token set enable")
                    }
                    token.tokenInfo.isEnabled = true
                    managedToken.isEnabled = true
                    managedToken.setUpdateTime(System.currentTimeMillis())
                }
                !token.isEthereum() && !token.hasPositiveBalance() && managedToken.getEnabled() && !managedToken.isVisibilityChanged() -> {
                    token.tokenInfo.isEnabled = false
                    managedToken.isEnabled = false
                }
            }

            realm.insertOrUpdate(managedToken)
        }

        private fun checkNameUpdate(
            realmToken: RealmToken,
            token: Token,
        ) {
            val tokenName = token.tokenInfo.name
            if (!tokenName.isNullOrBlank()) {
                if (realmToken.name.isNullOrBlank() || realmToken.name != tokenName) {
                    realmToken.name = tokenName
                }
                realmToken.symbol = token.tokenInfo.symbol
                realmToken.decimals = token.tokenInfo.decimals
            }
        }

        private fun writeAssetContract(
            realm: Realm,
            token: Token,
        ) {
            val assetContract =
                runCatching {
                    val method =
                        token::class.java.methods.firstOrNull { candidate ->
                            candidate.name == "getAssetContract" && candidate.parameterCount == 0
                        }
                    method?.invoke(token) as? AssetContract
                }.getOrNull() ?: return

            val databaseKey = databaseKey(token)
            val realmNFT =
                realm
                    .where(RealmNFTAsset::class.java)
                    .equalTo("tokenIdAddr", databaseKey)
                    .findFirst() ?: realm.createObject(RealmNFTAsset::class.java, databaseKey)

            realmNFT.setMetaData(assetContract.getJSON())
            realm.insertOrUpdate(realmNFT)
        }

        /**
         * 转换Realm代币为Token对象
         *
         * @param realmItem Realm代币对象
         * @param realm Realm实例
         * @param svs 资产定义服务
         * @param wallet 钱包信息
         * @return Token对象
         */
        private fun convertSingle(
            realmItem: RealmToken?,
            @Suppress("UNUSED_PARAMETER") realm: Realm,
            @Suppress("UNUSED_PARAMETER") svs: AssetDefinitionService?,
            wallet: Wallet,
        ): Token? {
            if (realmItem == null) return null

            return try {
                val tokenInfo =
                    TokenInfo(
                        realmItem.tokenAddress,
                        realmItem.name ?: "",
                        realmItem.symbol ?: "",
                        realmItem.decimals,
                        realmItem.isEnabled,
                        realmItem.chainId,
                    )

                val balance = BigDecimal(realmItem.balance ?: "0")
                val contractType = ContractType.values().getOrNull(realmItem.interfaceSpec) ?: ContractType.NOT_SET
                val networkName = ethereumNetworkRepository.getNetworkByChain(realmItem.chainId).shortName

                val tokenFactory = TokenFactory()
                val token = tokenFactory.createToken(tokenInfo, balance, null, 0, contractType, networkName, 0)
                token.lastBlockCheck = realmItem.lastBlock
                token.lastTxTime = realmItem.lastTxTime
                token.setTokenWallet(wallet.address)

                // 处理辅助数据 - 暂时注释掉，需要根据实际Token类的方法来实现
                // if (!realmItem.auxData.isNullOrEmpty()) {
                //     token.setAuxData(realmItem.auxData)
                // }

                token
            } catch (e: Exception) {
                Timber.w(e, "转换Realm代币时发生错误")
                null
            }
        }

        /**
         * 转换Realm认证代币为Token对象
         *
         * @param realmAttestation Realm认证代币对象
         * @param wallet 钱包信息
         * @return Token对象
         */
        private fun convertAttestation(
            realmAttestation: RealmAttestation,
            wallet: Wallet,
        ): Token? =
            try {
                // 从地址中提取链ID
                val addressParts = realmAttestation.getAttestationKey().split("-")
                val chainId = if (addressParts.size > 1) addressParts[1].toLongOrNull() ?: 1L else 1L

                val tokenInfo =
                    TokenInfo(
                        realmAttestation.getTokenAddress() ?: "",
                        realmAttestation.name ?: "",
                        "ATTN", // 认证代币的默认符号
                        0, // 认证代币没有小数位
                        true,
                        chainId,
                    )

                val networkName = ethereumNetworkRepository.getNetworkByChain(chainId).shortName
                val tokenFactory = TokenFactory()
                val token = tokenFactory.createToken(tokenInfo, BigDecimal.ZERO, null, 0, ContractType.ERC721, networkName, 0)

                // 设置认证信息
                token.setTokenWallet(wallet.address)

                token
            } catch (e: Exception) {
                Timber.w(e, "转换认证代币时发生错误")
                null
            }

        /**
         * 将价格信息写入Realm数据库
         */
        private fun writeTickerToRealm(
            realm: Realm,
            ticker: TokenTicker,
            chainId: Long,
            tokenAddress: String,
        ): Boolean {
            if (ticker == null) return false

            val databaseKey = databaseKey(chainId, tokenAddress.lowercase())
            var realmItem =
                realm
                    .where(RealmTokenTicker::class.java)
                    .equalTo("contract", databaseKey)
                    .findFirst()

            if (realmItem == null) {
                realmItem = realm.createObject(RealmTokenTicker::class.java, databaseKey)
                realmItem.updatedTime = (ticker.updateTime)
            }

            realmItem?.percentChange24h = (ticker.percentChange24h)
            realmItem?.price = (ticker.price)
            realmItem?.image = (if (ticker.image.isNullOrEmpty()) "" else ticker.image)
            realmItem?.updatedTime =(ticker.updateTime)
            realmItem?.currencySymbol =(ticker.priceSymbol)
            realm.insertOrUpdate(realmItem)
            return true
        }
    }
