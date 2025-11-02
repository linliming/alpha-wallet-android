package com.alphawallet.token.entity

import com.alphawallet.token.tools.TokenDefinition
import com.alphawallet.token.util.DateTimeFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.web3j.utils.Numeric
import org.xml.sax.SAXException
import java.io.UnsupportedEncodingException
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Attribute - TokenScript 属性类
 *
 * 这是 AlphaWallet 中处理 TokenScript 属性的核心类，负责：
 * 1. 解析 XML 元素并构建属性对象
 * 2. 处理不同类型的语法值转换
 * 3. 管理属性的映射关系
 * 4. 提供数据转换和格式化功能
 *
 * Kotlin 优化：
 * - 使用 Kotlin 的空安全特性
 * - 使用 Kotlin 的扩展函数
 * - 使用 Kotlin 的数据类和属性
 * - 提供更好的错误处理
 *
 * @author AlphaWallet Team
 * @since 2024
 */
class Attribute(
    attr: Element,
    def: TokenDefinition,
) {
    companion object {
        /**
         * 地址大小常量
         */
        private const val ADDRESS_SIZE = 160
        private const val ADDRESS_LENGTH_IN_HEX = ADDRESS_SIZE shr 2
        private const val ADDRESS_LENGTH_IN_BYTES = ADDRESS_SIZE shr 3
    }

    /**
     * 位掩码，默认为32字节表示
     */
    var bitmask: BigInteger = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16)

    /**
     * 标签，应该是多语言的，因为用户可以在运行时更改语言
     */
    @JvmField
    var label: String? = null

    /**
     * 属性名称
     */
    @JvmField
    var name: String? = null

    /**
     * 位移量
     */
    @JvmField
    var bitshift: Int = 0

    /**
     * 语法类型
     */
    @JvmField
    var syntax: TokenDefinition.Syntax = TokenDefinition.Syntax.DirectoryString

    /**
     * 数据类型
     */
    @JvmField
    var asValue: As = As.Unsigned

    /**
     * 成员映射
     */
    @JvmField
    var members: MutableMap<BigInteger, String>? = null

    /**
     * 原始合约信息
     */
    private var originContract: ContractInfo? = null

    /**
     * 函数定义
     */
    @JvmField
    var function: FunctionDefinition? = null

    /**
     * 事件定义
     */
    @JvmField
    var event: EventDefinition? = null

    /**
     * 是否为用户输入
     */
    @JvmField
    var userInput: Boolean = false

    /**
     * 构造函数
     *
     * @param attr XML 元素
     * @param def Token 定义
     * @throws SAXException XML 解析异常
     */
    init {
        originContract = def.contracts[def.holdingToken]

        // schema 2020/06 id 现在是 name；name 现在是 label
        name = attr.getAttribute("name")
        label = name // 如果未指定，将 label 设置为 name
        asValue = As.Unsigned // 默认值
        syntax = TokenDefinition.Syntax.DirectoryString // 默认值

        var node: Node? = attr.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                when (node.localName) {
                    "type" -> {
                        syntax = handleType(element)
                    }
                    "origins" -> {
                        handleOrigins(element, def)
                    }
                    "label" -> {
                        label = def.getLocalisedString(element)
                    }
                    "mapping" -> {
                        populate(element, def)
                    }
                }

                when (element.getAttribute("contract").lowercase()) {
                    "holding-contract" -> {
                        setAs(As.Mapping)
                        // TODO: 语法未检查
                        // getFunctions(origin)
                    }
                }
            }
            node = node.nextSibling
        }

        // 计算位移量
        if (bitmask != null) {
            while (bitmask.mod(BigInteger.ONE.shiftLeft(++bitshift)).equals(BigInteger.ZERO)) {
                // 空循环，用于计算位移量
            }
            bitshift--
        }
    }

    /**
     * 处理类型元素
     *
     * @param syntax 语法元素
     * @return 语法类型
     */
    private fun handleType(syntax: Element): TokenDefinition.Syntax {
        var asToken = TokenDefinition.Syntax.DirectoryString

        var node: Node? = syntax.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                // 处理类型子元素
                // 这里可以根据需要添加具体的类型处理逻辑
            }
            node = node.nextSibling
        }

        return asToken
    }

    /**
     * 根据 ISO 字符串获取语法类型
     *
     * @param ISO ISO 字符串
     * @return 语法类型
     */
    private fun getSyntax(ISO: String): TokenDefinition.Syntax? =
        when (ISO) {
            "1.3.6.1.4.1.1466.115.121.1.6" -> TokenDefinition.Syntax.BitString
            "1.3.6.1.4.1.1466.115.121.1.7" -> TokenDefinition.Syntax.Boolean
            "1.3.6.1.4.1.1466.115.121.1.11" -> TokenDefinition.Syntax.CountryString
            "1.3.6.1.4.1.1466.115.121.1.28" -> TokenDefinition.Syntax.JPEG
            "1.3.6.1.4.1.1466.115.121.1.36" -> TokenDefinition.Syntax.NumericString
            "1.3.6.1.4.1.1466.115.121.1.24" -> TokenDefinition.Syntax.GeneralizedTime
            "1.3.6.1.4.1.1466.115.121.1.26" -> TokenDefinition.Syntax.IA5String
            "1.3.6.1.4.1.1466.115.121.1.27" -> TokenDefinition.Syntax.Integer
            "1.3.6.1.4.1.1466.115.121.1.15" -> TokenDefinition.Syntax.DirectoryString
            else -> null
        }

    /**
     * 处理起源元素
     *
     * @param origin 起源元素
     * @param def Token 定义
     * @throws SAXException XML 解析异常
     */
    private fun handleOrigins(
        origin: Element,
        def: TokenDefinition,
    ) {
        var node: Node? = origin.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val resolve = node as Element
                setAs(def.parseAs(resolve))

                if (resolve.prefix == "ethereum") {
                    // 处理以太坊命名空间
                    when (node.localName) {
                        "transaction", "call" -> {
                            function = def.parseFunction(resolve, syntax)
                        }
                        "event" -> {
                            event = def.parseEvent(resolve)
                            event?.attributeName = name
                            event?.parentAttribute = this
                        }
                    }
                } else {
                    when (node.localName) {
                        "token-id" -> {
                            // 这个值是从 token 名称获得的
                            setAs(def.parseAs(resolve))
                            populate(resolve, def) // 检查映射
                            function?.asDefin = def.parseAs(resolve)
                            if (resolve.hasAttribute("bitmask")) {
                                bitmask = BigInteger(resolve.getAttribute("bitmask"), 16)
                            }
                        }
                        "user-entry" -> {
                            userInput = true
                            setAs(def.parseAs(resolve))
                            if (resolve.hasAttribute("bitmask")) {
                                bitmask = BigInteger(resolve.getAttribute("bitmask"), 16)
                            }
                        }
                    }
                }
            }
            node = node.nextSibling
        }
    }

    /**
     * 填充映射数据
     *
     * @param origin 起源元素
     * @param def Token 定义
     */
    private fun populate(
        origin: Element,
        def: TokenDefinition,
    ) {
        val membersMap = mutableMapOf<BigInteger, String>()

        var node: Node? = origin.firstChild
        while (node != null) {
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                if (element.localName == "mapping") {
                    setAs(As.Mapping)

                    val nList = origin.getElementsByTagNameNS(def.nameSpace, "option")
                    for (i in 0 until nList.length) {
                        val option = nList.item(i) as Element
                        val key = BigInteger(option.getAttribute("key"))
                        val values: String? = def.getLocalisedString(option, "value")
                        if (!values.isNullOrEmpty()) membersMap[key] = values
                    }
                }
            }
            node = node.nextSibling
        }

        if (membersMap.isNotEmpty()) {
            members = membersMap
        }
    }

    /**
     * 获取语法值
     *
     * 根据语法类型转换数据
     *
     * @param data 输入数据
     * @return 转换后的值
     */
    fun getSyntaxVal(data: String?): String? {
        if (data == null) return null

        return when (syntax) {
            TokenDefinition.Syntax.DirectoryString -> data
            TokenDefinition.Syntax.IA5String -> data
            TokenDefinition.Syntax.Integer -> {
                when {
                    data.isEmpty() -> "0"
                    data[0].isDigit() -> data
                    else -> {
                        // 从字节值转换
                        BigInteger(data.toByteArray()).toString()
                    }
                }
            }
            TokenDefinition.Syntax.GeneralizedTime -> {
                try {
                    // 确保数据是字母数字
                    val cleanData = checkAlphaNum(data)
                    val dt = DateTimeFactory.getDateTime(cleanData)
                    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd")
                    val simpleTimeFormat = SimpleDateFormat("hh:mm:ssZ")
                    val generalizedTime = "${dt.format(simpleDateFormat)}T${dt.format(simpleTimeFormat)}"
                    "{ generalizedTime: \"$cleanData\", date: new Date(\"$generalizedTime\") }"
                } catch (e: ParseException) {
                    data
                }
            }
            TokenDefinition.Syntax.Boolean -> {
                when {
                    data.isEmpty() -> "FALSE"
                    data[0].isDigit() -> if (data[0] == '0') "FALSE" else "TRUE"
                    data[0] == 0.toChar() -> "FALSE"
                    data[0] == 1.toChar() -> "TRUE"
                    else -> data
                }
            }
            TokenDefinition.Syntax.BitString -> data
            TokenDefinition.Syntax.CountryString -> data
            TokenDefinition.Syntax.JPEG -> data
            TokenDefinition.Syntax.NumericString -> {
                when {
                    data == null -> "0"
                    data.startsWith("0x") && this.asValue != As.Address -> data.substring(2)
                    else -> data
                }
            }
        }
    }

    /**
     * 处理值
     *
     * 有时值需要从原始输入处理。目前只有时间
     *
     * @param val 输入值
     * @return 处理后的值
     */
    fun processValue(bigInteger: BigInteger): BigInteger =
        when (syntax) {
            TokenDefinition.Syntax.GeneralizedTime -> parseGeneralizedTime(bigInteger)
            else -> bigInteger
        }

    /**
     * 解析广义时间
     *
     * @param value 时间值
     * @return 解析后的时间
     */
    private fun parseGeneralizedTime(value: BigInteger): BigInteger =
        try {
            val dt = DateTimeFactory.getDateTime(toString(value))
            BigInteger.valueOf(dt.toEpoch())
        } catch (e: Exception) {
            e.printStackTrace()
            value
        }

    /**
     * 检查字母数字字符串
     *
     * @param data 输入数据
     * @return 清理后的数据
     */
    private fun checkAlphaNum(data: String): String {
        for (ch in data.toCharArray()) {
            if (!(ch.isLetterOrDigit() || ch == '+' || ch == '-' || ch.isWhitespace())) {
                // 设置为当前时间
                val format = SimpleDateFormat("yyyyMMddHHmmssZ", Locale.ENGLISH)
                return format.format(Date(System.currentTimeMillis()))
            }
        }
        return data
    }

    /**
     * 转换为字符串
     *
     * 将位移/掩码的 token 数字数据转换为相应的字符串。
     * 例如：属性是 'venue'；选择是 "1" -> "Kaliningrad Stadium", "2" -> "Volgograd Arena" 等。
     * 注意：'time' 是 Unix EPOCH，这也是一个映射。
     * 由于值可能没有相应的映射，但是是有效的时间，我们应该仍然返回时间值
     * 并将其解释为本地时间
     *
     * 另外 - 一些与其他合约共享的 NF token（例如世界杯、聚会邀请）将具有
     * 故意具有零值的映射 - 例如 'Match' 对于会议没有查找值。返回 null 是
     * token 布局不显示值的指南。
     *
     * 一旦 IFrame 系统就位，这将变得不那么重要 - 每个 token 外观都将明确定义。
     * 但是，为了便于潜在用户熟悉系统，可能需要对 token 属性进行默认显示。
     *
     * @param data 数据
     * @return 转换后的字符串
     * @throws UnsupportedEncodingException 编码异常
     */
    @Suppress("NewApi")
    @Throws(UnsupportedEncodingException::class)
    fun toString(data: BigInteger): String? =
        when (getAs()) {
            As.UTF8 -> String(data.toByteArray(), StandardCharsets.UTF_8)
            As.Unsigned -> data.toString()
            As.Mapping -> {
                // members 可能为 null，但抛出异常比静默忽略更好
                if (members?.containsKey(data) == true) {
                    members!![data]!!
                } else if (syntax == TokenDefinition.Syntax.GeneralizedTime) {
                    // 这是一个时间条目，但没有本地化映射条目。返回 EPOCH 时间。
                    val date = Date(data.multiply(BigInteger.valueOf(1000)).longValueExact())
                    val sdf = SimpleDateFormat("yyyyMMddHHmmssZ")
                    sdf.format(date)
                } else {
                    null // 由于 token 创建时值为零，必须恢复此行为
                    // 参考 'AlphaWallet meetup indices'，其中 'Match' 映射为 null 但 FIFA 不是。
                }
            }
            As.Boolean -> if (data == BigInteger.ZERO) "FALSE" else "TRUE"
            As.UnsignedInput -> {
                // 转换为无符号
                val conv = BigInteger(1, data.toByteArray())
                conv.toString()
            }
            As.TokenId -> data.toString()
            As.Bytes -> Numeric.toHexString(data.toByteArray())
            As.Address -> parseEthereumAddress(data)
            // e18, e8, e4, e2
            // 返回调整大小的数据值？
            else -> throw NullPointerException("Missing valid 'as' attribute")
        }

    /**
     * 解析以太坊地址
     *
     * @param data 地址数据
     * @return 格式化的地址字符串
     */
    private fun parseEthereumAddress(data: BigInteger): String {
        val padded = Numeric.toBytesPadded(data, ADDRESS_LENGTH_IN_BYTES)
        val addr = Numeric.toHexString(padded)

        return if (Numeric.cleanHexPrefix(addr).length == ADDRESS_LENGTH_IN_HEX) {
            addr
        } else {
            "<Invalid Address: addr>"
        }
    }

    /**
     * 获取 As 类型
     *
     * @return As 类型
     */
    fun getAs(): As = asValue

    /**
     * 设置 As 类型
     *
     * @param as As 类型
     */
    fun setAs(asValue: As) {
        this.asValue = asValue
    }

    fun getOriginContract(): ContractInfo? = originContract

    fun setOriginContract(contractInfo: ContractInfo) {
        this.originContract = contractInfo
    }

    /**
     * 检测具有多个 tokenId 的函数调用 - 这意味着我们不应该缓存结果。
     *
     * @return 函数是否依赖多个 tokenId 输入？
     */
    fun isMultiTokenCall(): Boolean = getTokenIdCount() > 1

    /**
     * 检查是否使用 tokenId
     *
     * @return 是否使用 tokenId
     */
    fun usesTokenId(): Boolean = getTokenIdCount() > 0

    /**
     * 获取 tokenId 数量
     *
     * @return tokenId 数量
     */
    private fun getTokenIdCount(): Int {
        var tokenIdCount = 0
        if (function?.parameters != null) {
            for (arg in function!!.parameters) {
                if (arg.isTokenId()) tokenIdCount++
            }
        }
        return tokenIdCount
    }

    /**
     * 检查属性是否易变
     *
     * 任何使函数易变的属性都应该放在这里。建议我们添加一个 'volatile' 关键字。
     *
     * @return 是否易变
     */
    fun isVolatile(): Boolean = isMultiTokenCall()
}
