package com.alphawallet.app.entity.tokens

import android.content.Context
import android.text.Html
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.Pair
import com.alphawallet.app.R
import com.alphawallet.app.entity.ContractInteract
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.EventSync
import com.alphawallet.app.entity.TicketRangeElement
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.TransactionInput
import com.alphawallet.app.entity.TransactionType
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.repository.EventResult
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.app.repository.entity.RealmToken
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.ui.widget.entity.ENSHandler
import com.alphawallet.app.ui.widget.entity.StatusType
import com.alphawallet.app.ui.widget.entity.TokenTransferData
import com.alphawallet.app.util.BalanceUtils
import com.alphawallet.token.entity.ContractAddress
import com.alphawallet.token.entity.TicketRange
import com.alphawallet.token.entity.TokenScriptResult
import com.alphawallet.token.tools.TokenDefinition
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Uint
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.EthLog
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Token 实体类
 *
 * 代表区块链上的代币，包含代币的基本信息、余额、交易历史等核心功能。
 * 支持多种代币类型：ERC20、ERC721、ERC1155、以太坊原生代币等。
 *
 * 主要功能：
 * - 代币基本信息管理（名称、符号、地址、精度等）
 * - 余额查询和更新
 * - 交易记录处理
 * - NFT 资产管理
 * - TokenScript 支持
 * - 事件同步和合约交互
 *
 * 技术特点：
 * - 使用 Kotlin 协程替代 RxJava 进行异步操作
 * - 支持多链代币管理
 * - 集成 TokenScript 功能
 * - 提供完整的代币生命周期管理
 *
 * @param tokenInfo 代币基本信息
 * @param balance 当前余额
 * @param updateBlancaTime 余额更新时间
 * @param networkName 网络名称
 * @param type 合约类型
 */
