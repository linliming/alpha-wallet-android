package com.alphawallet.app.repository

import android.util.Pair
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.ImageEntry
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.entity.tokendata.TokenTicker
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.entity.tokens.TokenInfo
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.token.entity.ContractAddress
import io.realm.Realm
import java.math.BigDecimal
import java.math.BigInteger

/**
 * TokenLocalSource - 代币本地数据源接口
 *
 * 定义代币数据本地存储的核心操作接口，包括：
 * 1. 代币数据的增删改查操作
 * 2. NFT资产管理
 * 3. 代币价格信息管理
 * 4. 认证代币处理
 * 5. 代币元数据管理
 *
 * 使用Kotlin协程替代RxJava，提供更好的异步处理性能。
 *
 * @author AlphaWallet Team
 * @since 2024
 */
interface TokenLocalSource {
    /**
     * 保存单个代币
     *
     * @param wallet 钱包信息
     * @param token 代币对象
     * @return 保存后的代币对象
     */
    suspend fun saveToken(
        wallet: Wallet?,
        token: Token?,
    ): Token?

    /**
     * 保存代币数组
     *
     * @param wallet 钱包信息
     * @param items 代币数组
     * @return 保存后的代币数组
     */
    suspend fun saveTokens(
        wallet: Wallet?,
        items: Array<Token?>?,
    ): Array<Token?>?

    /**
     * 更新代币余额
     *
     * @param wallet 钱包信息
     * @param token 代币对象
     * @param balance 余额
     * @param balanceArray 余额数组
     * @return 更新是否成功
     */
    fun updateTokenBalance(
        wallet: Wallet?,
        token: Token?,
        balance: BigDecimal?,
        balanceArray: List<BigInteger?>?,
    ): Boolean

    /**
     * 获取代币
     *
     * @param chainId 链ID
     * @param wallet 钱包信息
     * @param address 代币地址
     * @return 代币对象
     */
    fun fetchToken(
        chainId: Long,
        wallet: Wallet?,
        address: String?,
    ): Token?

    /**
     * 设置代币启用状态
     *
     * @param wallet 钱包信息
     * @param cAddr 合约地址
     * @param isEnabled 是否启用
     */
    fun setEnable(
        wallet: Wallet?,
        cAddr: ContractAddress?,
        isEnabled: Boolean,
    )

    /**
     * 获取代币图片URL
     *
     * @param chainId 链ID
     * @param address 代币地址
     * @return 图片URL
     */
    fun getTokenImageUrl(
        chainId: Long,
        address: String?,
    ): String?

    /**
     * 删除Realm代币
     *
     * @param wallet 钱包信息
     * @param tcmList 代币卡片元数据列表
     */
    fun deleteRealmTokens(
        wallet: Wallet?,
        tcmList: List<TokenCardMeta?>?,
    )

    /**
     * 存储代币URL
     *
     * @param entries 图片条目列表
     */
    fun storeTokenUrl(entries: List<ImageEntry?>?)

    /**
     * 初始化NFT资产
     *
     * @param wallet 钱包信息
     * @param tokens 代币对象
     * @return 初始化后的代币对象
     */
    fun initNFTAssets(
        wallet: Wallet?,
        tokens: Token?,
    ): Token?

    /**
     * 获取代币元数据
     *
     * @param wallet 钱包信息
     * @param networkFilters 网络过滤器
     * @param svs 资产定义服务
     * @return 代币卡片元数据数组
     */
    suspend fun fetchTokenMetas(
        wallet: Wallet?,
        networkFilters: List<Long?>?,
        svs: AssetDefinitionService?,
    ): Array<TokenCardMeta?>?

    /**
     * 获取所有代币元数据
     *
     * @param wallet 钱包信息
     * @param networkFilters 网络过滤器
     * @param searchTerm 搜索词
     * @return 代币卡片元数据数组
     */
    suspend fun fetchAllTokenMetas(
        wallet: Wallet?,
        networkFilters: List<Long?>?,
        searchTerm: String?,
    ): Array<TokenCardMeta?>?

    /**
     * 获取需要更新的代币元数据
     *
     * @param wallet 钱包信息
     * @param networkFilters 网络过滤器
     * @return 代币卡片元数据数组
     */
    fun fetchTokenMetasForUpdate(
        wallet: Wallet?,
        networkFilters: List<Long?>?,
    ): Array<TokenCardMeta?>?

    /**
     * 获取所有有名称问题的代币
     *
     * @param walletAddress 钱包地址
     * @param networkFilters 网络过滤器
     * @return 代币数组
     */
    suspend fun fetchAllTokensWithNameIssue(
        walletAddress: String?,
        networkFilters: List<Long?>?,
    ): Array<Token?>?

    /**
     * 获取所有名称空白的代币
     *
     * @param walletAddress 钱包地址
     * @param networkFilters 网络过滤器
     * @return 合约地址数组
     */
    suspend fun fetchAllTokensWithBlankName(
        walletAddress: String?,
        networkFilters: List<Long?>?,
    ): Array<ContractAddress?>?

