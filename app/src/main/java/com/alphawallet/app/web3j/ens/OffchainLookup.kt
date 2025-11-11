package com.alphawallet.app.web3j.ens

import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.DynamicStruct
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Bytes4
import org.web3j.utils.Numeric

/**
 * 根据 EIP-3668 (链下数据检索) 定义的数据结构。
 * 封装了从链下查找错误中解析出的所有参数。
 * @see <a href="https://eips.ethereum.org/EIPS/eip-3668#client-lookup-protocol">EIP-3668 客户端查找协议</a>
 *
 * @param sender 调用者地址。
 * @param urls 用于链下数据检索的 URL 列表。
 * @param callData 发送到 URL 的原始调用数据。
 * @param callbackFunction 用于处理链下返回数据的回调函数的选择器（4字节）。
 * @param extraData 传递给回调函数的额外数据。
 */
class OffchainLookup(
    @get:JvmName("getSenderValue") val sender: String,
    @get:JvmName("getUrlsValue") val urls: List<String>,
    @get:JvmName("getCallDataValue") val callData: ByteArray,
    @get:JvmName("getCallbackFunctionValue") val callbackFunction: ByteArray,
    @get:JvmName("getExtraDataValue") val extraData: ByteArray
) : DynamicStruct(
    // 将 Kotlin 类型转换为 web3j 的 Type 类型以调用父构造函数
    Address(sender),
    DynamicArray(Utf8String::class.java, urls.map { Utf8String(it) }),
    DynamicBytes(callData),
    Bytes4(callbackFunction),
    DynamicBytes(extraData)
) {
    /**
     * 辅助构造函数，接收 web3j 的 Type 类型作为参数。
     * 在内部将这些类型转换为 Kotlin 的原生类型。
     */
    constructor(
        sender: Address,
        urls: DynamicArray<Utf8String>,
        callData: DynamicBytes,
        callbackFunction: Bytes4,
        extraData: DynamicBytes
    ) : this(
        sender.value,
        urls.value.map { it.value },
        callData.value,
        callbackFunction.value,
        extraData.value
    )

    companion object {
        /**
         * 定义了用于解码链下查找返回数据的 ABI 输出参数类型列表。
         */
        @Suppress("UNCHECKED_CAST")
        private fun getOutputParameters(): List<TypeReference<Type<*>>> =
            listOf(
                object : TypeReference<Address>() {} as TypeReference<Type<*>>,
                object : TypeReference<DynamicArray<Utf8String>>() {} as TypeReference<Type<*>>,
                object : TypeReference<DynamicBytes>() {} as TypeReference<Type<*>>,
                object : TypeReference<Bytes4>() {} as TypeReference<Type<*>>,
                object : TypeReference<DynamicBytes>() {} as TypeReference<Type<*>>,
            )

        /**
         * 从原始字节数组构建一个 OffchainLookup 实例。
         *
         * @param bytes 从 OffchainLookup 错误中获取的原始返回数据。
         * @return 一个解析完成的 OffchainLookup 实例。
         */
        @JvmStatic
        fun build(bytes: ByteArray): OffchainLookup {
            val resultList = FunctionReturnDecoder.decode(
                Numeric.toHexString(bytes),
                getOutputParameters()
            )

            // 使用辅助构造函数创建实例
            return OffchainLookup(
                resultList[0] as Address,
                resultList[1] as DynamicArray<Utf8String>,
                resultList[2] as DynamicBytes,
                resultList[3] as Bytes4,
                resultList[4] as DynamicBytes
            )
        }
    }

    // 重写 equals 和 hashCode 以确保基于值的比较
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OffchainLookup

        if (sender != other.sender) return false
        if (urls != other.urls) return false
        if (!callData.contentEquals(other.callData)) return false
        if (!callbackFunction.contentEquals(other.callbackFunction)) return false
        if (!extraData.contentEquals(other.extraData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sender.hashCode()
        result = 31 * result + urls.hashCode()
        result = 31 * result + callData.contentHashCode()
        result = 31 * result + callbackFunction.contentHashCode()
        result = 31 * result + extraData.contentHashCode()
        return result
    }
}
