package com.alphawallet.app.repository

import com.alphawallet.app.entity.ContractLocator
import com.alphawallet.app.entity.KnownContract
import com.alphawallet.app.entity.NetworkInfo
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.Token
import org.web3j.protocol.Web3j
import java.math.BigInteger

/**
 * EthereumNetworkRepositoryType - 以太坊网络仓库接口
 *
 * 这个接口定义了AlphaWallet中以太坊网络管理的核心功能。
 * 主要职责包括：
 * 1. 网络信息管理（获取、设置、查询网络信息）
 * 2. 网络过滤器管理（设置、获取网络过滤器）
 * 3. 交易相关操作（获取交易nonce）
 * 4. 合约管理（已知合约、流行代币）
 * 5. 自定义RPC网络管理
 * 6. 网络状态查询（Gas API、链合约等）
 *
 * @author AlphaWallet Team
 * @since 2024
 */
interface EthereumNetworkRepositoryType {

    /**
     * 获取当前活跃的浏览器网络
     *
     * @return 当前活跃的网络信息
     */
    fun getActiveBrowserNetwork(): NetworkInfo

    /**
     * 设置活跃的浏览器网络
     *
     * @param networkInfo 要设置的网络信息
     */
    fun setActiveBrowserNetwork(networkInfo: NetworkInfo)

    /**
     * 根据链ID获取网络信息
     *
     * @param chainId 链ID
     * @return 对应的网络信息
     */
    fun getNetworkByChain(chainId: Long): NetworkInfo

    /**
     * 获取钱包地址的最后交易nonce
     *
     * @param web3j Web3j实例
     * @param walletAddress 钱包地址
     * @return 最后交易nonce
     */
    suspend fun getLastTransactionNonce(web3j: Web3j, walletAddress: String): BigInteger

    /**
     * 获取可用的网络列表
     *
     * @return 可用网络数组
     */
    fun getAvailableNetworkList(): Array<NetworkInfo>

    /**
     * 获取所有活跃网络
     *
     * @return 活跃网络数组
     */
    fun getAllActiveNetworks(): Array<NetworkInfo>

    /**
     * 添加网络变更监听器
     *
     * @param onNetworkChanged 网络变更监听器
     */
    fun addOnChangeDefaultNetwork(onNetworkChanged: OnNetworkChangeListener)

    /**
     * 根据链ID获取网络名称
     *
     * @param chainId 链ID
     * @return 网络名称
     */
    fun getNameById(chainId: Long): String

    /**
     * 获取网络过滤器列表
     *
     * @return 网络过滤器链ID列表
     */
    fun getFilterNetworkList(): List<Long>

    /**
     * 获取选中的过滤器
     *
     * @return 选中的过滤器链ID列表
     */
    fun getSelectedFilters(): List<Long>

    /**
     * 获取默认网络
     *
     * @return 默认网络链ID
     */
    fun getDefaultNetwork(): Long

    /**
     * 设置网络过滤器列表
     *
     * @param networkList 网络链ID数组
     */
    fun setFilterNetworkList(networkList: Array<Long>)

    /**
     * 获取所有已知合约
     *
     * @param networkFilters 网络过滤器
     * @return 合约定位器列表
     */
    fun getAllKnownContracts(networkFilters: List<Long>): List<ContractLocator>

    /**
     * 获取钱包的空白覆盖代币
     *
     * @param wallet 钱包
     * @return 空白覆盖代币数组
     */
    suspend fun getBlankOverrideTokens(wallet: Wallet): Array<Token>

    /**
     * 获取空白覆盖代币
     *
     * @return 空白覆盖代币
     */
    fun getBlankOverrideToken(): Token

    /**
     * 根据网络信息获取空白覆盖代币
     *
     * @param networkInfo 网络信息
     * @return 空白覆盖代币
     */
    fun getBlankOverrideToken(networkInfo: NetworkInfo): Token

    /**
     * 读取合约信息
     *
     * @return 已知合约信息
     */
    fun readContracts(): KnownContract

    /**
     * 检查是否为流行代币
     *
     * @param chainId 链ID
     * @param address 合约地址
     * @return 是否为流行代币
     */
    fun getIsPopularToken(chainId: Long, address: String): Boolean

    /**
     * 获取当前钱包地址
     *
     * @return 当前钱包地址
     */
    fun getCurrentWalletAddress(): String

    /**
     * 检查是否已设置网络过滤器
     *
     * @return 是否已设置网络过滤器
     */
    fun hasSetNetworkFilters(): Boolean

    /**
     * 设置已设置网络过滤器标志
     */
    fun setHasSetNetworkFilters()

    /**
     * 保存自定义RPC网络
     *
     * @param networkName 网络名称
     * @param rpcUrl RPC URL
     * @param chainId 链ID
     * @param symbol 代币符号
     * @param blockExplorerUrl 区块浏览器URL
     * @param explorerApiUrl 浏览器API URL
     * @param isTestnet 是否为测试网
     * @param oldChainId 旧链ID（用于更新）
     */
    fun saveCustomRPCNetwork(
        networkName: String,
        rpcUrl: String,
        chainId: Long,
        symbol: String,
        blockExplorerUrl: String,
        explorerApiUrl: String,
        isTestnet: Boolean,
        oldChainId: Long?
    )

    /**
     * 移除自定义RPC网络
     *
     * @param chainId 链ID
     */
    fun removeCustomRPCNetwork(chainId: Long)

    /**
     * 检查是否为链合约
     *
     * @param chainId 链ID
     * @param address 合约地址
     * @return 是否为链合约
     */
    fun isChainContract(chainId: Long, address: String): Boolean

    /**
     * 检查是否有锁定的Gas
     *
     * @param chainId 链ID
     * @return 是否有锁定的Gas
     */
    fun hasLockedGas(chainId: Long): Boolean

    /**
     * 检查是否有BlockNative Gas API
     *
     * @param chainId 链ID
     * @return 是否有BlockNative Gas API
     */
    fun hasBlockNativeGasAPI(chainId: Long): Boolean

    /**
     * 获取内置网络信息
     *
     * @param chainId 链ID
     * @return 内置网络信息
     */
    fun getBuiltInNetwork(chainId: Long): NetworkInfo

    /**
     * 提交偏好设置
     */
    fun commitPrefs()
}