    /**
     * 修复完整名称
     *
     * @param wallet 钱包信息
     * @param svs 资产定义服务
     * @return 修复的数量
     */
    suspend fun fixFullNames(
        wallet: Wallet?,
        svs: AssetDefinitionService?,
    ): Int?

    /**
     * 更新以太坊价格信息
     *
     * @param ethTickers 以太坊价格映射
     */
    fun updateEthTickers(ethTickers: MutableMap<Long?, TokenTicker?>?)

    /**
     * 更新ERC20代币价格信息
     *
     * @param chainId 链ID
     * @param erc20Tickers ERC20代币价格映射
     */
    fun updateERC20Tickers(
        chainId: Long,
        erc20Tickers: MutableMap<String?, TokenTicker?>?,
    )

    /**
     * 移除过期的价格信息
     */
    fun removeOutdatedTickers()

    /**
     * 获取Realm实例
     *
     * @param wallet 钱包信息
     * @return Realm实例
     */
    fun getRealmInstance(wallet: Wallet?): Realm?

    /**
     * 价格Realm实例
     */
    val tickerRealmInstance: Realm?

    /**
     * 获取当前代币价格信息
     *
     * @param token 代币对象
     * @return 代币价格信息
     */
    fun getCurrentTicker(token: Token?): TokenTicker?

    /**
     * 获取当前代币价格信息
     *
     * @param key 代币键值
     * @return 代币价格信息
     */
    fun getCurrentTicker(key: String?): TokenTicker?

    /**
     * 设置可见性变更
     *
     * @param wallet 钱包信息
     * @param cAddr 合约地址
     */
    fun setVisibilityChanged(
        wallet: Wallet?,
        cAddr: ContractAddress?,
    )

    /**
     * 获取代币启用状态
     *
     * @param token 代币对象
     * @return 是否启用
     */
    fun getEnabled(token: Token?): Boolean

    /**
     * 更新NFT资产
     *
     * @param wallet 钱包地址
     * @param erc721Token ERC721代币
     * @param additions 添加的资产列表
     * @param removals 移除的资产列表
     */
    fun updateNFTAssets(
        wallet: String?,
        erc721Token: Token?,
        additions: List<BigInteger?>?,
        removals: List<BigInteger?>?,
    )

    /**
     * 存储资产
     *
     * @param wallet 钱包地址
     * @param token 代币对象
     * @param tokenId 代币ID
     * @param asset NFT资产
     */
    fun storeAsset(
        wallet: String?,
        token: Token?,
        tokenId: BigInteger?,
        asset: NFTAsset?,
    )

    /**
     * 获取总价值
     *
     * @param currentAddress 当前地址
     * @param networkFilters 网络过滤器
     * @return 总价值对
     */
    suspend fun getTotalValue(
        currentAddress: String?,
        networkFilters: List<Long?>?,
    ): Pair<Double?, Double?>?

    /**
     * 获取价格时间映射
     *
     * @param chainId 链ID
     * @param erc20Tokens ERC20代币列表
     * @return 价格时间映射
     */
    fun getTickerTimeMap(
        chainId: Long,
        erc20Tokens: List<TokenCardMeta?>?,
    ): Map<String?, Long?>?

    /**
     * 删除价格信息
     */
    fun deleteTickers()

    /**
     * 获取价格更新列表
     *
     * @param networkFilter 网络过滤器
     * @return 价格更新列表
     */
    suspend fun getTickerUpdateList(networkFilter: List<Long?>?): List<String?>?

    /**
     * 获取代币分组
     *
     * @param chainId 链ID
     * @param address 代币地址
     * @param type 合约类型
     * @return 代币分组
     */
    fun getTokenGroup(
        chainId: Long,
        address: String?,
        type: ContractType?,
    ): TokenGroup?

    /**
     * 更新价格信息
     *
     * @param chainId 链ID
     * @param address 代币地址
     * @param ticker 价格信息
     */
    fun updateTicker(
        chainId: Long,
        address: String?,
        ticker: TokenTicker?,
    )

    /**
     * 存储代币信息
     *
     * @param wallet 钱包信息
     * @param tInfo 代币信息
     * @param type 合约类型
     * @return 存储的代币信息
     */
    suspend fun storeTokenInfo(
        wallet: Wallet?,
        tInfo: TokenInfo?,
        type: ContractType?,
    ): TokenInfo?

    /**
     * 获取认证代币
     *
     * @param chainId 链ID
     * @param wallet 钱包信息
     * @param address 代币地址
     * @param attnId 认证ID
     * @return 认证代币
     */
    fun fetchAttestation(
        chainId: Long,
        wallet: Wallet?,
        address: String?,
        attnId: String?,
    ): Token?

    /**
     * 获取认证代币列表
     *
     * @param chainId 链ID
     * @param walletAddress 钱包地址
     * @param tokenAddress 代币地址
     * @return 认证代币列表
     */
    fun fetchAttestations(
        chainId: Long,
        walletAddress: String?,
        tokenAddress: String?,
    ): List<Token?>?

    /**
     * 获取认证代币列表
     *
     * @param walletAddress 钱包地址
     * @return 认证代币列表
     */
    fun fetchAttestations(walletAddress: String?): List<Token?>?
}
