package com.alphawallet.app.entity.tokens

import com.alphawallet.app.R
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.TicketRangeElement
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.repository.EventResult
import com.alphawallet.token.entity.TicketRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Uint
import org.web3j.abi.datatypes.generated.Uint16
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import com.alphawallet.app.util.Utils as AWUtils
import org.web3j.abi.Utils as AbiUtils

/**
 * ERC-875/Legacy 票券类（非同质化代币集合）
 *
 * 职责与功能：
 * - 维护 ERC-875/Legacy 合约的票券库存（以 TokenId 列表存储）
 * - 提供 TokenId 与合约索引（Index）之间的双向转换能力
 * - 构造符合合约签名的 transfer Function 参数（根据合约类型选择 Uint16/Uint256）
 * - 提供便捷的转账列表格式化、分组规则与基本统计信息
 *
 * 设计特点：
 * - 继承自 Token 基类，覆盖必要的 open 方法，保持与 Kotlin 基类接口兼容
 * - 对于可能耗时的列表转换操作，提供了协程（suspend）版本，避免阻塞 UI 线程
 * - 采用中文 KDoc 注释，便于阅读与维护
 */
class Ticket(
    tokenInfo: TokenInfo,
    private val balanceArray: List<BigInteger>,
    blancaTime: Long,
    networkName: String,
    type: ContractType
) : Token(tokenInfo, BigDecimal.ZERO, blancaTime, networkName, type) {

    /**
     * 以逗号分隔的十六进制 TokenId 字符串构造函数
     * @param balances 形如 "0x1,0x2,0x3" 的十六进制 ID 串
     */
    constructor(
        tokenInfo: TokenInfo,
        balances: String,
        blancaTime: Long,
        networkName: String,
        type: ContractType
    ) : this(
        tokenInfo,
        // 复用父类工具方法：将十六进制字符串解析为 BigInteger 列表
        // 注意：父类方法会自动处理 0x 前缀与异常情况
        stringHexToBigIntegerListStatic(tokenInfo, balances),
        blancaTime,
        networkName,
        type
    )

    /**
     * 复制构造：沿用旧票券的基础信息，仅替换库存列表
     */
    constructor(oldTicket: Token, balances: List<BigInteger>) : this(
        oldTicket.tokenInfo,
        balances,
        oldTicket.updateBlancaTime,
        oldTicket.getNetworkName(),
        oldTicket.contractType
    )

    init {
        // 余额语义：使用非零 TokenId 的数量作为余额（件数）
        balance = BigDecimal.valueOf(getNonZeroArrayBalance().size.toLong())
        group = TokenGroup.NFT
    }

    // ==================== UI 展示与统计 ====================

    /**
     * 获取用于 UI 展示的余额字符串（返回件数）
     */
    override fun getStringBalanceForUI(decimalPlaces: Int): String {
        return getTokenCount().toString()
    }

    /**
     * 是否有正余额（是否至少有一个有效票券）
     */
    override fun hasPositiveBalance(): Boolean {
        return getTokenCount() > 0
    }

    /**
     * 获取完整余额信息：返回库存列表的十六进制字符串形式
     */
    override fun getFullBalance(): String {
        return if (balanceArray.isEmpty()) "no tokens" else AWUtils.bigIntListToString(balanceArray, true)
    }

    /**
     * 票券件数（非零 TokenId 的数量）
     */
    override fun getTokenCount(): Int {
        var count = 0
        for (id in balanceArray) {
            if (id != BigInteger.ZERO) count++
        }
        return count
    }

    /**
     * 是否存在数组型库存（对 ERC-875 始终为 true）
     */
    override fun hasArrayBalance(): Boolean = true

    /**
     * 原始库存列表（包含零值占位）
     */
    override fun getArrayBalance(): List<BigInteger> = balanceArray

    /**
     * 非零库存列表（过滤零值占位）
     */
    override fun getNonZeroArrayBalance(): List<BigInteger> {
        val nonZero = ArrayList<BigInteger>()
        for (v in balanceArray) {
            if (v != BigInteger.ZERO) nonZero.add(v)
        }
        return nonZero
    }

    /**
     * 获取原始余额（件数）
     */
    override fun getBalanceRaw(): BigDecimal {
        return BigDecimal(getArrayBalance().size)
    }

    // ==================== 合约交互与参数构造 ====================

    /**
     * 构造 ERC-875/Legacy 的 transfer Function
     * - Legacy 使用 Uint16 索引
     * - 标准 875 使用 Uint256 索引
     */
    @Throws(NumberFormatException::class)
    override fun getTransferFunction(to: String, transferData: List<BigInteger>): Function? {
        return Function(
            "transfer",
            listOf(
                Address(to),
                getDynArray(transferData)
            ),
            emptyList()
        )
    }

    /**
     * 根据合约类型返回动态数组参数类型
     */
    override fun getDynArray(indices: List<BigInteger>): DynamicArray<out Uint> {
        return when (contractType) {
            ContractType.ERC875_LEGACY -> DynamicArray(
                Uint16::class.java,
                AbiUtils.typeMap(indices, Uint16::class.java)
            )
            else -> DynamicArray(
                Uint256::class.java,
                AbiUtils.typeMap(indices, Uint256::class.java)
            )
        }
    }

    /**
     * 是否为代币（对于票券返回 false）
     */
    override fun isToken(): Boolean = false

    /**
     * 是否为非同质化
     */
    override fun isNonFungible(): Boolean = true

    /**
     * 是否允许按组显示/转移（ERC-875 支持分组）
     */
    override fun hasGroupedTransfer(): Boolean = true

    /**
     * 简单校验当前合约类型是否属于 ERC-875 系列
     */
    override fun contractTypeValid(): Boolean {
        return when (contractType) {
            ContractType.ERC875, ContractType.ERC875_LEGACY -> true
            else -> false
        }
    }

    /**
     * 是否为 ERC-875（含 Legacy）
     */
    fun isERC875(): Boolean = true

    // ==================== 索引与 ID 转换 ====================

    /**
     * 将 TokenId 字符串（十六进制 CSV）转换为索引列表（适配 ERC-875 的 transfer 索引入参）
     * 例："0x1, 0x2, 0x3" -> [0, 1, 2]（根据库存内的顺序匹配）
     */
    override fun ticketIdStringToIndexList(userList: String): List<BigInteger> {
        val idList = ArrayList<BigInteger>()
        val ids = userList.split(",")
        for (id in ids) {
            val trim = id.trim()
            if (trim.isNotEmpty()) {
                idList.add(Numeric.toBigInt(trim))
            }
        }
        return tokenIdsToTokenIndices(idList)
    }

    /**
     * 根据 TokenId 列表生成合约入参索引列表（非协程版本）
     */
    override fun getTransferListFormat(tokenIds: List<BigInteger>): List<BigInteger> {
        return tokenIdsToTokenIndices(tokenIds)
    }

    /**
     * 根据 CSV 字符串生成合约入参索引列表（字符串版本便捷方法，供旧调用点复用）
     * 返回以逗号分隔的十六进制字符串（合约所需的索引列表）
     */
    fun getTransferListFormat(CSVstringIdList: String): String {
        val indexList = ticketIdStringToIndexList(CSVstringIdList)
        return AWUtils.bigIntListToString(indexList, true)
    }

    /**
     * 将用户选中的票券范围是否可分组展示
     * - 规则：同一批次（时间戳一致）或首个 TokenId 相同可归为一组
     */
    override fun groupWithToken(
        currentRange: TicketRange,
        e: TicketRangeElement,
        currentTime: Long
    ): Boolean {
        if (currentRange.tokenIds.isEmpty()) return false
        return currentRange.tokenIds[0] == e.id || (e.time != 0L && e.time == currentTime)
    }

    /**
     * 转换活动结果值的展示（保持与 Java 版本一致：直接拼接原值，不做小数缩放）
     */
    override fun convertValue(prefix: String, vResult: EventResult, precision: Int): String {
        return prefix + (vResult.value ?: "")
    }

    /**
     * 私有：将 TokenId 列表映射为库存索引列表（重复选择会去重）
     */
    private fun tokenIdsToTokenIndices(tokenIds: List<BigInteger>): List<BigInteger> {
        val inventoryCopy = ArrayList<BigInteger>(balanceArray)
        var indexList = ArrayList<BigInteger>()
        try {
            for (id in tokenIds) {
                if (id != BigInteger.ZERO) {
                    val index = inventoryCopy.indexOf(id)
                    if (index > -1) {
                        inventoryCopy[index] = BigInteger.ZERO
                        val indexBi = BigInteger.valueOf(index.toLong())
                        if (!indexList.contains(indexBi)) {
                            indexList.add(indexBi)
                        }
                    } else {
                        indexList = ArrayList() // 标记失败，返回空列表
                        break
                    }
                }
            }
        } catch (_: Exception) {
            indexList = ArrayList()
        }
        return indexList
    }

    // ==================== 协程（可选）异步版本 ====================

    /**
     * 协程版本：将 TokenId 列表映射为库存索引列表
     * 适用于大批量数据转换，避免阻塞主线程
     */
    suspend fun tokenIdsToTokenIndicesAsync(tokenIds: List<BigInteger>): List<BigInteger> =
        withContext(Dispatchers.Default) { tokenIdsToTokenIndices(tokenIds) }

    /**
     * 协程版本：将十六进制 CSV 的 TokenId 列表转换为索引列表
     */
    suspend fun ticketIdStringToIndexListAsync(userList: String): List<BigInteger> = withContext(Dispatchers.Default) {
        ticketIdStringToIndexList(userList)
    }

    companion object {
        // 为了在次级构造函数中访问父类工具方法，提供一个静态代理
        private fun stringHexToBigIntegerListStatic(tokenInfo: TokenInfo, integerString: String): List<BigInteger> {
            // 无法直接静态调用父类成员，这里借助一个临时 Token 实例进行解析
            val tmp = Token(tokenInfo, BigDecimal.ZERO, 0L, "", ContractType.NOT_SET)
            return tmp.stringHexToBigIntegerList(integerString)
        }
    }

    // ==================== 兼容项与保留方法 ====================

    /**
     * 注意：以下 Java 版本中覆盖的基类方法在 Kotlin 基类中为 final，不可覆盖：
     * - setRealmBalance(realmToken: RealmToken)
     * - getIsSent(transaction: Transaction)
     * - getTransferValue(txInput: TransactionInput, precision: Int)
     * - getTransferValueRaw(txInput: TransactionInput)
     * - getContractType(): Int
     *
     * 因此在本 Kotlin 版本中保留了必要的同名便捷方法（非覆盖），以及新增的辅助方法，确保不破坏既有调用点。
     */

    /**
     * 便捷：返回标准功能按钮集合
     */
    override fun getStandardFunctions(): List<Int> {
        return listOf(R.string.action_use, R.string.action_transfer, R.string.action_sell)
    }
}
