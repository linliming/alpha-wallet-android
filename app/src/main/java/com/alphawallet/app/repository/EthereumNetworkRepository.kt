package com.alphawallet.app.repository

import android.content.Context
import com.alphawallet.app.entity.ContractLocator
import com.alphawallet.app.entity.KnownContract
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.token.entity.ChainSpec
import com.google.gson.Gson
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * EthereumNetworkRepository - 以太坊网络仓库实现类
 *
 * 这个类是EthereumNetworkBase的具体实现，提供了以太坊网络管理的完整功能。
 * 主要功能包括：
 * 1. 流行代币管理（检查、获取流行代币）
 * 2. 已知合约管理（读取、解析合约信息）
 * 3. 网络配置管理（继承自EthereumNetworkBase）
 * 4. 自定义网络支持（继承自EthereumNetworkBase）
 *
 * @param preferenceRepository 偏好设置仓库
 * @param context Android上下文
 *
 * @author AlphaWallet Team
 * @since 2024
 */
class EthereumNetworkRepository(
    preferenceRepository: PreferenceRepositoryType,
    private val context: Context
) : EthereumNetworkBase(preferenceRepository, emptyArray(), true) {

    // 流行代币映射
    private val popularTokens = mutableMapOf<String, ContractLocator>()

    /**
     * 根据网络ID获取节点URL
     *
     * @param networkId 网络ID
     * @return 节点URL
     */
    fun getNodeURLByNetworkId(networkId: Long): String {
        return EthereumNetworkBase.getNodeURLByNetworkId(networkId)
    }

    /**
     * 获取所有已知合约
     *
     * 注意：如果合约在XDAI和MAINNET_ID上有相同地址，需要重构
     *
     * @param networkFilters 网络过滤器
     * @return 合约定位器列表
     */
    override fun getAllKnownContracts(networkFilters: List<Long>): List<ContractLocator> {
        if (popularTokens.isEmpty()) {
            buildPopularTokenMap(networkFilters)
        }
        return popularTokens.values.toList()
    }

    /**
     * 构建流行代币映射
     *
     * @param networkFilters 网络过滤器
     */
    private fun buildPopularTokenMap(networkFilters: List<Long>) {
        val knownContract = readContracts()
        if (knownContract == null) return

        // 处理主网代币
        if (networkFilters.isEmpty() || networkFilters.contains(com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID)) {
            for (unknownToken in knownContract.mainNet.orEmpty()) {
                popularTokens[unknownToken.address.lowercase()] = ContractLocator(
                    unknownToken.address,
                    com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
                )
            }
        }

        // 处理XDAI代币
        if (networkFilters.isEmpty() || networkFilters.contains(com.alphawallet.ethereum.EthereumNetworkBase.GNOSIS_ID)) {
            for (unknownToken in knownContract.xDAI.orEmpty()) {
                popularTokens[unknownToken.address.lowercase()] = ContractLocator(
                    unknownToken.address,
                    com.alphawallet.ethereum.EthereumNetworkBase.GNOSIS_ID
                )
            }
        }
    }

    /**
     * 读取合约信息
     *
     * 从assets目录下的known_contract.json文件读取已知合约信息
     *
     * @return 已知合约信息，如果读取失败返回null
     */
    override fun readContracts(): KnownContract {
        return try {
            val inputStream: InputStream = context.assets.open("known_contract.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()

            val jsonString = String(buffer, StandardCharsets.UTF_8)
            Gson().fromJson(jsonString, KnownContract::class.java) ?: KnownContract()
        } catch (e: IOException) {
            Timber.e(e, "Error reading known contracts")
            KnownContract()
        }
    }

    /**
     * 检查是否为流行代币（重写父类方法）
     *
     * @param chainId 链ID
     * @param address 合约地址
     * @return 是否为流行代币
     */
    override fun getIsPopularToken(chainId: Long, address: String): Boolean {
        return popularTokens.containsKey(address.lowercase())
    }

    companion object {
        @JvmStatic
        fun hasRealValue(chainId: Long): Boolean {
            return EthereumNetworkBase.hasRealValue(chainId)
        }

        @JvmStatic
        fun getChainLogo(networkId: Long): Int {
            return EthereumNetworkBase.getChainLogo(networkId)
        }

        @JvmStatic
        fun getSmallChainLogo(networkId: Long): Int {
            return EthereumNetworkBase.getSmallChainLogo(networkId)
        }

        @JvmStatic
        fun getChainOverrideAddress(chainId: Long): String {
            return EthereumNetworkBase.getChainOverrideAddress(chainId).orEmpty()
        }

        @JvmStatic
        fun getDefaultNodeURL(chainId: Long): String {
            return EthereumNetworkBase.getDefaultNodeURL(chainId)
        }

        @JvmStatic
        fun getGasOracle(chainId: Long): String {
            return EthereumNetworkBase.getGasOracle(chainId)
        }

        @JvmStatic
        fun extraChainsCompat(): List<ChainSpec>? {
            return extraChains()
        }

        @JvmStatic
        fun getOverrideTokenCompat(): ContractLocator {
            return getOverrideToken()
        }

        @JvmStatic
        fun isPriorityTokenCompat(token: Token): Boolean {
            return isPriorityToken(token)
        }
    }
}
