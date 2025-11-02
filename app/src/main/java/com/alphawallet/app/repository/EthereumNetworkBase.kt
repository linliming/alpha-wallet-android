package com.alphawallet.app.repository

import android.annotation.SuppressLint
import android.text.TextUtils
import android.util.LongSparseArray
import com.alphawallet.app.R
import com.alphawallet.app.entity.ContractLocator
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.CustomViewSettings
import com.alphawallet.app.entity.EventSync.Companion.BLOCK_SEARCH_INTERVAL
import com.alphawallet.app.entity.EventSync.Companion.OKX_BLOCK_SEARCH_INTERVAL
import com.alphawallet.app.entity.EventSync.Companion.POLYGON_BLOCK_SEARCH_INTERVAL
import com.alphawallet.app.entity.KnownContract
import com.alphawallet.app.entity.NetworkInfo
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenInfo
import com.alphawallet.app.util.Utils.isValidUrl
import com.alphawallet.ethereum.EthereumNetworkBase.ARBITRUM_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.ARBITRUM_TEST_ID
import com.alphawallet.ethereum.EthereumNetworkBase.AURORA_MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.AURORA_TESTNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.AVALANCHE_ID
import com.alphawallet.ethereum.EthereumNetworkBase.BASE_MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.BASE_TESTNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.BINANCE_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.BINANCE_TEST_ID
import com.alphawallet.ethereum.EthereumNetworkBase.CLASSIC_ID
import com.alphawallet.ethereum.EthereumNetworkBase.CRONOS_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.CRONOS_TEST_ID
import com.alphawallet.ethereum.EthereumNetworkBase.FANTOM_ID
import com.alphawallet.ethereum.EthereumNetworkBase.FANTOM_TEST_ID
import com.alphawallet.ethereum.EthereumNetworkBase.FUJI_TEST_ID
import com.alphawallet.ethereum.EthereumNetworkBase.GNOSIS_ID
import com.alphawallet.ethereum.EthereumNetworkBase.GOERLI_ID
import com.alphawallet.ethereum.EthereumNetworkBase.HOLESKY_ID
import com.alphawallet.ethereum.EthereumNetworkBase.IOTEX_MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.IOTEX_TESTNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.KLAYTN_BAOBAB_ID
import com.alphawallet.ethereum.EthereumNetworkBase.KLAYTN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.LINEA_ID
import com.alphawallet.ethereum.EthereumNetworkBase.LINEA_TEST_ID
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.MANTLE_MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.MANTLE_TESTNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.MILKOMEDA_C1_ID
import com.alphawallet.ethereum.EthereumNetworkBase.MINT_ID
import com.alphawallet.ethereum.EthereumNetworkBase.MINT_SEPOLIA_TESTNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.OKX_ID
import com.alphawallet.ethereum.EthereumNetworkBase.OPTIMISTIC_MAIN_ID
import com.alphawallet.ethereum.EthereumNetworkBase.PALM_ID
import com.alphawallet.ethereum.EthereumNetworkBase.PALM_TEST_ID
import com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_AMOY_ID
import com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_ID
import com.alphawallet.ethereum.EthereumNetworkBase.POLYGON_TEST_ID
import com.alphawallet.ethereum.EthereumNetworkBase.ROOTSTOCK_MAINNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.ROOTSTOCK_TESTNET_ID
import com.alphawallet.ethereum.EthereumNetworkBase.SEPOLIA_TESTNET_ID
import com.alphawallet.token.entity.ChainSpec
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.datatypes.Address
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger

/**
 * EthereumNetworkBase - 以太坊网络基础类
 *
 * 这个类是AlphaWallet中以太坊网络管理的核心基础类，提供了网络配置、管理和查询功能。
 * 
 * 主要功能包括：
 * 1. 网络配置管理（RPC URL、链ID、网络信息等）
 * 2. 网络状态查询（Gas API、链合约、网络支持等）
 * 3. 自定义网络管理（添加、删除、更新自定义RPC网络）
 * 4. 网络过滤器管理（设置、获取网络过滤器）
 * 5. 交易相关操作（获取nonce、Gas限制等）
 * 6. 网络变更监听（网络切换回调）
 * 7. 批处理限制管理
 * 8. 网络映射和缓存
 *
 * 技术特点：
 * - 使用LongSparseArray进行高效网络映射
 * - 支持协程处理异步操作
 * - 提供多种网络类型（主网、测试网）的支持
 * - 动态RPC URL配置基于环境
 *
 * @author AlphaWallet Team
 * @since 2024
 */