open class Token(
    /** 代币基本信息 */
    val tokenInfo: TokenInfo,
    /** 当前余额 */
    var balance: BigDecimal,
    /** 余额更新时间戳 */
    var updateBlancaTime: Long,
    /** 网络简称 */
    private val shortNetworkName: String,
    /** 合约类型 */
    var contractType: ContractType
) {

    companion object {
        /** 代币余额显示精度 */
        const val TOKEN_BALANCE_PRECISION = 4

        /** 代币余额焦点显示精度 */
        const val TOKEN_BALANCE_FOCUS_PRECISION = 5

        /** 代币符号最大长度 */
        const val MAX_TOKEN_SYMBOL_LENGTH = 8
    }

    // ==================== 余额相关属性 ====================

    /** 待确认余额 */
    var pendingBalance: BigDecimal = balance

    /** 余额是否发生变化 */
    var balanceChanged: Boolean = false

    // ==================== 区块链同步相关属性 ====================

    /** 最后检查的区块高度 */
    var lastBlockCheck: Long = 0

    /** 最后交易检查时间 */
    var lastTxCheck: Long = 0

    /** 最后交易时间 */
    var lastTxTime: Long = 0

    // ==================== UI 相关属性 ====================

    /** 钱包UI是否需要更新 */
    var walletUIUpdateRequired: Boolean = false

    /** 列表项视图高度 */
    var itemViewHeight: Int = 0

    // ==================== TokenScript 相关属性 ====================

    /** 是否有TokenScript */
    var hasTokenScript: Boolean = false

    /** 代币分组 */
    var group: TokenGroup = TokenGroup.ASSET

    // ==================== 钱包地址 ====================

    /** 代币所属钱包地址 */
    private var tokenWallet: String? = null

    // ==================== 事件同步和合约交互 ====================

    /** 事件同步器 */
    protected val eventSync: EventSync = EventSync(this)

    /** 合约交互器 */
    protected val contractInteract: ContractInteract = ContractInteract(this)

    // ==================== 结果缓存 ====================

    /** TokenScript 结果缓存映射 - 按 tokenId 存储属性结果 */
    private val resultMap: MutableMap<BigInteger, MutableMap<String, TokenScriptResult.Attribute>> = ConcurrentHashMap()

    /** 功能可用性映射 */
    private var functionAvailabilityMap: Map<BigInteger, List<String>>? = null

    /**
     * 初始化块
     * 设置默认值并清理缓存
     */
    init {
        // 确保余额不为空
        if (this.balance == null) {
            this.balance = BigDecimal.ZERO
        }

        // 初始化时间戳
        this.lastTxCheck = 0
        this.lastBlockCheck = 0
        this.lastTxTime = 0

        // 初始化状态标志
        this.balanceChanged = false
        this.walletUIUpdateRequired = false
        this.hasTokenScript = false

        // 清理结果缓存
        this.resultMap.clear()

        // 设置默认分组
        if (this.group == null) {
            this.group = TokenGroup.ASSET
        }
    }

    // ==================== 网络相关方法 ====================

    /**
     * 获取网络名称
     * @return 网络简称
     */
    fun getNetworkName(): String = shortNetworkName

    // ==================== 余额相关方法 ====================

    /**
     * 获取UI显示用的格式化余额字符串
     * @param decimalPlaces 小数位数
     * @return 格式化的余额字符串
     */
    open fun getStringBalanceForUI(decimalPlaces: Int): String {
        return BalanceUtils.getScaledValueFixed(balance, tokenInfo.decimals.toLong(), decimalPlaces)
    }

    /**
     * 检查是否有正余额
     * @return 如果余额大于0返回true
     */
    open fun hasPositiveBalance(): Boolean {
        return balance > BigDecimal.ZERO
    }

    /**
     * 获取完整余额字符串
     * @return 完整的余额字符串
     */
    open fun getFullBalance(): String {
        return BalanceUtils.getScaledValueScientific(balance, tokenInfo.decimals.toLong())
    }

    /**
     * 获取修正后的余额
     * @param scale 精度
     * @return 修正后的余额
     */
    fun getCorrectedBalance(scale: Int): BigDecimal {
        if (balance == BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }

        val decimals = tokenInfo.decimals.toInt()
        val decimalDivisor = BigDecimal.valueOf(Math.pow(10.0, decimals.toDouble()))

        return if (decimals > 0) {
            try {
                balance.divide(decimalDivisor, scale, RoundingMode.DOWN).stripTrailingZeros()
            } catch (e: Exception) {
                BigDecimal.ZERO
            }
        } else {
            balance
        }
    }

    /**
     * 获取原始余额
     * @return 原始余额（BigDecimal格式）
     */
    open fun getBalanceRaw(): BigDecimal {
        return balance
    }

    /**
     * 获取固定格式的余额字符串
     * @return 固定格式的余额字符串
     */
    fun getFixedFormattedBalance(): String {
        return getStringBalanceForUI(TOKEN_BALANCE_PRECISION)
    }

    // ==================== NFT 资产相关方法 ====================

    /**
     * 根据tokenId获取NFT资产
     * @param tokenId 代币ID（字符串格式）
     * @return NFT资产对象，如果不存在返回null
     */
    open fun getAssetForToken(tokenId: String): NFTAsset? {
        return null
    }

    /**
     * 根据tokenId获取NFT资产
     * @param tokenId 代币ID（BigInteger格式）
     * @return NFT资产对象，如果不存在返回null
     */
    open fun getAssetForToken(tokenId: BigInteger): NFTAsset? {
        return null
    }

    /**
     * 获取唯一的代币ID列表
     * @return 代币ID列表
     */
    open fun getUniqueTokenIds(): List<BigInteger> {
        val tokenIds = mutableListOf<BigInteger>()
        val tokenAssets = getTokenAssets()

        if (tokenAssets.isNotEmpty()) {
            for (tokenId in tokenAssets.keys) {
                if (!tokenIds.contains(tokenId)) {
                    tokenIds.add(tokenId)
                }
            }
        }

        return tokenIds
    }

    /**
     * 添加NFT资产到代币余额资产中
     * @param tokenId 代币ID
     * @param asset NFT资产
     */
    open fun addAssetToTokenBalanceAssets(tokenId: BigInteger, asset: NFTAsset) {
        // 子类实现
    }

    /**
     * 获取代币资产映射
     * @return 代币ID到NFT资产的映射
     */
    open fun getTokenAssets(): Map<BigInteger, NFTAsset> {
        return emptyMap()
    }

    /**
     * 检查是否为非同质化代币
     * @return 如果是NFT返回true，否则返回false
     */
    open fun isNonFungible(): Boolean = false

    /**
     * 获取代币数量
     * @return 代币数量
     */
    open fun getTokenCount(): Int {
        return 0
    }

    open fun contractTypeValid():Boolean {
        return !(contractType == ContractType.NOT_SET || contractType == ContractType.OTHER)
    }
    /**
     * 返回代币的数组余额（针对 ERC-875/721 等 NFT 类型）
     * 默认返回空列表，由子类覆盖具体实现
     */
    open fun getArrayBalance(): List<BigInteger> = emptyList()

    /**
     * 返回移除零值后的数组余额
     * 默认返回空列表，由子类覆盖具体实现
     */
    open fun getNonZeroArrayBalance(): List<BigInteger> = emptyList()

    /**
     * 获取集合映射（默认无集合信息）
     */
    open fun getCollectionMap(): Map<BigInteger, NFTAsset> = emptyMap()

    /**
     * 检查是否有数组余额
     * @return 如果有数组余额返回true
     */
    open fun hasArrayBalance(): Boolean = false

    /**
     * 获取NFT元数据
     * @param tokenId 代币ID
     * @return NFT资产对象
     */
    open fun fetchTokenMetadata(tokenId: BigInteger): NFTAsset? {
        return null
    }

    /**
     * 构建资产列表（协程版本）
     * @param transferData 转账数据
     * @return NFT资产列表
     */
    suspend fun buildAssetList(transferData: TokenTransferData?): List<NFTAsset> = withContext(Dispatchers.IO) {
        val assets = mutableListOf<NFTAsset>()

        if (transferData == null || !isNonFungible()) {
            return@withContext assets
        }

        val result = transferData.eventResultMap
        val amounts = result["amount"]
        val counts = result["value"]

        if (amounts == null) {
            return@withContext assets
        }

        for (i in amounts.values.indices) {
            val tokenId = amounts.values[i]
            val count = if (counts == null || counts.values.size <= i) "1" else counts.values[i]

            var asset = tokenId?.let { getAssetForToken(it) }
            if (asset == null) {
                asset = fetchTokenMetadata(BigInteger(tokenId))
            }

            asset?.let {
                it.setSelectedBalance(BigDecimal(count))
                assets.add(it)
            }
        }

        assets
    }

    // ==================== 地址和合约相关方法 ====================

    /**
     * 获取合约地址对象
     * @return 合约地址对象
     */
    fun getContractAddress(): ContractAddress {
        return ContractAddress(tokenInfo.chainId, tokenInfo.address)
    }

    /**
     * 获取代币地址
     * @return 代币合约地址
     */
    fun getAddress(): String {
        return tokenInfo.address ?: ""
    }

    /**
     * 获取钱包地址
     * @return 钱包地址
     */
    fun getWallet(): String {
        return tokenWallet ?: ""
    }

    /**
     * 设置代币钱包地址
     * @param address 钱包地址
     */
    fun setTokenWallet(address: String?) {
        this.tokenWallet = address
    }

    // ==================== 名称和符号相关方法 ====================

    /**
     * 获取完整名称
     * @return 代币完整名称
     */
    fun getFullName(): String? {
        val name = tokenInfo.name
        return if (!TextUtils.isEmpty(name)) {
            name
        } else {
            tokenInfo.symbol
        }
    }

    /**
     * 获取完整名称（带资产定义服务和数量）
     * @param assetDefinition 资产定义服务
     * @param count 数量
     * @return 完整名称
     */
    fun getFullName(assetDefinition: AssetDefinitionService, count: Int): String? {
        val name = getName(assetDefinition, count)
        return if (!TextUtils.isEmpty(name)) {
            name
        } else {
            tokenInfo.symbol
        }
    }

    /**
     * 获取代币符号
     * @param token 代币对象
     * @return 代币符号
     */
    fun getTokenSymbol(token: Token): String? {
        val symbol = token.tokenInfo.symbol
        return if (!TextUtils.isEmpty(symbol)) {
            symbol
        } else {
            "UNK"
        }
    }

    /**
     * 获取TokenScript名称
     * @param assetDefinition 资产定义服务
     * @param count 数量
     * @return TokenScript名称
     */
    fun getTSName(assetDefinition: AssetDefinitionService, count: Int): String? {
        return getName(assetDefinition, count)
    }

    /**
     * 获取名称（带资产定义服务和数量）
     * @param assetDefinition 资产定义服务
     * @param count 数量
     * @return 代币名称
     */
    fun getName(assetDefinition: AssetDefinitionService, count: Int): String? {
        val name = getName()
        return if (!TextUtils.isEmpty(name)) {
            name
        } else {
            tokenInfo.symbol
        }
    }

    /**
     * 获取名称
     * @return 代币名称
     */
    fun getName(): String? {
        val name = tokenInfo.name
        return if (!TextUtils.isEmpty(name)) {
            name?.let { sanitiseString(it) }
        } else {
            tokenInfo.symbol
        }
    }

    /**
     * 获取符号
     * @return 代币符号
     */
    fun getSymbol(): String? {
        return getShortestNameOrSymbol()
    }

    /**
     * 获取短符号
     * @return 短符号（最多8个字符）
     */
    fun getShortSymbol(): String? {
        val symbol = tokenInfo.symbol ?: return null
        return if (symbol.length > MAX_TOKEN_SYMBOL_LENGTH) {
            symbol.substring(0, MAX_TOKEN_SYMBOL_LENGTH)
        } else {
            symbol
        }
    }

    /**
     * 获取短名称
     * @return 短名称
     */
    fun getShortName(): String? {
        return getShortestNameOrSymbol()
    }

    /**
     * 获取代币标题
     * @return 代币标题
     */
    fun getTokenTitle(): String? {
        return getShortestNameOrSymbol()
    }

    /**
     * 获取代币名称（带资产服务和数量）
     * @param assetService 资产定义服务
     * @param count 数量
     * @return 代币名称
     */
    fun getTokenName(assetService: AssetDefinitionService, count: Int): String? {
        val name = getName(assetService, count)

        return when {
            isNonFungible() && !TextUtils.isEmpty(name) -> {
                name
            }

            !TextUtils.isEmpty(tokenInfo.name) -> {
                tokenInfo.name
            }

            !TextUtils.isEmpty(tokenInfo.symbol) -> {
                tokenInfo.symbol
            }

            else -> {
                "Unknown"
            }
        }
    }

    /**
     * 清理字符串
     * @param str 输入字符串
     * @return 清理后的字符串
     */
    private fun sanitiseString(str: String): String {
        var result = str

        // 移除HTML标签
        if (result.contains("<")) {
            result = Html.fromHtml(result, Html.FROM_HTML_MODE_COMPACT).toString()
        }

        // 移除换行符
        if (result.contains("\n")) {
            result = result.replace("\n", " ")
        }

        // 移除多余空格
        if (result.contains("  ")) {
            result = result.replace("  ", " ")
        }

        return result.trim()
    }

    /**
     * 获取最短的名称或符号
     * @return 最短的名称或符号
     */
    private fun getShortestNameOrSymbol(): String? {
        val symbol = tokenInfo.symbol
        val name = tokenInfo.name

        val symbolLength = if (!symbol.isNullOrEmpty()) symbol.length else 0
        val nameLength = if (!name.isNullOrEmpty()) name.length else 0

        return when {
            symbolLength == 0 && nameLength == 0 -> ""
            nameLength > 0 && (symbolLength > nameLength || symbolLength == 0) -> name
            !symbol.isNullOrEmpty() -> symbol
            else -> name
        }
    }

    // ==================== 数值转换相关方法 ====================

    /**
     * 获取十进制值
     * @param value 字符串值
     * @return 十进制值
     */
    protected fun getDecimalValue(value: String): BigDecimal {
        return try {
            if (TextUtils.isEmpty(value)) {
                BigDecimal.ZERO
            } else {
                BigDecimal(value)
            }
        } catch (e: NumberFormatException) {
            try {
                // 尝试解析十六进制
                val hexValue = if (value.startsWith("0x")) value.substring(2) else value
                BigDecimal(BigInteger(hexValue, 16))
            } catch (e2: NumberFormatException) {
                BigDecimal.ZERO
            }
        }
    }

    // ==================== 合约类型相关方法 ====================

    /**
     * 获取合约类型
     * @return 合约类型的序号
     */
    open fun getContractType(): Int {
        return when (contractType) {
            ContractType.NOT_SET -> 0
            ContractType.OTHER -> 1
            ContractType.CREATION -> 2
            ContractType.ETHEREUM -> 3
            ContractType.ERC20 -> 4
            ContractType.ERC721 -> 5
            ContractType.ERC721_LEGACY -> 6
            ContractType.ERC721_TICKET -> 7
            ContractType.ERC721_UNDETERMINED -> 8
            ContractType.ERC875_LEGACY -> 9
            ContractType.ERC875 -> 10
            ContractType.CURRENCY -> 11
            ContractType.DELETED_ACCOUNT -> 12
            ContractType.ERC1155 -> 13
            else -> 0
        }
    }

    /**
     * 设置接口规范
     * @param type 合约类型
     */
    fun setInterfaceSpec(type: ContractType) {
        this.contractType = type
    }

    /**
     * 获取接口规范
     * @return 合约类型
     */
    fun getInterfaceSpec(): ContractType {
        return this.contractType
    }

    /**
     * 设置为以太坊代币
     */
    fun setIsEthereum() {
        this.contractType = ContractType.ETHEREUM
    }

    // ==================== 代币类型判断方法 ====================

    /**
     * 检查是否为代币
     * @return 如果是代币返回true
     */
    open fun isToken(): Boolean {
        return contractType != ContractType.ETHEREUM
    }

    /**
     * 检查是否为ERC20代币
     * @return 如果是ERC20返回true
     */
    fun isERC20(): Boolean {
        return contractType == ContractType.ERC20
    }

    /**
     * 检查是否为ERC721代币
     * @return 如果是ERC721返回true
     */
    open fun isERC721(): Boolean {
        return false
    }

    open fun isERC721Ticket(): Boolean {
        return false
    }
//    fun isERC875(): Boolean {
//        return false
//    }


    /**
     * 检查是否为以太坊原生代币
     * @return 如果是以太坊返回true
     */
    fun isEthereum(): Boolean {
        return contractType == ContractType.ETHEREUM
    }

    /**
     * 检查是否为无效代币
     * @return 如果是无效代币返回true
     */
    fun isBad(): Boolean {
        return contractType == ContractType.DELETED_ACCOUNT
    }

    /**
     * 检查是否有真实价值
     * @return 如果有真实价值返回true
     */
    fun hasRealValue(): Boolean {
        return balance.compareTo(BigDecimal.ZERO) > 0
    }

    // ==================== Realm 数据库相关方法 ====================

    /**
     * 设置Realm余额
     * @param realmToken Realm代币对象
     */
   open fun setRealmBalance(realmToken: RealmToken) {
        val realmBalance = realmToken.balance
        if (!realmBalance.isNullOrEmpty()) {
            this.balance = getDecimalValue(realmBalance)
        } else {
            this.balance = BigDecimal.ZERO
        }
    }

    /**
     * 设置Realm代币
     * @param realmToken Realm代币对象
     */
    fun setupRealmToken(realmToken: RealmToken) {
        this.lastBlockCheck = realmToken.lastBlock
        this.lastTxCheck = realmToken.lastTxTime
        setRealmInterfaceSpec(realmToken)
    }

    /**
     * 设置Realm接口规范
     * @param realmToken Realm代币对象
     */
    fun setRealmInterfaceSpec(realmToken: RealmToken) {
        val typeOrdinal = realmToken.interfaceSpec
        if (typeOrdinal < ContractType.values().size) {
            this.contractType = ContractType.values()[typeOrdinal]
        }
    }

    /**
     * 设置Realm最后区块
     * @param realmToken Realm代币对象
     */
    fun setRealmLastBlock(realmToken: RealmToken) {
        this.lastBlockCheck = realmToken.lastBlock
        this.lastTxTime = realmToken.lastTxTime
    }

    /**
     * 检查Realm余额变化
     * @param realmToken Realm代币对象
     * @return 如果余额发生变化返回true
     */
   open fun checkRealmBalanceChange(realmToken: RealmToken): Boolean {
        val realmBalance = realmToken.balance
        val currentBalance = if (!realmBalance.isNullOrEmpty()) {
            getDecimalValue(realmBalance)
        } else {
            BigDecimal.ZERO
        }

        return balance.compareTo(currentBalance) != 0
    }

    /**
     * 检查余额变化
     * @param oldToken 旧代币对象
     * @return 如果余额发生变化返回true
     */
    open fun checkBalanceChange(oldToken: Token): Boolean {
        return balance.compareTo(oldToken.balance) != 0
    }

    open fun updateBalance(realm: Realm) : BigDecimal { 
        return BigDecimal.ZERO
    }

    /**
     * 检查信息是否需要更新
     * @param realmToken Realm代币对象
     * @return 如果需要更新返回true
     */
    fun checkInfoRequiresUpdate(realmToken: RealmToken): Boolean {
        return tokenInfo.name != realmToken.name || tokenInfo.symbol != realmToken.symbol
    }

    // ==================== 待确认余额相关方法 ====================

    /**
     * 获取待确认差额
     * @return 待确认差额字符串
     */
    fun getPendingDiff(): String {
        val diff = pendingBalance.subtract(balance)
        return when {
            diff.compareTo(BigDecimal.ZERO) > 0 -> "+${BalanceUtils.getScaledValueFixed(diff, tokenInfo.decimals.toLong(), TOKEN_BALANCE_PRECISION)}"
            diff.compareTo(BigDecimal.ZERO) < 0 -> BalanceUtils.getScaledValueFixed(diff, tokenInfo.decimals.toLong(), TOKEN_BALANCE_PRECISION)
            else -> ""
        }
    }

    // ==================== 转账功能相关方法 ====================

    /**
     * 获取转账函数
     * @param to 接收地址
     * @param transferData 转账数据
     * @return 转账函数
     * @throws NumberFormatException 数字格式异常
     */
    @Throws(NumberFormatException::class)
    open fun getTransferFunction(to: String, transferData: List<BigInteger>): Function? {
        return null
    }

    /**
     * 获取转账字节数据
     * @param to 接收地址
     * @param transferData 转账数据
     * @return 转账字节数组
     */
    open fun getTransferBytes(to: String, transferData: ArrayList<Pair<BigInteger, NFTAsset>>): ByteArray? {
        return null
    }

    open fun hasGroupedTransfer(): Boolean = false

    /**
     * 获取生成通行证函数
     * @param expiry 过期时间
     * @param tokenIds 代币ID列表
     * @param v 签名参数v
     * @param r 签名参数r
     * @param s 签名参数s
     * @param recipient 接收者
     * @return 生成通行证函数
     */
    open fun getSpawnPassToFunction(
        expiry: BigInteger,
        tokenIds: List<BigInteger>,
        v: Int,
        r: ByteArray,
        s: ByteArray,
        recipient: String
    ): Function? {
        return null
    }

    /**
     * 获取交易函数
     * @param expiry 过期时间
     * @param tokenIds 代币ID列表
     * @param v 签名参数v
     * @param r 签名参数r
     * @param s 签名参数s
     * @return 交易函数
     */
    open fun getTradeFunction(
        expiry: BigInteger,
        tokenIds: List<BigInteger>,
        v: Int,
        r: ByteArray,
        s: ByteArray
    ): Function? {
        return null
    }

    /**
     * 检查选择有效性
     * @param selection 选择的ID列表
     * @return 如果选择有效返回true
     */
    open fun checkSelectionValidity(selection: List<BigInteger>): Boolean {
        return true
    }

    /**
     * 获取转账列表格式
     * @param tokenIds 代币ID列表
     * @return 转账列表格式
     */
    open fun getTransferListFormat(tokenIds: List<BigInteger>): List<BigInteger> {
        return tokenIds
    }

    /**
     * 检查是否支持批量转账
     * @return 如果支持批量转账返回true
     */
    open fun isBatchTransferAvailable(): Boolean {
        return false
    }

    /**
     * 获取动态数组
     * @param indices 索引列表
     * @return 动态数组
     */
    protected open fun getDynArray(indices: List<BigInteger>): DynamicArray<out Uint> {
        val uintList = indices.map { Uint(it) }
        return DynamicArray(Uint::class.java, uintList)
    }

    // ==================== 交易相关方法 ====================

    /**
     * 获取操作名称
     * @param transaction 交易对象
     * @param ctx 上下文
     * @return 操作名称
     */
    fun getOperationName(transaction: Transaction, ctx: Context): String? {
        return if (isEthereum() && !transaction.hasInput()) {
            when {
                transaction.value == "0" && transaction.hasInput() -> ctx.getString(R.string.contract_call)
                transaction.from.equals(tokenWallet, ignoreCase = true) -> ctx.getString(R.string.sent)
                else -> ctx.getString(R.string.received)
            }
        } else {
            transaction.getOperationName(ctx, this, getWallet())
        }
    }

    /**
     * 获取交易类型
     * @param transaction 交易对象
     * @return 交易类型
     */
    fun getTransactionType(transaction: Transaction): TransactionType {
        return if (isEthereum() && !transaction.hasInput()) {
            when {
                transaction.value == "0" && transaction.hasInput() -> TransactionType.CONTRACT_CALL
                transaction.from.equals(tokenWallet, ignoreCase = true) -> TransactionType.SEND
                else -> TransactionType.RECEIVED
            }
        } else {
            transaction.getTransactionType(this, getWallet())
        }
    }

    /**
     * 获取交易值
     * @param transaction 交易对象
     * @param precision 精度
     * @return 交易值字符串
     */
    fun getTransactionValue(transaction: Transaction, precision: Int): String {
        return when {
            transaction.hasError() -> ""
            transaction.value == "0" || transaction.value == "0x0" -> "0"
            else -> transaction.getPrefix(this) + BalanceUtils.getScaledValueFixed(
                BigDecimal(transaction.value),
                tokenInfo.decimals.toLong(),
                precision
            )
        }
    }

    /**
     * 获取交易值
     * @param transaction 交易对象
     * @return 交易值字符串
     */
    fun getTransactionValue(transaction: Transaction): String {
        return when {
            transaction.hasError() -> ""
            transaction.value == "0" || transaction.value == "0x0" -> "0"
            else -> BalanceUtils.getScaledValue(transaction.value, tokenInfo.decimals.toLong())
        }
    }

    /**
     * 获取交易结果值
     * @param transaction 交易对象
     * @param precision 精度
     * @return 交易结果值字符串
     */
   open fun getTransactionResultValue(transaction: Transaction, precision: Int): String {
        return when {
            isEthereum() && !transaction.hasInput() -> getTransactionValue(transaction, precision) + " " + (getSymbol() ?: "")
            transaction.hasInput() -> transaction.getOperationResult(this, precision)
            else -> ""
        }
    }

    /**
     * 获取交易结果值
     * @param transaction 交易对象
     * @return 交易结果值字符串
     */
    fun getTransactionResultValue(transaction: Transaction): String {
        return when {
            isEthereum() && !transaction.hasInput() -> getTransactionValue(transaction) + " " + (getSymbol() ?: "")
            transaction.hasInput() -> transaction.getOperationResult(this)
            else -> ""
        }
    }

    /**
     * 检查是否应该显示符号
     * @param transaction 交易对象
     * @return 如果应该显示符号返回true
     */
    fun shouldShowSymbol(transaction: Transaction): Boolean {
        return (isEthereum() && !transaction.hasInput()) || transaction.shouldShowSymbol(this)
    }

    /**
     * 获取是否为发送交易
     * @param transaction 交易对象
     * @return 如果是发送交易返回true
     */
    open fun getIsSent(transaction: Transaction): Boolean {
        return if (isEthereum()) {
            transaction.from.equals(tokenWallet, ignoreCase = true)
        } else {
            transaction.getIsSent(getWallet())
        }
    }

    /**
     * 获取交易详情
     * @param ctx 上下文
     * @param tx 交易对象
     * @param tService 代币服务
     * @return 交易详情字符串
     */
    fun getTransactionDetail(ctx: Context, tx: Transaction, tService: TokensService): String {
        return if (isEthereum()) {
            ctx.getString(
                R.string.operation_definition,
                ctx.getString(getToFromText(tx)),
                ENSHandler.matchENSOrFormat(ctx, getTransactionDestination(tx))
            )
        } else {
            tx.getOperationDetail(ctx, this, tService)
        }
    }

    /**
     * 获取交易目标地址
     * @param transaction 交易对象
     * @return 目标地址
     */
    fun getTransactionDestination(transaction: Transaction): String {
        return if (isEthereum()) {
            if (transaction.from.equals(tokenWallet, ignoreCase = true)) {
                transaction.to
            } else {
                transaction.from
            }
        } else {
            transaction.getDestination(this)
        }
    }

    /**
     * 获取以太坊交易图像状态
     * @param tx 交易对象
     * @return 状态类型
     */
    fun ethereumTxImage(tx: Transaction): StatusType {
        return if (tx.from.equals(tokenWallet, ignoreCase = true)) {
            if (tx.to.equals(tx.from, ignoreCase = true)) {
                StatusType.SELF
            } else {
                StatusType.SENT
            }
        } else {
            StatusType.RECEIVE
        }
    }

    /**
     * 获取交易状态
     * @param transaction 交易对象
     * @return 状态类型
     */
    fun getTxStatus(transaction: Transaction): StatusType {
        val status = transaction.getTransactionStatus()
        return if (status != null) {
            status
        } else {
            if (isEthereum()) {
                ethereumTxImage(transaction)
            } else {
                transaction.getOperationImage(this)
            }
        }
    }

    /**
     * 获取发送/接收文本
     * @param transaction 交易对象
     * @return 文本资源ID
     */
    fun getToFromText(transaction: Transaction): Int {
        return if (isEthereum()) {
            if (getIsSent(transaction)) {
                R.string.to
            } else {
                R.string.from_op
            }
        } else {
            transaction.getOperationToFrom(getWallet())
        }
    }

    /**
     * 获取转账值
     * @param txInput 交易输入
     * @param transactionBalancePrecision 交易余额精度
     * @return 转账值字符串
     */
    open fun getTransferValue(txInput: TransactionInput, transactionBalancePrecision: Int): String {
        val value = getTransferValueRaw(txInput)
        return if (value > BigInteger.ZERO) {
            BalanceUtils.getScaledValueMinimal(BigDecimal(value), tokenInfo.decimals.toLong(), transactionBalancePrecision)
        } else {
            "0"
        }
    }

    /**
     * 获取原始转账值
     * @param txInput 交易输入
     * @return 原始转账值
     */
    open fun getTransferValueRaw(txInput: TransactionInput): BigInteger {
        return if (txInput.miscData.isNotEmpty()) {
            try {
                BigInteger(txInput.miscData[0], 16)
            } catch (e: NumberFormatException) {
                BigInteger.ZERO
            }
        } else {
            BigInteger.ZERO
        }
    }

    // ==================== 字符串处理相关方法 ====================

    /**
     * 将代币ID字符串转换为索引列表
     * @param userList 用户列表字符串
     * @return 代币ID列表
     */
    open fun ticketIdStringToIndexList(userList: String): List<BigInteger> {
        return emptyList()
    }

    /**
     * 将十六进制字符串转换为BigInteger列表
     * @param integerString 整数字符串
     * @return BigInteger列表
     */
    fun stringHexToBigIntegerList(integerString: String): List<BigInteger> {
        val result = mutableListOf<BigInteger>()

        try {
            val hexValues = integerString.split(",")
            for (hexValue in hexValues) {
                val trimmed = hexValue.trim()
                if (trimmed.isNotEmpty()) {
                    val value = if (trimmed.startsWith("0x")) {
                        BigInteger(trimmed.substring(2), 16)
                    } else {
                        BigInteger(trimmed, 16)
                    }
                    result.add(value)
                }
            }
        } catch (e: NumberFormatException) {
            // 忽略格式错误
        }

        return result
    }

    /**
     * 转换值
     * @param prefix 前缀
     * @param vResult 事件结果
     * @param precision 精度
     * @return 转换后的值字符串
     */
    open fun convertValue(prefix: String, vResult: EventResult, precision: Int): String {
        val decimalValue = vResult.value?.let { getDecimalValue(it) }
        return prefix + decimalValue?.let { BalanceUtils.getScaledValueFixed(it, tokenInfo.decimals.toLong(), precision) }
    }

    // ==================== 修剪和分组相关方法 ====================

    /**
     * 修剪ID列表
     * @param ticketIds 代币ID字符串
     * @param quantity 数量
     * @return 修剪后的ID列表
     */
    open fun pruneIDList(ticketIds: String, quantity: Int): List<BigInteger> {
        return emptyList()
    }

    /**
     * 与代币分组
     * @param currentRange 当前范围
     * @param e 范围元素
     * @param currentTime 当前时间
     * @return 如果可以分组返回true
     */
    open fun groupWithToken(currentRange: TicketRange, e: TicketRangeElement, currentTime: Long): Boolean {
        return false
    }

    // ==================== 时间检查相关方法 ====================

    /**
     * 获取交易检查间隔
     * @return 检查间隔（毫秒）
     */
    fun getTransactionCheckInterval(): Long {
        return when {
            lastTxTime > (System.currentTimeMillis() - DateUtils.WEEK_IN_MILLIS) -> 30 * DateUtils.SECOND_IN_MILLIS
            lastTxTime > (System.currentTimeMillis() - DateUtils.DAY_IN_MILLIS) -> 5 * DateUtils.MINUTE_IN_MILLIS
            else -> 15 * DateUtils.MINUTE_IN_MILLIS
        }
    }

    /**
     * 检查是否需要交易检查
     * @return 如果需要检查返回true
     */
    fun needsTransactionCheck(): Boolean {
        val currentTime = System.currentTimeMillis()
        val checkInterval = getTransactionCheckInterval()

        return when {
            lastTxCheck == 0L -> true
            (currentTime - lastTxCheck) > checkInterval -> true
            balanceChanged -> true
            else -> false
        }
    }

    /**
     * 检查是否可能需要刷新
     * @return 如果可能需要刷新返回true
     */
    fun mayRequireRefresh(): Boolean {
        val currentTime = System.currentTimeMillis()
        return when {
            lastBlockCheck == 0L -> true
            (currentTime - lastBlockCheck) > (5 * DateUtils.MINUTE_IN_MILLIS) -> true
            balanceChanged -> true
            else -> false
        }
    }

    // ==================== 标准功能相关方法 ====================

    /**
     * 获取标准功能列表
     * @return 标准功能ID列表
     */
    open fun getStandardFunctions(): List<Int> {
        return emptyList()
    }

    /**
     * 从交易中获取资产列表
     * @param tx 交易对象
     * @return NFT资产列表
     */
    open fun getAssetListFromTransaction(tx: Transaction): List<NFTAsset> {
        return emptyList()
    }

    /**
     * 查询资产
     * @param assetMap 资产映射
     * @return 查询后的资产映射
     */
    open fun queryAssets(assetMap: Map<BigInteger, NFTAsset>): Map<BigInteger, NFTAsset> {
        return assetMap
    }

    /**
     * 获取资产变化
     * @param oldAssetList 旧资产列表
     * @return 资产变化映射
     */
    open fun getAssetChange(oldAssetList: Map<BigInteger, NFTAsset>): Map<BigInteger, NFTAsset> {
        val currentAssets = getTokenAssets()
        val changes = mutableMapOf<BigInteger, NFTAsset>()

        // 检查新增或变化的资产
        for ((tokenId, asset) in currentAssets) {
            val oldAsset = oldAssetList[tokenId]
            if (oldAsset == null || oldAsset != asset) {
                changes[tokenId] = asset
            }
        }

        return changes
    }

    // ==================== 协程版本的URI获取方法 ====================

    /**
     * 获取脚本URI（协程版本）
     * @return 脚本URI列表
     */
    open suspend fun getScriptURI(): List<String> = withContext(Dispatchers.IO) {
        try {
            contractInteract.getScriptFileURIAsync()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取合约URI（协程版本）
     * @return 合约URI
     */
    suspend fun getContractURI(): String = withContext(Dispatchers.IO) {
        try {
            contractInteract.getContractURIResultAsync()
        } catch (e: Exception) {
            ""
        }
    }

    // ==================== 事件过滤器相关方法 ====================

    /**
     * 获取接收余额过滤器
     * @param event 事件
     * @param startBlock 开始区块
     * @param endBlock 结束区块
     * @return 以太坊过滤器
     */
    open fun getReceiveBalanceFilter(
        event: Event,
        startBlock: DefaultBlockParameter,
        endBlock: DefaultBlockParameter
    ): EthFilter? {
        return null
    }

    /**
     * 获取发送余额过滤器
     * @param event 事件
     * @param startBlock 开始区块
     * @param endBlock 结束区块
     * @return 以太坊过滤器
     */
    open fun getSendBalanceFilter(
        event: Event,
        startBlock: DefaultBlockParameter,
        endBlock: DefaultBlockParameter
    ): EthFilter? {
        return null
    }

    /**
     * 处理日志并存储转账事件
     * @param receiveLogs 接收日志
     * @param event 事件
     * @param txHashes 交易哈希集合
     * @param realm Realm数据库实例
     * @return 处理的代币ID集合
     */
    open fun processLogsAndStoreTransferEvents(
        receiveLogs: EthLog,
        event: Event,
        txHashes: HashSet<String>,
        realm: Realm
    ): HashSet<BigInteger>? {
        return null
    }

    /**
     * 添加资产元素
     * @param asset NFT资产
     * @param ctx 上下文
     */
    open fun addAssetElements(asset: NFTAsset, ctx: Context) {
        // 子类实现
    }

    // ==================== TokenScript 相关方法 ====================

    /**
     * 获取TokenScript键
     * @return TokenScript键
     */
    open fun getTSKey(): String {
        return if (isEthereum()) {
            "ethereum-${tokenInfo.chainId}"
        } else {
            "${tokenInfo.address?.lowercase() ?: ""}-${tokenInfo.chainId}"
        }
    }

    /**
     * 获取数据库键
     * @return 数据库键
     */
   open fun getDatabaseKey(): String {
        return TokensRealmSource.databaseKey(tokenInfo.chainId, tokenInfo.address ?: "")
    }

    /**
     * 获取TokenScript键（带TokenDefinition）
     * @param td TokenDefinition对象
     * @return TokenScript键
     */
    open fun getTSKey(td: TokenDefinition): String {
        return getTSKey()
    }

    /**
     * 获取内在类型
     * @param name 类型名称
     * @return 类型对象
     */
    open fun getIntrinsicType(name: String): Type<*>? {
        return null
    }

    /**
     * 获取属性值
     * @param typeName 类型名称
     * @return 属性值
     */
    open fun getAttrValue(typeName: String): String {
        return ""
    }

    /**
     * 获取UUID
     * @return UUID
     */
    open fun getUUID(): BigInteger {
        return BigInteger.ONE
    }

    /**
     * 获取认证集合ID
     * @return 认证集合ID
     */
    open fun getAttestationCollectionId(): String {
        return getTSKey()
    }

    /**
     * 获取认证集合ID（带TokenDefinition）
     * @param td TokenDefinition对象
     * @return 认证集合ID
     */
    open fun getAttestationCollectionId(td: TokenDefinition): String {
        return getTSKey()
    }

    /**
     * 获取第一个图片URL
     * @return 图片URL
     */
    open fun getFirstImageUrl(): String {
        return ""
    }

    // ==================== TokenScript 结果缓存相关方法 ====================

    /**
     * 获取属性结果
     * @param attrId 属性ID
     * @param tokenId 代币ID
     * @return 属性结果
     */
    fun getAttributeResult(attrId: String?, tokenId: BigInteger?): TokenScriptResult.Attribute? {
        val tokenIdMap = resultMap[tokenId]
        return tokenIdMap?.get(attrId)
    }

    /**
     * 设置属性结果
     * @param tokenId 代币ID
     * @param attrResult 属性结果
     */
    fun setAttributeResult(tokenId: BigInteger, attrResult: TokenScriptResult.Attribute) {
        var tokenIdMap = resultMap[tokenId]
        if (tokenIdMap == null) {
            tokenIdMap = mutableMapOf()
            resultMap[tokenId] = tokenIdMap
        }
       attrResult.id.let {
           tokenIdMap[it.toString()] = attrResult
         }
    }

    /**
     * 清理结果映射
     */
    fun clearResultMap() {
        resultMap.clear()
    }

    /**
     * 设置功能可用性
     * @param availabilityMap 可用性映射
     */
    fun setFunctionAvailability(availabilityMap: Map<BigInteger, List<String>>) {
        this.functionAvailabilityMap = availabilityMap
    }

    /**
     * 检查功能是否可用
     * @param tokenId 代币ID
     * @param functionName 功能名称
     * @return 如果功能可用返回true
     */
    fun isFunctionAvailable(tokenId: BigInteger, functionName: String): Boolean {
        val availabilityMap = this.functionAvailabilityMap ?: return true
        val functions = availabilityMap[tokenId] ?: return false
        return functions.contains(functionName)
    }

    // ==================== 对象比较相关方法 ====================

    /**
     * 比较代币是否相等
     * @param token 另一个代币对象
     * @return 如果相等返回true
     */
    fun equals(token: Token): Boolean {
        return tokenInfo.chainId == token.tokenInfo.chainId &&
                tokenInfo.address.equals(token.tokenInfo.address, ignoreCase = true)
    }

    /**
     * 重写equals方法
     * @param other 另一个对象
     * @return 如果相等返回true
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Token) return false

        return tokenInfo.chainId == other.tokenInfo.chainId &&
                tokenInfo.address.equals(other.tokenInfo.address, ignoreCase = true)
    }

    /**
     * 重写hashCode方法
     * @return 哈希码
     */
    override fun hashCode(): Int {
        var result = tokenInfo.chainId.hashCode()
        result = 31 * result + tokenInfo.address?.lowercase().hashCode()
        return result
    }
}
