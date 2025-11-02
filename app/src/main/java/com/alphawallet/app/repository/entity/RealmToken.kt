package com.alphawallet.app.repository.entity

import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.tokens.TokenInfo
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

/**
 * Realm 持久化层中的代币实体。
 *
 * 该类在 Java -> Kotlin 迁移过程中进行了以下增强：
 * 1. 为常用的变更操作提供协程版本，便于在后台线程上更新 Realm 对象。
 * 2. 修复静态代码分析中发现的潜在异常（例如使用字符串分隔符时的正则问题、BigInteger 解析异常）。
 * 3. 补充注释说明各字段与方法的用途，帮助后续维护。
 */
open class RealmToken : RealmObject() {

    @PrimaryKey
    var address: String = ""

    var name: String? = null
    var symbol: String? = null
    var decimals: Int = 0
    var addedTime: Long = 0L
    var updatedTime: Long = 0L
    @get:JvmName("getLastTxTimeCompat")
    @set:JvmName("setLastTxTimeCompat")
    var lastTxTime: Long = 0L

    // 保留原有字段名称，便于与已有 Realm Schema 对应；通过 @JvmName 避免与 getBalance()/setBalance() 方法冲突。
    @get:JvmName("getBalanceRaw")
    @set:JvmName("setBalanceRaw")
    var balance: String? = null

    var isEnabled: Boolean = false
    var tokenId: Int = 0
    var interfaceSpec: Int = ContractType.NOT_SET.ordinal
    var auxData: String? = null
    var lastBlockRead: Long = 0L
    @get:JvmName("getChainIdCompat")
    @set:JvmName("setChainIdCompat")
    var chainId: Long = 0L
    var earliestTxBlock: Long = 0L
    private var visibilityChanged: Boolean = false
    private var erc1155BlockRead: String? = null

    @get:JvmName("getTokenAddressCompat")
    val tokenAddress: String
        get() = computeTokenAddress()

    /**
     * 获取数据库中存储的完整地址（带有链信息），移除链信息后返回裸地址。
     */
    fun getTokenAddress(): String = computeTokenAddress()

    /**
     * Realm 历史上将 addTime 作为更新时间字段，这里保持兼容。
     */
    @get:JvmName("getUpdateTimeCompat")
    val updateTime: Long
        get() = addedTime

    fun getUpdateTime(): Long = updateTime

    fun setUpdateTime(updateTime: Long) {
        updatedTime = updateTime
    }

    /**
     * 返回安全的余额字符串，避免出现空值。
     */
    fun getBalance(): String = balance?.takeUnless { it.isBlank() } ?: ZERO_BALANCE

    /**
     * 余额最后更新时间（用于 UI 刷新）。
     */
    @get:JvmName("getBalanceUpdateTimeCompat")
    val balanceUpdateTime: Long
        get() = updatedTime

    fun getBalanceUpdateTime(): Long = balanceUpdateTime

    fun getEnabled(): Boolean = isEnabled

    /**
     * 获取接口规范类型，确保下标合法，避免数组越界。
     */
    @get:JvmName("getContractTypeCompat")
    val contractType: ContractType
        get() {
            val safeOrdinal = interfaceSpec.coerceIn(ContractType.NOT_SET.ordinal, ContractType.CREATION.ordinal)
            return ContractType.values()[safeOrdinal]
        }

    fun getContractType(): ContractType = contractType

    fun setLastBlock(lastBlockCheck: Long) {
        lastBlockRead = lastBlockCheck
    }

    @get:JvmName("getLastBlockCompat")
    val lastBlock: Long
        get() = lastBlockRead

    fun getLastBlock(): Long = lastBlock

    @get:JvmName("getEarliestTransactionBlockCompat")
    val earliestTransactionBlock: Long
        get() = earliestTxBlock

    fun getEarliestTransactionBlock(): Long = earliestTransactionBlock

    fun setEarliestTransactionBlock(earliestTransactionBlock: Long) {
        earliestTxBlock = earliestTransactionBlock
    }