abstract class EthereumNetworkBase(
    protected val preferences: PreferenceRepositoryType,
    private val additionalNetworks: Array<NetworkInfo>,
    private val useTestNets: Boolean
) : EthereumNetworkRepositoryType {

    companion object {
        // API 常量
        const val COVALENT = "[COVALENT]"
        private const val GAS_API = "module=gastracker&action=gasoracle"
        const val DEFAULT_INFURA_KEY = "da3717f25f824cc1baa32d812386d93f"
        private const val INFURA_GAS_API = "https://gas.api.infura.io/networks/CHAIN_ID/suggestedGasFees"
        const val INFURA_BATCH_LIMIT = 512
        const val INFURA_DOMAIN = "infura.io"
        private const val BLOCKNATIVE_GAS_API = "https://api.blocknative.com/gasprices/blockprices?chainid="

        // 动态RPC URL配置
        private val keyProvider by lazy { KeyProviderFactory.get() }
        val usesProductionKey by lazy { !keyProvider.getInfuraKey().equals(DEFAULT_INFURA_KEY) }

        // 免费RPC URL
        const val FREE_MAINNET_RPC_URL = "https://rpc.ankr.com/eth"
        const val FREE_POLYGON_RPC_URL = "https://polygon-rpc.com"
        const val FREE_ARBITRUM_RPC_URL = "https://arbitrum.public-rpc.com"
        const val FREE_GOERLI_RPC_URL = "https://rpc.ankr.com/eth_goerli"
        const val FREE_MUMBAI_RPC_URL = "https://rpc-mumbai.maticvigil.com"
        const val FREE_PALM_RPC_URL = "https://palm-mainnet.infura.io/v3/3a961d6501e54add9a41aa53f15de99b"
        const val FREE_PALM_TEST_RPC_URL = "https://palm-testnet.infura.io/v3/3a961d6501e54add9a41aa53f15de99b"
        const val FREE_CRONOS_MAIN_BETA_RPC_URL = "https://evm.cronos.org"

        // 动态RPC URL（基于生产/开发环境）
        val DYNAMIC_MAINNET_RPC_URL by lazy { if (usesProductionKey) "https://mainnet.infura.io/v3/${keyProvider.getInfuraKey()}" else FREE_MAINNET_RPC_URL }
        val DYNAMIC_GOERLI_RPC_URL by lazy { if (usesProductionKey) "https://goerli.infura.io/v3/${keyProvider.getInfuraKey()}" else FREE_GOERLI_RPC_URL }
        val DYNAMIC_SEPOLIA_RPC_URL by lazy { if (usesProductionKey) "https://sepolia.infura.io/v3/${keyProvider.getInfuraKey()}" else SEPOLIA_TESTNET_RPC_URL }
        val DYNAMIC_POLYGON_RPC_URL by lazy { if (usesProductionKey) "https://polygon-mainnet.infura.io/v3/${keyProvider.getInfuraKey()}" else FREE_POLYGON_RPC_URL }
        val DYNAMIC_ARBITRUM_MAINNET_RPC by lazy { if (usesProductionKey) "https://arbitrum-mainnet.infura.io/v3/${keyProvider.getInfuraKey()}" else FREE_ARBITRUM_RPC_URL }
        val DYNAMIC_MUMBAI_TEST_RPC_URL by lazy { if (usesProductionKey) "https://polygon-mumbai.infura.io/v3/${keyProvider.getInfuraKey()}" else FREE_MUMBAI_RPC_URL }
        val DYNAMIC_OPTIMISTIC_MAIN_URL by lazy { if (usesProductionKey) "https://optimism-mainnet.infura.io/v3/${keyProvider.getInfuraKey()}" else OPTIMISTIC_MAIN_FALLBACK_URL }
        val DYNAMIC_PALM_RPC_URL by lazy { if (usesProductionKey) "https://palm-mainnet.infura.io/v3/${keyProvider.getInfuraKey()}" else FREE_PALM_RPC_URL }
        val DYNAMIC_PALM_TEST_RPC_URL by lazy { if (usesProductionKey) "https://palm-testnet.infura.io/v3/${keyProvider.getInfuraKey()}" else FREE_PALM_TEST_RPC_URL }
        val DYNAMIC_HOLESKY_BACKUP_RPC_URL by lazy { if (usesProductionKey) "https://holesky.infura.io/v3/${keyProvider.getInfuraKey()}" else "https://holesky.infura.io/v3/da3717f25f824cc1baa32d812386d93f" }
        val DYNAMIC_AMOY_RPC by lazy { if (usesProductionKey) "https://polygon-amoy.infura.io/v3/${keyProvider.getInfuraKey()}" else AMOY_TEST_RPC_URL }
        val DYNAMIC_AMOY_RPC_FALLBACK by lazy { if (usesProductionKey) AMOY_TEST_RPC_URL else "https://polygon-amoy-bor-rpc.publicnode.com" }

        // 备用RPC URL
        val DYNAMIC_MAINNET_FALLBACK_RPC_URL by lazy { if (usesProductionKey) FREE_MAINNET_RPC_URL else "https://mainnet.infura.io/v3/${keyProvider.getSecondaryInfuraKey()}" }
        val DYNAMIC_GOERLI_FALLBACK_RPC_URL by lazy { if (usesProductionKey) FREE_GOERLI_RPC_URL else "https://goerli.infura.io/v3/${keyProvider.getSecondaryInfuraKey()}" }
        val DYNAMIC_ARBITRUM_FALLBACK_MAINNET_RPC by lazy { if (usesProductionKey) FREE_ARBITRUM_RPC_URL else "https://arbitrum-mainnet.infura.io/v3/${keyProvider.getSecondaryInfuraKey()}" }
        val DYNAMIC_PALM_RPC_FALLBACK_URL by lazy { if (usesProductionKey) FREE_PALM_RPC_URL else "https://palm-mainnet.infura.io/v3/${keyProvider.getSecondaryInfuraKey()}" }
        val DYNAMIC_PALM_TEST_RPC_FALLBACK_URL by lazy { if (usesProductionKey) FREE_PALM_RPC_URL else "https://palm-testnet.infura.io/v3/${keyProvider.getSecondaryInfuraKey()}" }
        val DYNAMIC_LINEA_TEST_RPC by lazy { if (usesProductionKey) "https://linea-sepolia.infura.io/v3/${keyProvider.getInfuraKey()}" else LINEA_TEST_FREE_RPC }
        val DYNAMIC_BASE_RPC by lazy { if (usesProductionKey) "https://base-mainnet.infura.io/v3/${keyProvider.getInfuraKey()}" else BASE_FREE_MAINNET_RPC }
        val DYNAMIC_BASE_FALLBACK_RPC by lazy { if (usesProductionKey) BASE_FREE_MAINNET_RPC else "https://base-mainnet.public.blastapi.io" }
        val DYNAMIC_BASE_TEST_RPC by lazy { if (usesProductionKey) "https://base-sepolia.infura.io/v3/${keyProvider.getInfuraKey()}" else BASE_FREE_TESTNET_RPC }
        val DYNAMIC_BASE_TEST_FALLBACK_RPC by lazy { if (usesProductionKey) BASE_FREE_TESTNET_RPC else "https://sepolia.base.org" }

        // 批处理限制映射
        private val batchProcessingLimitMap = LongSparseArray<Int>()

        // 地址覆盖映射
        val addressOverride: Map<Long, String> = mapOf(
            com.alphawallet.ethereum.EthereumNetworkBase.OPTIMISTIC_MAIN_ID to "0x4200000000000000000000000000000000000006"
        )

        // 有真实价值的网络列表（主网）
        private val hasValue = listOf(
            MAINNET_ID,
            GNOSIS_ID,
            POLYGON_ID,
            ROOTSTOCK_MAINNET_ID,
            CLASSIC_ID,
            LINEA_ID,
            BASE_MAINNET_ID,
            MANTLE_MAINNET_ID,
            MINT_ID,
            BINANCE_MAIN_ID,
            AVALANCHE_ID,
            FANTOM_ID,
            OPTIMISTIC_MAIN_ID,
            CRONOS_MAIN_ID,
            ARBITRUM_MAIN_ID,
            PALM_ID,
            KLAYTN_ID,
            IOTEX_MAINNET_ID,
            AURORA_MAINNET_ID,
            MILKOMEDA_C1_ID,
            OKX_ID
        )

        // 测试网列表
        private val testnetList = listOf(
            SEPOLIA_TESTNET_ID,
            POLYGON_AMOY_ID,
            HOLESKY_ID,
            BASE_TESTNET_ID,
            MINT_SEPOLIA_TESTNET_ID,
            GOERLI_ID,
            BINANCE_TEST_ID,
            ROOTSTOCK_TESTNET_ID,
            CRONOS_TEST_ID,
            MANTLE_TESTNET_ID,
            POLYGON_TEST_ID,
            ARBITRUM_TEST_ID,
            LINEA_TEST_ID,
            KLAYTN_BAOBAB_ID,
            FANTOM_TEST_ID,
            IOTEX_TESTNET_ID,
            FUJI_TEST_ID,
            AURORA_TESTNET_ID,
            PALM_TEST_ID
        )

        // 链配置RPC映射
        val CHAIN_CONFIG_RPC: Map<Long, Array<String>> = mapOf(
            MAINNET_ID to arrayOf(DYNAMIC_MAINNET_RPC_URL, DYNAMIC_MAINNET_FALLBACK_RPC_URL),
            GOERLI_ID to arrayOf(DYNAMIC_GOERLI_RPC_URL, DYNAMIC_GOERLI_FALLBACK_RPC_URL),
            POLYGON_ID to arrayOf(DYNAMIC_POLYGON_RPC_URL, FREE_POLYGON_RPC_URL),
            ARBITRUM_MAIN_ID to arrayOf(DYNAMIC_ARBITRUM_MAINNET_RPC, DYNAMIC_ARBITRUM_FALLBACK_MAINNET_RPC),
            PALM_ID to arrayOf(DYNAMIC_PALM_RPC_URL, DYNAMIC_PALM_RPC_FALLBACK_URL),
            PALM_TEST_ID to arrayOf(DYNAMIC_PALM_TEST_RPC_URL, DYNAMIC_PALM_TEST_RPC_FALLBACK_URL)
        )

        /**
         * 获取Etherscan Gas Oracle API
         */
        fun getEtherscanGasOracle(chainId: Long): String {
            return if (hasEtherscanGasOracleAPI.contains(chainId) && networkMap.indexOfKey(chainId) >= 0) {
                networkMap.get(chainId).etherscanAPI + GAS_API
            } else {
                ""
            }
        }

        /**
         * 获取链的显示顺序
         */
        fun getChainOrdinal(chainId: Long): Int {
            return when {
                hasValue.contains(chainId) -> hasValue.indexOf(chainId)
                testnetList.contains(chainId) -> hasValue.size + testnetList.indexOf(chainId)
                else -> hasValue.size + testnetList.size + (chainId % 500).toInt()
            }
        }

        /**
         * 设置批处理错误
         */
        fun setBatchProcessingError(chainId: Long) {
            batchProcessingLimitMap.put(chainId, 0)
        }

        /**
         * 检查是否强制执行事件块限制
         *
         * @param chainId 链ID
         * @return 如果是Polygon网络则返回true，否则返回false
         */
        fun isEventBlockLimitEnforced(chainId: Long): Boolean {
            return chainId == POLYGON_ID ||
                    chainId == POLYGON_TEST_ID ||
                    chainId == POLYGON_AMOY_ID
        }

        /**
         * 获取最大事件获取数量
         *
         * @param chainId 链ID
         * @return 根据不同链返回相应的块搜索间隔
         */
        fun getMaxEventFetch(chainId: Long): BigInteger {
            return when (chainId) {
                POLYGON_ID,
                POLYGON_TEST_ID ->
                    BigInteger.valueOf(POLYGON_BLOCK_SEARCH_INTERVAL)
                OKX_ID ->
                    BigInteger.valueOf(OKX_BLOCK_SEARCH_INTERVAL)
                else ->
                    BigInteger.valueOf(BLOCK_SEARCH_INTERVAL)
            }
        }

        /**
         * 获取用于事件的节点URL
         *
         * @param chainId 链ID
         * @return 针对特定链优化的RPC URL
         */
        fun getNodeURLForEvents(chainId: Long): String {
            return when (chainId) {
                POLYGON_ID ->
                    FREE_POLYGON_RPC_URL // 比Infura更适合获取事件
                POLYGON_TEST_ID ->
                    FREE_MUMBAI_RPC_URL
                else ->
                    getNodeURLByNetworkId(chainId)
            }
        }

        /**
         * 计算批处理限制（临时方法）
         */
        private fun batchProcessingLimit(chainId: Long): Int {
            // 暂时返回0，不进行批处理
            return 0
            /*
            val info = builtinNetworkMap.get(chainId)
            return when {
                info?.rpcServerUrl?.contains(INFURA_DOMAIN) == true -> INFURA_BATCH_LIMIT
                info?.rpcServerUrl?.contains("klaytn") == true || info.rpcServerUrl.contains("rpc.ankr.com") -> 0
                chainId == GNOSIS_ID -> 6
                info?.rpcServerUrl?.contains("cronos.org") == true -> 5
                else -> 32
            }
            */
        }

        // 原始 RPC URL 常量（保留必要的常量，删除重复的）
        const val CLASSIC_RPC_URL = "https://www.ethercluster.com/etc"
        const val XDAI_RPC_URL = "https://rpc.gnosischain.com"
        const val BINANCE_TEST_RPC_URL = "https://data-seed-prebsc-1-s3.binance.org:8545"
        const val BINANCE_MAIN_RPC_URL = "https://bsc-dataseed.binance.org"
        const val HECO_RPC_URL = "https://http-mainnet.hecochain.com"
        const val AVALANCHE_RPC_URL = "https://api.avax.network/ext/bc/C/rpc"
        const val FUJI_TEST_RPC_URL = "https://api.avax-test.network/ext/bc/C/rpc"
        const val FANTOM_RPC_URL = "https://rpcapi.fantom.network"
        const val FANTOM_TEST_RPC_URL = "https://rpc.ankr.com/fantom_testnet"
        const val MATIC_RPC_URL = "https://matic-mainnet.chainstacklabs.com"
        const val AMOY_TEST_RPC_URL = "https://rpc-amoy.polygon.technology"
        const val OPTIMISTIC_MAIN_FALLBACK_URL = "https://mainnet.optimism.io"
        const val CRONOS_MAIN_RPC_URL = "https://evm.cronos.org"
        const val CRONOS_TEST_URL = "https://evm-t3.cronos.org"
        const val KLAYTN_RPC = "https://klaytn.blockpi.network/v1/rpc/public"
        const val KLAYTN_BAOBAB_RPC = "https://klaytn-baobab.blockpi.network/v1/rpc/public"
        const val AURORA_MAINNET_RPC_URL = "https://mainnet.aurora.dev"
        const val AURORA_TESTNET_RPC_URL = "https://testnet.aurora.dev"
        const val MILKOMEDA_C1_RPC = "https://rpc-mainnet-cardano-evm.c1.milkomeda.com"
        const val MILKOMEDA_C1_TEST_RPC = "https://rpc-devnet-cardano-evm.c1.milkomeda.com"
        const val SEPOLIA_TESTNET_RPC_URL = "https://rpc.sepolia.org"
        const val OPTIMISM_GOERLI_TESTNET_FALLBACK_RPC_URL = "https://goerli.optimism.io"
        const val ARBITRUM_GOERLI_TESTNET_FALLBACK_RPC_URL = "https://goerli-rollup.arbitrum.io/rpc"
        const val IOTEX_MAINNET_RPC_URL = "https://babel-api.mainnet.iotex.io"
        const val IOTEX_TESTNET_RPC_URL = "https://babel-api.testnet.iotex.io"
        const val OKX_RPC_URL = "https://exchainrpc.okex.org"
        const val ROOTSTOCK_MAINNET_RPC_URL = "https://public-node.rsk.co"
        const val ROOTSTOCK_TESTNET_RPC_URL = "https://public-node.testnet.rsk.co"
        const val HOLESKY_RPC_URL = "https://rpc.holesky.ethpandaops.io"
        const val HOLESKY_FALLBACK_URL = "https://holesky.rpc.thirdweb.com"
        const val LINEA_FREE_RPC = "https://linea.drpc.org"
        const val LINEA_TEST_FREE_RPC = "https://rpc.sepolia.linea.build"
        const val BASE_FREE_MAINNET_RPC = "https://base-rpc.publicnode.com"
        const val BASE_FREE_TESTNET_RPC = "https://base-sepolia-rpc.publicnode.com"
        const val MANTLE_MAINNET_RPC = "https://rpc.mantle.xyz"
        const val MANTLE_TESTNET_RPC = "https://rpc.sepolia.mantle.xyz"
        const val MINT_MAINNET_RPC = "https://global.rpc.mintchain.io"
        const val MINT_SEPOLIA_RPC = "https://sepolia-testnet-rpc.mintchain.io"

        // 已弃用的网络列表
        private val deprecatedNetworkList = listOf(
            POLYGON_TEST_ID,
            GOERLI_ID
        )

        // Gas API支持列表
        private val hasGasOracleAPI = listOf(
            MAINNET_ID,
            POLYGON_ID,
            ARBITRUM_MAIN_ID,
            AVALANCHE_ID,
            BINANCE_MAIN_ID,
            CRONOS_MAIN_ID,
            GOERLI_ID
        )

        private val hasEtherscanGasOracleAPI = listOf(
            MAINNET_ID,
            BINANCE_MAIN_ID,
            POLYGON_ID
        )

        private val hasBlockNativeGasOracleAPI = listOf(
            MAINNET_ID,
            POLYGON_ID
        )

        private val hasLockedGas = listOf(
            KLAYTN_ID,
            KLAYTN_BAOBAB_ID
        )

        private val hasOpenSeaAPI = listOf(
            MAINNET_ID,
            POLYGON_ID,
            ARBITRUM_TEST_ID,
            AVALANCHE_ID,
            KLAYTN_ID,
            GOERLI_ID
        )

        // 网络映射
        private val networkMap = LongSparseArray<NetworkInfo>()
        private val builtinNetworkMap = LongSparseArray<NetworkInfo>()
        private val chainLogos = LongSparseArray<Int>()
        private val smallChainLogos = LongSparseArray<Int>()
        private val chainColours = LongSparseArray<Int>()
        private val blockGasLimit = LongSparseArray<BigInteger>()

        init {
            setBatchProcessingLimits()
            initializeNetworkMaps()
        }

        /**
         * 设置批处理限制
         */
        private fun setBatchProcessingLimits() {
            batchProcessingLimitMap.put(MAINNET_ID, INFURA_BATCH_LIMIT)
            batchProcessingLimitMap.put(POLYGON_ID, INFURA_BATCH_LIMIT)
            batchProcessingLimitMap.put(ARBITRUM_MAIN_ID, INFURA_BATCH_LIMIT)
        }

        /**
         * 初始化网络映射
         */
        private fun initializeNetworkMaps() {
            // 初始化网络映射
            networkMap.put(MAINNET_ID,
                NetworkInfo("Ethereum", "ETH", arrayOf(DYNAMIC_MAINNET_RPC_URL), "https://etherscan.io/tx/",
                    MAINNET_ID, "https://api.etherscan.io/api", false))
            networkMap.put(CLASSIC_ID,
                NetworkInfo("Ethereum Classic", "ETC", arrayOf(CLASSIC_RPC_URL), "https://blockscout.com/etc/mainnet/tx/",
                    CLASSIC_ID, "https://blockscout.com/etc/mainnet/api", false))
            networkMap.put(GNOSIS_ID,
                NetworkInfo("Gnosis", "xDAi", arrayOf(XDAI_RPC_URL), "https://blockscout.com/xdai/mainnet/tx/",
                    GNOSIS_ID, "https://blockscout.com/xdai/mainnet/api", false))
            networkMap.put(GOERLI_ID,
                NetworkInfo("Görli (Test)", "GÖETH", arrayOf(DYNAMIC_GOERLI_RPC_URL), "https://goerli.etherscan.io/tx/",
                    GOERLI_ID, "https://goerli.etherscan.io/api", false))
            networkMap.put(BINANCE_TEST_ID,
                NetworkInfo("BSC TestNet (Test)", "T-BSC", arrayOf(BINANCE_TEST_RPC_URL), "https://explorer.binance.org/smart-testnet/tx/",
                    BINANCE_TEST_ID, "https://testnet.bscscan.com/api", false))
            networkMap.put(BINANCE_MAIN_ID,
                NetworkInfo("Binance (BSC)", "BSC", arrayOf(BINANCE_MAIN_RPC_URL), "https://explorer.binance.org/smart/tx/",
                    BINANCE_MAIN_ID, "https://api.bscscan.com/api", false))
            networkMap.put(AVALANCHE_ID,
                NetworkInfo("Avalanche Mainnet C-Chain", "AVAX", arrayOf(AVALANCHE_RPC_URL), "https://cchain.explorer.avax.network/tx/",
                    AVALANCHE_ID, "https://api.snowtrace.io/api", false))
            networkMap.put(FUJI_TEST_ID,
                NetworkInfo("Avalanche FUJI C-Chain (Test)", "AVAX", arrayOf(FUJI_TEST_RPC_URL), "https://cchain.explorer.avax-test.network/tx/",
                    FUJI_TEST_ID, "https://testnet.snowtrace.io/api", false))
            networkMap.put(FANTOM_ID,
                NetworkInfo("Fantom Opera", "FTM", arrayOf(FANTOM_RPC_URL), "https://ftmscan.com/tx/",
                    FANTOM_ID, "https://api.ftmscan.com/api", false))
            networkMap.put(FANTOM_TEST_ID,
                NetworkInfo("Fantom (Test)", "FTM", arrayOf(FANTOM_TEST_RPC_URL), "https://explorer.testnet.fantom.network/tx/",
                    FANTOM_TEST_ID, "https://testnet.ftmscan.com/api", false))
            networkMap.put(POLYGON_ID,
                NetworkInfo("Polygon", "POLY", arrayOf(MATIC_RPC_URL), "https://polygonscan.com/tx/",
                    POLYGON_ID, "https://api.polygonscan.com/api", false))
            networkMap.put(POLYGON_TEST_ID,
                NetworkInfo("Mumbai (Test)", "POLY", arrayOf(DYNAMIC_MUMBAI_TEST_RPC_URL), "https://mumbai.polygonscan.com/tx/",
                    POLYGON_TEST_ID, "https://api-testnet.polygonscan.com/api", false))
            networkMap.put(POLYGON_AMOY_ID,
                NetworkInfo("Amoy (Test)", "POLY", arrayOf(AMOY_TEST_RPC_URL), "https://amoy.polygonscan.com/tx/",
                    POLYGON_AMOY_ID, "https://api-amoy.polygonscan.com/api", false))
            networkMap.put(OPTIMISTIC_MAIN_ID,
                NetworkInfo("Optimistic", "ETH", arrayOf(OPTIMISTIC_MAIN_FALLBACK_URL), "https://optimistic.etherscan.io/tx/",
                    OPTIMISTIC_MAIN_ID, "https://api-optimistic.etherscan.io/api", false))
            networkMap.put(CRONOS_MAIN_ID,
                NetworkInfo("Cronos (Beta)", "CRO", arrayOf(CRONOS_MAIN_RPC_URL), "https://cronoscan.com/tx", 
                    CRONOS_MAIN_ID, "https://api.cronoscan.com/api", false))
            networkMap.put(CRONOS_TEST_ID,
                NetworkInfo("Cronos (Test)", "tCRO", arrayOf(CRONOS_TEST_URL), "https://testnet.cronoscan.com/tx/", 
                    CRONOS_TEST_ID, "https://testnet.cronoscan.com/api", false))
            networkMap.put(ARBITRUM_MAIN_ID,
                NetworkInfo("Arbitrum One", "AETH", arrayOf(DYNAMIC_ARBITRUM_MAINNET_RPC), "https://arbiscan.io/tx/",
                    ARBITRUM_MAIN_ID, "https://api.arbiscan.io/api", false))
            networkMap.put(PALM_ID,
                NetworkInfo("PALM", "PALM", arrayOf(DYNAMIC_PALM_RPC_URL), "https://explorer.palm.io/tx/",
                    PALM_ID, "https://explorer.palm.io/api", false))
            networkMap.put(PALM_TEST_ID,
                NetworkInfo("PALM (Test)", "PALM", arrayOf(DYNAMIC_PALM_TEST_RPC_URL), "https://explorer.palm-uat.xyz/tx/",
                    PALM_TEST_ID, "https://explorer.palm-uat.xyz/api", false))
            networkMap.put(KLAYTN_ID,
                NetworkInfo("Kaia Mainnet", "KAIA", arrayOf(KLAYTN_RPC), "https://scope.klaytn.com/tx/",
                    KLAYTN_ID, "https://scope.klaytn.com/api", false))
            networkMap.put(KLAYTN_BAOBAB_ID,
                NetworkInfo("Kaia Kairos (Test)", "KAIA", arrayOf(KLAYTN_BAOBAB_RPC), "https://baobab.scope.klaytn.com/tx/",
                    KLAYTN_BAOBAB_ID, "https://baobab.scope.klaytn.com/api", false))
            networkMap.put(AURORA_MAINNET_ID,
                NetworkInfo("Aurora", "ETH", arrayOf(AURORA_MAINNET_RPC_URL), "https://aurorascan.dev/tx/",
                    AURORA_MAINNET_ID, "https://aurorascan.dev/api", false))
            networkMap.put(AURORA_TESTNET_ID,
                NetworkInfo("Aurora (Test)", "ETH", arrayOf(AURORA_TESTNET_RPC_URL), "https://testnet.aurorascan.dev/tx/",
                    AURORA_TESTNET_ID, "https://testnet.aurorascan.dev/api", false))
            networkMap.put(MILKOMEDA_C1_ID,
                NetworkInfo("Milkomeda Cardano", "milkADA", arrayOf(MILKOMEDA_C1_RPC), "https://explorer-mainnet-cardano-evm.c1.milkomeda.com/tx/",
                    MILKOMEDA_C1_ID, "https://explorer-mainnet-cardano-evm.c1.milkomeda.com/api", false))
            networkMap.put(SEPOLIA_TESTNET_ID,
                NetworkInfo("Sepolia (Test)", "ETH", arrayOf(SEPOLIA_TESTNET_RPC_URL), "https://sepolia.etherscan.io/tx/",
                    SEPOLIA_TESTNET_ID, "https://sepolia.etherscan.io/api", false))
            networkMap.put(ARBITRUM_TEST_ID,
                NetworkInfo("Arbitrum Goerli (Test)", "AGOR", arrayOf(OPTIMISM_GOERLI_TESTNET_FALLBACK_RPC_URL), "https://goerli-rollup-explorer.arbitrum.io/tx/",
                    ARBITRUM_TEST_ID, "https://goerli-rollup-explorer.arbitrum.io/api", false))
            networkMap.put(IOTEX_MAINNET_ID,
                NetworkInfo("IoTeX", "IOTX", arrayOf(IOTEX_MAINNET_RPC_URL), "https://iotexscan.io/tx/",
                    IOTEX_MAINNET_ID, "https://iotexscan.io/api", false))
            networkMap.put(IOTEX_TESTNET_ID,
                NetworkInfo("IoTeX (Test)", "IOTX", arrayOf(IOTEX_TESTNET_RPC_URL), "https://testnet.iotexscan.io/tx/",
                    IOTEX_TESTNET_ID, "https://testnet.iotexscan.io/api", false))
            networkMap.put(OKX_ID,
                NetworkInfo("OKXChain Mainnet", "OKT", arrayOf(OKX_RPC_URL), "https://www.oklink.com/en/okc",
                    OKX_ID, "https://www.oklink.com/api", false))
            networkMap.put(ROOTSTOCK_MAINNET_ID,
                NetworkInfo("Rootstock", "RBTC", arrayOf(ROOTSTOCK_MAINNET_RPC_URL), "https://blockscout.com/rsk/mainnet/tx/",
                    ROOTSTOCK_MAINNET_ID, "https://blockscout.com/rsk/mainnet/api", false))
            networkMap.put(ROOTSTOCK_TESTNET_ID,
                NetworkInfo("Rootstock (Test)", "tBTC", arrayOf(ROOTSTOCK_TESTNET_RPC_URL), "",
                    ROOTSTOCK_TESTNET_ID, "", false))
            networkMap.put(LINEA_ID,
                NetworkInfo("Linea", "ETH", arrayOf(LINEA_FREE_RPC), "https://lineascan.build/tx/",
                    LINEA_ID, "https://api.lineascan.build/api", false))
            networkMap.put(LINEA_TEST_ID,
                NetworkInfo("Linea (Test)", "ETH", arrayOf(LINEA_TEST_FREE_RPC), "https://sepolia.lineascan.build/tx/",
                    LINEA_TEST_ID, "https://api-sepolia.lineascan.build/api", false))
            networkMap.put(HOLESKY_ID,
                NetworkInfo("Holesky (Test)", "HolETH", arrayOf(HOLESKY_RPC_URL), "https://holesky.etherscan.io/tx/",
                    HOLESKY_ID, "https://api-holesky.etherscan.io/api", false))
            networkMap.put(BASE_MAINNET_ID,
                NetworkInfo("Base", "ETH", arrayOf(BASE_FREE_MAINNET_RPC), "https://basescan.org/tx/",
                    BASE_MAINNET_ID, "https://api.basescan.org/api", false))
            networkMap.put(BASE_TESTNET_ID,
                NetworkInfo("Base (Test)", "ETH", arrayOf(BASE_FREE_TESTNET_RPC), "https://sepolia.basescan.org/tx/",
                    BASE_TESTNET_ID, "https://api-sepolia.basescan.org/api", false))
            networkMap.put(MANTLE_MAINNET_ID,
                NetworkInfo("Mantle", "ETH", arrayOf(MANTLE_MAINNET_RPC), "https://explorer.mantle.xyz/tx/",
                    MANTLE_MAINNET_ID, "https://explorer.mantle.xyz/api", false))
            networkMap.put(MANTLE_TESTNET_ID,
                NetworkInfo("Mantle Sepolia (Test)", "ETH", arrayOf(MANTLE_TESTNET_RPC), "https://explorer.sepolia.mantle.xyz/tx/",
                    MANTLE_TESTNET_ID, "https://explorer.sepolia.mantle.xyz/api", false))
            networkMap.put(MINT_ID,
                NetworkInfo("Mint", "ETH", arrayOf(MINT_MAINNET_RPC), "https://explorer.mintchain.io/tx/",
                    MINT_ID, "https://explorer.mintchain.io/api", false))
            networkMap.put(MINT_SEPOLIA_TESTNET_ID,
                NetworkInfo("Mint Sepolia (Test)", "ETH", arrayOf(MINT_SEPOLIA_RPC), "https://sepolia-testnet-explorer.mintchain.io/tx/",
                    MINT_SEPOLIA_TESTNET_ID, "https://sepolia-testnet-explorer.mintchain.io/api", false))
            
            // 复制到 builtinNetworkMap
            for (i in 0 until networkMap.size()) {
                val key = networkMap.keyAt(i)
                val value = networkMap.valueAt(i)
                builtinNetworkMap.put(key, value)
            }
        }

        /**
         * 获取批处理限制
         */
        fun getBatchProcessingLimit(chainId: Long): Int {
            return batchProcessingLimitMap.get(chainId, INFURA_BATCH_LIMIT)
        }

        /**
         * 检查网络是否有真实价值
         */
        fun hasRealValue(chainId: Long): Boolean {
            return hasValue.contains(chainId)
        }

        /**
         * 获取所有主网网络
         */
        fun getAllMainNetworks(): List<Long> {
            return hasValue
        }

        /**
         * 获取网络信息
         */
        fun getNetworkInfo(chainId: Long): NetworkInfo? {
            return networkMap.get(chainId)
        }

        /**
         * 获取网络名称
         */
        fun getNameById(chainId: Long): String {
            val info = networkMap.get(chainId)
            return info?.name ?: ""
        }

        /**
         * 获取网络
         */
        fun getNetwork(chainId: Long): NetworkInfo? {
            return networkMap.get(chainId)
        }

        /**
         * 获取节点URL
         */
        fun getNodeURLByNetworkId(networkId: Long): String {
            val network = networkMap.get(networkId)
            return network?.rpcServerUrl ?: ""
        }

        /**
         * 获取默认节点URL（随机挑选一个RPC端点）
         */
        @JvmStatic
        fun getDefaultNodeURL(chainId: Long): String {
            val info = networkMap.get(chainId)
            val urls = info?.rpcUrls
            return if (urls.isNullOrEmpty()) {
                ""
            } else {
                urls[kotlin.random.Random.nextInt(urls.size)] ?: ""
            }
        }

        /**
         * 获取次要节点URL（随机选择一个RPC URL）
         * 
         * @param networkId 网络ID
         * @return 随机选择的RPC URL，如果网络不存在则返回空字符串
         */
        fun getSecondaryNodeURL(networkId: Long): String? {
            val info = networkMap.get(networkId)
            return if (info != null && info.rpcUrls.isNotEmpty()) {
                val random = kotlin.random.Random
                info.rpcUrls[random.nextInt(info.rpcUrls.size)]
            } else {
                ""
            }
        }

        /**
         * 获取额外链 （用于静态调用）
         */

        fun extraChains(): List<ChainSpec>? = null


        fun getOverrideToken(): ContractLocator = ContractLocator("", CustomViewSettings.primaryChain, ContractType.ETHEREUM)


        fun isPriorityToken(token: Token): Boolean = false

        /**
         * 获取链Logo
         */
        fun getChainLogo(networkId: Long): Int {
            return chainLogos.get(networkId, R.drawable.ic_ethereum)
        }

        /**
         * 获取小链Logo
         */
        fun getSmallChainLogo(networkId: Long): Int {
            return smallChainLogos.get(networkId, R.drawable.ic_ethereum)
        }

        /**
         * 获取链颜色
         */
        fun getChainColour(chainId: Long): Int {
            return chainColours.get(chainId, R.color.mainnet)
        }

        /**
         * 获取最大Gas限制
         */
        fun getMaxGasLimit(chainId: Long): BigInteger {
            return blockGasLimit.get(chainId, BigInteger.valueOf(9000000))
        }

        /**
         * 获取Gas Oracle
         */
        fun getGasOracle(chainId: Long): String {
            return when {
                hasEtherscanGasOracleAPI.contains(chainId) -> "https://api.etherscan.io/api?$GAS_API"
                else -> ""
            }
        }

        /**
         * 获取BlockNative Oracle
         */
        fun getBlockNativeOracle(chainId: Long): String {
            return if (hasBlockNativeGasOracleAPI.contains(chainId)) {
                "$BLOCKNATIVE_GAS_API$chainId"
            } else {
                ""
            }
        }

        /**
         * 获取链序号
         */
//        fun getChainOrdinal(chainId: Long): Int {
//            return when (chainId) {
//                MAINNET_ID -> 0
//                CLASSIC_ID -> 1
//                GNOSIS_ID -> 2
//                BINANCE_MAIN_ID -> 3
//                FANTOM_ID -> 4
//                AVALANCHE_ID -> 5
//                POLYGON_ID -> 6
//                OPTIMISTIC_MAIN_ID -> 7
//                CRONOS_MAIN_ID -> 8
//                ARBITRUM_MAIN_ID -> 9
//                PALM_ID -> 10
//                KLAYTN_ID -> 11
//                IOTEX_MAINNET_ID -> 12
//                AURORA_MAINNET_ID -> 13
//                MILKOMEDA_C1_ID -> 14
//                SEPOLIA_TESTNET_ID -> 15
//                OKX_ID -> 16
//                ROOTSTOCK_MAINNET_ID -> 17
//                HOLESKY_ID -> 18
//                else -> -1
//            }
//        }

        /**
         * 检查是否为Infura
         */
        fun isInfura(rpcServerUrl: String): Boolean {
            return rpcServerUrl.contains(INFURA_DOMAIN)
        }

        /**
         * 检查是否为OKX
         */
        fun isOKX(networkInfo: NetworkInfo): Boolean {
            return networkInfo.chainId == OKX_ID
        }

        /**
         * 检查网络是否被弃用
         */
        fun isNetworkDeprecated(chainId: Long): Boolean {
            return deprecatedNetworkList.contains(chainId)
        }

        /**
         * 获取所有网络
         */
        fun getAllNetworks(): List<Long> {
            val keys = mutableListOf<Long>()
            for (i in 0 until networkMap.size()) {
                keys.add(networkMap.keyAt(i))
            }
            return keys
        }

        /**
         * 获取链覆盖地址
         */
        fun getChainOverrideAddress(chainId: Long): String? {
            return addressOverride[chainId]
        }

        /**
         * 检查是否有OpenSea API
         */
        fun hasOpenseaAPI(chainId: Long): Boolean {
            return hasOpenSeaAPI.contains(chainId)
        }

        /**
         * 获取网络信息
         */
        fun getNetworkByChain(chainId: Long): NetworkInfo? {
            return networkMap.get(chainId)
        }

        /**
         * 检查网络是否受支持
         * @param chainId 要检查的链ID
         * @return 如果网络受支持则返回true，否则返回false
         */
        fun isChainSupported(chainId: Long): Boolean {
            return hasValue.contains(chainId)
        }


        /**
         * 获取短链名称
         */
        fun getShortChainName(chainId: Long): String {
            return when (chainId) {
                MAINNET_ID -> "Ethereum"
                CLASSIC_ID -> "Ethereum Classic"
                GNOSIS_ID -> "Gnosis"
                GOERLI_ID -> "Görli (Test)"
                BINANCE_TEST_ID -> "BSC TestNet (Test)"
                BINANCE_MAIN_ID -> "Binance (BSC)"
                AVALANCHE_ID -> "Avalanche Mainnet C-Chain"
                FUJI_TEST_ID -> "Avalanche Fuji (Test)"
                POLYGON_ID -> "Polygon"
                POLYGON_TEST_ID -> "Polygon Mumbai (Test)"
                OPTIMISTIC_MAIN_ID -> "Optimism"
                CRONOS_MAIN_ID -> "Cronos"
                CRONOS_TEST_ID -> "Cronos TestNet (Test)"
                ARBITRUM_MAIN_ID -> "Arbitrum"
                PALM_ID -> "Palm"
                PALM_TEST_ID -> "Palm (Test)"
                KLAYTN_ID -> "Klaytn"
                KLAYTN_BAOBAB_ID -> "Klaytn Baobab (Test)"
                IOTEX_MAINNET_ID -> "IoTeX"
                IOTEX_TESTNET_ID -> "IoTeX (Test)"
                AURORA_MAINNET_ID -> "Aurora"
                AURORA_TESTNET_ID -> "Aurora (Test)"
                MILKOMEDA_C1_ID -> "Milkomeda C1"
                SEPOLIA_TESTNET_ID -> "Sepolia (Test)"
                ARBITRUM_TEST_ID -> "Arbitrum Goerli (Test)"
                OKX_ID -> "OKX"
                ROOTSTOCK_MAINNET_ID -> "Rootstock"
                ROOTSTOCK_TESTNET_ID -> "Rootstock (Test)"
                HOLESKY_ID -> "Holesky (Test)"
                LINEA_ID -> "Linea"
                LINEA_TEST_ID -> "Linea (Test)"
                POLYGON_AMOY_ID -> "Polygon Amoy (Test)"
                BASE_MAINNET_ID -> "Base"
                BASE_TESTNET_ID -> "Base (Test)"
                MANTLE_MAINNET_ID -> "Mantle"
                MANTLE_TESTNET_ID -> "Mantle Sepolia (Test)"
                MINT_ID -> "Mint"
                MINT_SEPOLIA_TESTNET_ID -> "Mint Sepolia (Test)"
                else -> "Unknown"
            }
        }

        /**
         * 获取链符号
         */
        fun getChainSymbol(chainId: Long): String {
            return when (chainId) {
                MAINNET_ID -> "ETH"
                CLASSIC_ID -> "ETC"
                GNOSIS_ID -> "xDAi"
                GOERLI_ID -> "GÖETH"
                BINANCE_TEST_ID -> "T-BSC"
                BINANCE_MAIN_ID -> "BSC"
                AVALANCHE_ID -> "AVAX"
                FUJI_TEST_ID -> "AVAX"
                POLYGON_ID -> "MATIC"
                POLYGON_TEST_ID -> "MATIC"
                OPTIMISTIC_MAIN_ID -> "ETH"
                CRONOS_MAIN_ID -> "CRO"
                CRONOS_TEST_ID -> "CRO"
                ARBITRUM_MAIN_ID -> "ETH"
                PALM_ID -> "PALM"
                PALM_TEST_ID -> "PALM"
                KLAYTN_ID -> "KLAY"
                KLAYTN_BAOBAB_ID -> "KLAY"
                IOTEX_MAINNET_ID -> "IOTX"
                IOTEX_TESTNET_ID -> "IOTX"
                AURORA_MAINNET_ID -> "ETH"
                AURORA_TESTNET_ID -> "ETH"
                MILKOMEDA_C1_ID -> "ADA"
                SEPOLIA_TESTNET_ID -> "ETH"
                ARBITRUM_TEST_ID -> "ETH"
                OKX_ID -> "OKT"
                ROOTSTOCK_MAINNET_ID -> "RBTC"
                ROOTSTOCK_TESTNET_ID -> "tBTC"
                HOLESKY_ID -> "HolETH"
                LINEA_ID -> "ETH"
                LINEA_TEST_ID -> "ETH"
                POLYGON_AMOY_ID -> "MATIC"
                BASE_MAINNET_ID -> "ETH"
                BASE_TESTNET_ID -> "ETH"
                MANTLE_MAINNET_ID -> "ETH"
                MANTLE_TESTNET_ID -> "ETH"
                MINT_ID -> "ETH"
                MINT_SEPOLIA_TESTNET_ID -> "ETH"
                else -> "UNKNOWN"
            }
        }
    }

    // 网络变更监听器
    private val onNetworkChangedListeners = mutableSetOf<OnNetworkChangeListener>()

    // 自定义网络管理
    var customNetworks: CustomNetworks? = null

    init {
        customNetworks = CustomNetworks(preferences)
    }

    /**
     * 自定义网络管理类
     */
    class CustomNetworks(private val preferences: PreferenceRepositoryType) {
        private val list = mutableListOf<NetworkInfo>()
        private val mapToTestNet = mutableMapOf<Long, Boolean>()
        private val hasValue = mutableSetOf<Long>()
        private val testnetList = mutableSetOf<Long>()

        init {
            restore()
        }

        /**
         * 恢复自定义网络
         */
        fun restore() {
            val networks = preferences.customRPCNetworks
            if (!networks.isNullOrEmpty()) {
                try {
                    val gson = Gson()
                    val listType = object : TypeToken<CustomNetworks>() {}.type
                    val customNetworks = gson.fromJson(networks, listType) as CustomNetworks
                    list.clear()
                    list.addAll(customNetworks.list)
                    mapToTestNet.clear()
                    mapToTestNet.putAll(customNetworks.mapToTestNet)
                    hasValue.clear()
                    hasValue.addAll(customNetworks.hasValue)
                    testnetList.clear()
                    testnetList.addAll(customNetworks.testnetList)
                } catch (e: Exception) {
                    Timber.e(e, "Error restoring custom networks")
                }
            }
        }

        /**
         * 保存网络
         */
        fun save(info: NetworkInfo, isTestnet: Boolean, oldChainId: Long?) {
            if (oldChainId != null) {
                updateNetwork(info, isTestnet, oldChainId)
            } else {
                addNetwork(info, isTestnet)
            }
            val networks = Gson().toJson(this)
            preferences.customRPCNetworks = networks
        }

        /**
         * 更新网络
         */
        private fun updateNetwork(info: NetworkInfo, isTestnet: Boolean, oldChainId: Long) {
            removeNetwork(oldChainId)
            addNetwork(info, isTestnet)
        }

        /**
         * 添加网络
         */
        private fun addNetwork(info: NetworkInfo, isTestnet: Boolean) {
            list.add(info)
            if (!isTestnet) {
                hasValue.add(info.chainId)
            } else {
                testnetList.add(info.chainId)
            }
            mapToTestNet[info.chainId] = isTestnet
            networkMap.put(info.chainId, info)
        }

        /**
         * 移除网络
         */
        fun remove(chainId: Long) {
            removeNetwork(chainId)
            val networks = Gson().toJson(this)
            preferences.customRPCNetworks = networks
        }

        /**
         * 移除网络实现
         */
        private fun removeNetwork(chainId: Long) {
            list.removeAll { it.chainId == chainId }
            hasValue.remove(chainId)
            testnetList.remove(chainId)
            mapToTestNet.remove(chainId)
            networkMap.remove(chainId)
        }
    }

    // 实现 EthereumNetworkRepositoryType 接口方法

    override fun getActiveBrowserNetwork(): NetworkInfo {
        val chainId = preferences.activeBrowserNetwork
        return getNetworkByChain(chainId)
    }

    override fun setActiveBrowserNetwork(networkInfo: NetworkInfo) {
        preferences.activeBrowserNetwork = networkInfo.chainId
        onNetworkChangedListeners.forEach { it.onNetworkChanged(networkInfo) }
    }

    override fun getNetworkByChain(chainId: Long): NetworkInfo {
        return networkMap.get(chainId) ?: throw IllegalArgumentException("Network not found for chainId: $chainId")
    }

    override suspend fun getLastTransactionNonce(web3j: Web3j, walletAddress: String): BigInteger {
        return withContext(Dispatchers.IO) {
            try {
                val ethGetTransactionCount = web3j.ethGetTransactionCount(
                    walletAddress,
                    DefaultBlockParameterName.LATEST
                ).send()
                
                if (ethGetTransactionCount.hasError()) {
                    throw Exception("Error getting transaction count: ${ethGetTransactionCount.error.message}")
                }
                
                ethGetTransactionCount.transactionCount
            } catch (e: Exception) {
                Timber.e(e, "Error getting last transaction nonce")
                throw e
            }
        }
    }

    /**
     * 获取可用网络列表
     *
     * 与 Java 版本一致的组装顺序：
     * 1) 先加入“有真实价值”的主网（含内置顺序与去重）
     * 2) 再按需加入测试网（受 `useTestNets` 控制）
     * 3) 合并 `additionalNetworks`（扩展网络），并保持顺序与去重
     *
     * 说明：展示顺序会影响 UI 列表排序；主网优先，随后测试网，确保常用网络优先展示。
     */
    override fun getAvailableNetworkList(): Array<NetworkInfo> {
        val result = mutableListOf<NetworkInfo>()

        // 1) 主网（有真实价值的网络）
        addNetworks(result, true)

        // 2) 追加测试网（根据开关决定是否加入）
        if (useTestNets) addNetworks(result, false)

        // 3) 合并额外网络（主网优先，再测试网），保持去重
        addNetworks(additionalNetworks, result, true)
        if (useTestNets) addNetworks(additionalNetworks, result, false)

        return result.toTypedArray()
    }

    /**
     * 获取所有激活的主网列表
     *
     * 仅返回“有真实价值”的主网集合（用于资产页等场景）。
     * 逻辑：主网 -> 合并 additionalNetworks 主网部分 -> 去重
     */
    override fun getAllActiveNetworks(): Array<NetworkInfo> {
        val result = mutableListOf<NetworkInfo>()

        // 主网
        addNetworks(result, true)

        // 额外主网
        addNetworks(additionalNetworks, result, true)

        return result.toTypedArray()
    }

    override fun addOnChangeDefaultNetwork(onNetworkChanged: OnNetworkChangeListener) {
        onNetworkChangedListeners.add(onNetworkChanged)
    }

    override fun getNameById(chainId: Long): String {
        return Companion.getNameById(chainId)
    }

    override fun getFilterNetworkList(): List<Long> {
        val filterString = preferences.networkFilterList
        return if (filterString.isNullOrEmpty()) {
            emptyList()
        } else {
            try {
                // 解析字符串格式的网络过滤器列表
                // 格式可能是: "[1, 137, 56]" 或 "1,137,56"
                val cleanString = filterString.trim().let { str ->
                    if (str.startsWith("[") && str.endsWith("]")) {
                        str.substring(1, str.length - 1)
                    } else {
                        str
                    }
                }
                if (cleanString.isEmpty()) {
                    emptyList()
                } else {
                    cleanString.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .mapNotNull { it.toLongOrNull() }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing network filter list: $filterString")
                emptyList()
            }
        }
    }

    override fun getSelectedFilters(): List<Long> {
        return getFilterNetworkList()
    }

    override fun getDefaultNetwork(): Long {
        return preferences.activeBrowserNetwork
    }

    override fun setFilterNetworkList(networkList: Array<Long>) {
        val filterString = networkList.joinToString(",")
        preferences.networkFilterList = filterString
    }

    override fun getAllKnownContracts(networkFilters: List<Long>): List<ContractLocator> {
        // 子类实现
        return emptyList()
    }

    override suspend fun getBlankOverrideTokens(wallet: Wallet): Array<Token> {
        return withContext(Dispatchers.IO) {
            val tokens = mutableListOf<Token>()
            val networks = getAllActiveNetworks()
            
            for (network in networks) {
                val token = createCurrencyToken(network)
                wallet.address?.let { token.setTokenWallet(it) }
                tokens.add(token)
            }
            
            tokens.toTypedArray()
        }
    }

    override fun getBlankOverrideToken(): Token {
        return getBlankOverrideToken(getActiveBrowserNetwork())
    }

    override fun getBlankOverrideToken(networkInfo: NetworkInfo): Token {
        return createCurrencyToken(networkInfo)
    }

    override fun readContracts(): KnownContract {
        // 子类实现
        throw UnsupportedOperationException("readContracts must be implemented by subclass")
    }

    override fun getIsPopularToken(chainId: Long, address: String): Boolean {
        // 子类实现
        return false
    }

    override fun getCurrentWalletAddress(): String {
        return preferences.currentWalletAddress ?: ""
    }

    override fun hasSetNetworkFilters(): Boolean {
        return preferences.hasSetNetworkFilters()
    }

    override fun setHasSetNetworkFilters() {
        preferences.setHasSetNetworkFilters()
    }

    /**
     * 保存自定义 RPC 网络配置
     *
     * 参考 Java 实现：
     * - 若为自定义网络，则将新的 rpcUrl 插入到 rpcUrls 数组首位作为优先节点；
     * - 若为内置网络，则保持原有 rpcUrls，不插入自定义 rpcUrl（仅覆盖名称/符号/浏览器信息等）；
     * - 通过 CustomNetworks.save 进行持久化，并支持链 ID 迁移（oldChainId）。
     */
    override fun saveCustomRPCNetwork(
        networkName: String,
        rpcUrl: String,
        chainId: Long,
        symbol: String,
        blockExplorerUrl: String,
        explorerApiUrl: String,
        isTestnet: Boolean,
        oldChainId: Long?
    ) {
        // 规范化与基础校验
        val trimmedRpc = rpcUrl.trim()
        val trimmedExplorer = blockExplorerUrl.trim()
        val trimmedExplorerApi = explorerApiUrl.trim()
        if (!isValidUrl(trimmedRpc)) {
            Timber.w("Invalid RPC url: %s", trimmedRpc)
            return
        }

        // 获取内置网络（若不存在则为自定义网络）
        val builtInNetwork = try {
            getBuiltInNetwork(chainId)
        } catch (_: IllegalArgumentException) {
            null
        }
        val isCustom = builtInNetwork == null

        // 基于 Java 逻辑：自定义网络把新 RPC 放在首位；内置网络不插入 rpcUrl
        val base = builtInNetwork?.rpcUrls ?: emptyArray()
        val networks: Array<String?> = if (isCustom) {
            // 去重后首位插入
            val deduped : Array<String?> = base.filter { it != trimmedRpc }.toTypedArray()

            arrayOf<String?>(trimmedRpc) + deduped

        } else {
            base
        }

        // 构建并保存
        val info = NetworkInfo(
            networkName,
            symbol,
            networks,
            trimmedExplorer,
            chainId,
            trimmedExplorerApi,
            isCustom
        )

        if (customNetworks == null) customNetworks = CustomNetworks(preferences)
        customNetworks?.save(info, isTestnet, oldChainId)
    }

    override fun removeCustomRPCNetwork(chainId: Long) {
        customNetworks?.remove(chainId)
    }

    override fun isChainContract(chainId: Long, address: String): Boolean {
        return addressOverride.containsKey(chainId) && addressOverride[chainId] == address
    }

    override fun hasLockedGas(chainId: Long): Boolean {
        return hasLockedGas.contains(chainId)
    }

    override fun hasBlockNativeGasAPI(chainId: Long): Boolean {
        return hasBlockNativeGasOracleAPI.contains(chainId)
    }

    override fun getBuiltInNetwork(chainId: Long): NetworkInfo {
        return builtinNetworkMap.get(chainId) ?: throw IllegalArgumentException("Built-in network not found for chainId: $chainId")
    }

    override fun commitPrefs() {
        preferences.commit()
    }

    /**
     * 根据名称获取网络ID
     */
    fun getNetworkIdFromName(name: String): Long {
        if (!TextUtils.isEmpty(name)) {
            for (i in 0 until networkMap.size()) {
                val networkInfo = networkMap.valueAt(i)
                if (name == networkInfo.name) {
                    return networkInfo.chainId
                }
            }
        }
        return 0
    }

    /**
     * 检查是否有Gas覆盖
     */
    fun hasGasOverride(chainId: Long): Boolean {
        return false
    }

    /**
     * 获取Gas覆盖值
     */
    fun gasOverrideValue(chainId: Long): BigInteger {
        return BigInteger.valueOf(1)
    }

    /**
     * 获取额外链
     */
//    fun extraChains(): List<ChainSpec>? = extraChainsCompat()

//    open fun extraChainsCompat(): List<ChainSpec>? {
//        return null
//    }

    /**
     * 添加默认网络
     */
    fun addDefaultNetworks(): List<Long> {
        return CustomViewSettings.alwaysVisibleChains
    }

    /**
     * 获取覆盖代币
     */
//    fun getOverrideToken(): ContractLocator = getOverrideTokenCompat()

//    open fun getOverrideTokenCompat(): ContractLocator {
//        return getOverrideToken()
//    }

    /**
     * 检查是否为优先代币
     */
//    fun isPriorityToken(token: Token): Boolean = isPriorityTokenCompat(token)

//    open fun isPriorityTokenCompat(token: Token): Boolean {
//        return isPriorityToken(token)
//    }

    /**
     * 获取优先覆盖
     */
    fun getPriorityOverride(token: Token): Long {
        return if (token.isEthereum()) token.tokenInfo.chainId + 1 else 0
    }

    /**
     * 检查链是否被支持
     */
    fun isChainSupported(chainId: Long): Boolean {
        return networkMap.get(chainId) != null
    }

    /**
     * 获取小数位覆盖（目前总是返回0）
     * 
     * @param address 代币地址
     * @param chainId 链ID
     * @return 小数位覆盖值，目前固定返回0
     */
    fun decimalOverride(address: String, chainId: Long): Int {
        return 0
    }



    /**
     * 添加网络数组到结果列表
     * 
     * @param networks 网络数组
     * @param result 结果列表
     * @param withValue 是否添加有价值的网络
     */
    private fun addNetworks(networks: Array<NetworkInfo>, result: MutableList<NetworkInfo>, withValue: Boolean) {
        for (network in networks) {
            if (hasRealValue(network.chainId) == withValue && !result.contains(network)) {
                result.add(network)
            }
        }
    }

    /**
     * 添加网络到结果列表
     * 
     * @param result 结果列表
     * @param withValue 是否添加有价值的网络
     */
    private fun addNetworks(result: MutableList<NetworkInfo>, withValue: Boolean) {
        if (withValue) {
            // 添加有真实价值的非弃用网络
            for (networkId in hasValue) {
                if (!deprecatedNetworkList.contains(networkId)) {
                    networkMap.get(networkId)?.let {
                        if (!result.contains(it)) {
                            result.add(it)
                        }
                    }
                }
            }

            // 添加有真实价值的弃用网络
            for (networkId in hasValue) {
                if (deprecatedNetworkList.contains(networkId)) {
                    networkMap.get(networkId)?.let {
                        if (!result.contains(it)) {
                            result.add(it)
                        }
                    }
                }
            }
        } else {
            // 添加测试网中的非弃用网络
            for (networkId in testnetList) {
                if (!deprecatedNetworkList.contains(networkId)) {
                    networkMap.get(networkId)?.let {
                        if (!result.contains(it)) {
                            result.add(it)
                        }
                    }
                }
            }

            // 添加测试网中的弃用网络
            for (networkId in testnetList) {
                if (deprecatedNetworkList.contains(networkId)) {
                    networkMap.get(networkId)?.let {
                        if (!result.contains(it)) {
                            result.add(it)
                        }
                    }
                }
            }
        }
        
        // 添加额外的网络
        // addNetworks(additionalNetworks, result, withValue)
    }

    /**
     * 创建货币代币
     */
    @SuppressLint("SuspiciousIndentation")
    private fun createCurrencyToken(network: NetworkInfo): Token {
        val tokenInfo = TokenInfo(Address.DEFAULT.toString(), network.name, network.symbol, 18, true, network.chainId)
        val eth = Token(tokenInfo, BigDecimal.ZERO, 0, network.shortName, ContractType.ETHEREUM)
        eth.setTokenWallet(Address.DEFAULT.toString())
        eth.setIsEthereum()
        eth.pendingBalance = BigDecimal.ZERO
        return eth
    }

}