    fun isVisibilityChanged(): Boolean = visibilityChanged

    fun setVisibilityChanged(changed: Boolean) {
        visibilityChanged = changed
    }

    fun getChainId(): Long = chainId

    fun setChainId(chainId: Long) {
        this.chainId = chainId
    }

    fun getLastTxTime(): Long = lastTxTime

    fun setLastTxTime(lastTxTime: Long) {
        this.lastTxTime = lastTxTime
    }

    /**
     * 更新余额（同步版本）。该方法会在值发生变动时刷新更新时间。
     */
    fun setBalance(newBalance: String) {
        applyBalanceUpdate(newBalance)
    }

    /**
     * 更新余额（协程版本），便于在协程环境下调用。
     */
    suspend fun setBalanceAsync(newBalance: String) = withContext(Dispatchers.Default) {
        applyBalanceUpdate(newBalance)
    }

    /**
     * 判断是否需要更新代币信息（同步版本）。
     */
    fun updateTokenInfoIfRequired(tokenInfo: TokenInfo) {
        applyTokenInfoUpdate(tokenInfo)
    }

    /**
     * 判断是否需要更新代币信息（协程版本），用于后台批量更新。
     */
    suspend fun updateTokenInfoIfRequiredAsync(tokenInfo: TokenInfo) = withContext(Dispatchers.Default) {
        applyTokenInfoUpdate(tokenInfo)
    }

    /**
     * 读取 ERC1155 的最新同步块信息，遇到异常时返回 0。
     */
    fun getErc1155BlockRead(): BigInteger {
        val encodedValue = erc1155BlockRead
        if (encodedValue.isNullOrBlank()) {
            return BigInteger.ZERO
        }

        return runCatching {
            BigInteger(encodedValue, Character.MAX_RADIX)
        }.getOrDefault(BigInteger.ZERO)
    }

    fun setErc1155BlockRead(block: BigInteger) {
        erc1155BlockRead = block.toString(Character.MAX_RADIX)
    }

    private fun applyBalanceUpdate(rawBalance: String) {
        val sanitizedBalance = rawBalance.ifBlank { ZERO_BALANCE }
        val now = System.currentTimeMillis()
        if (sanitizedBalance != balance) {
            updatedTime = now
        }
        balance = sanitizedBalance
        addedTime = now
    }

    private fun applyTokenInfoUpdate(tokenInfo: TokenInfo) {
        val sanitizedDecimals = safeDecimals(tokenInfo.decimals)
        val shouldUpdateDecimals =
            tokenInfo.decimals > 0 &&
                (decimals == 0 || decimals == DEFAULT_DECIMALS) &&
                decimals != sanitizedDecimals

        val shouldUpdateName = !tokenInfo.name.isNullOrBlank() && tokenInfo.name != name
        val shouldUpdateSymbol = !tokenInfo.symbol.isNullOrBlank() && tokenInfo.symbol != symbol

        if (shouldUpdateDecimals || shouldUpdateName || shouldUpdateSymbol) {
            name = tokenInfo.name
            symbol = tokenInfo.symbol
            decimals = sanitizedDecimals
        }

        if (!isEnabled && tokenInfo.isEnabled) {
            isEnabled = true
            visibilityChanged = false
        }
    }

    private fun safeDecimals(rawDecimals: Int): Int {
        return rawDecimals.coerceIn(Int.MIN_VALUE, Int.MAX_VALUE)
    }

    private fun computeTokenAddress(): String {
        if (address.isBlank()) {
            return address
        }

        val dotIndex = address.indexOf('.')
        if (dotIndex > 0) {
            return address.substring(0, dotIndex)
        }

        val dashIndex = address.indexOf('-')
        return if (dashIndex > 0) {
            address.substring(0, dashIndex)
        } else {
            address
        }
    }

    companion object {
        private const val ZERO_BALANCE = "0"
        private const val DEFAULT_DECIMALS = 18
    }
}
