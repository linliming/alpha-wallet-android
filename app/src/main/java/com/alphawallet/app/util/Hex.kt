package com.alphawallet.app.util

import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * 十六进制工具类，提供常用的进制转换与字节数组编解码功能。
 * 迁移自 Java 版本，并添加空值处理以满足静态分析要求。
 */
object Hex {

    /**
     * 将十六进制字符串转换为 [Int]，若失败则返回默认值。
     */
    @JvmStatic
    fun hexToInteger(input: String, def: Int): Int = hexToInteger(input) ?: def

    /**
     * 将十六进制字符串转换为 [Int]，失败返回 null。
     */
    @JvmStatic
    fun hexToInteger(input: String?): Int? =
        runCatching { input?.let { Integer.decode(it) } }.getOrNull()

    /**
     * 将十六进制转换为 [Long]，失败返回默认值。
     */
    @JvmStatic
    fun hexToLong(input: String, def: Int): Long = hexToLong(input) ?: def.toLong()

    /**
     * 将十六进制转换为 [Long]，失败返回 null。
     */
    @JvmStatic
    fun hexToLong(input: String?): Long? =
        runCatching { input?.let { java.lang.Long.decode(it) } }.getOrNull()

    /**
     * 将十六进制转换为 [BigInteger]，失败返回 null。
     */
    @JvmStatic
    fun hexToBigInteger(input: String?): BigInteger? {
        if (input.isNullOrEmpty()) return null
        return try {
            val cleanInput = cleanHexPrefix(input)
            val isHex = containsHexPrefix(input)
            BigInteger(cleanInput, if (isHex) 16 else 10)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 将十六进制转换为 [BigInteger]，失败返回默认值。
     */
    @JvmStatic
    fun hexToBigInteger(input: String, def: BigInteger): BigInteger =
        hexToBigInteger(input) ?: def

    /**
     * 将十六进制转换为 [BigDecimal]。
     */
    @JvmStatic
    fun hexToBigDecimal(input: String): BigDecimal =
        hexToBigInteger(input)?.let { BigDecimal(it) } ?: BigDecimal.ZERO

    /**
     * 将十六进制转换为 [BigDecimal]，失败返回默认值。
     */
    @JvmStatic
    fun hexToBigDecimal(input: String, def: BigDecimal): BigDecimal =
        BigDecimal(hexToBigInteger(input, def.toBigInteger()))

    /**
     * 判断是否包含 0x 前缀。
     */
    @JvmStatic
    fun containsHexPrefix(input: String): Boolean =
        input.length > 1 && input[0] == '0' && input[1] == 'x'

    /**
     * 移除 0x 前缀。
     */
    @JvmStatic
    fun cleanHexPrefix(input: String?): String =
        if (input != null && containsHexPrefix(input)) input.substring(2) else input ?: ""

    /**
     * 将十六进制字符串转换为十进制字符串。
     */
    @JvmStatic
    fun hexToDecimal(value: String?): String? =
        hexToBigInteger(value)?.toString(10)

    /**
     * 将十六进制字符串转换为字节数组。
     */
    @JvmStatic
    fun hexStringToByteArray(input: String?): ByteArray {
        val cleanInput = cleanHexPrefix(input)
        if (cleanInput.isEmpty()) return ByteArray(0)

        val len = cleanInput.length
        val data: ByteArray
        var startIdx: Int

        if (len % 2 != 0) {
            data = ByteArray(len / 2 + 1)
            data[0] = Character.digit(cleanInput[0], 16).toByte()
            startIdx = 1
        } else {
            data = ByteArray(len / 2)
            startIdx = 0
        }

        var i = startIdx
        while (i < len) {
            val first = Character.digit(cleanInput[i], 16)
            val second = Character.digit(cleanInput[i + 1], 16)
            data[(i + 1) / 2] = ((first shl 4) + second).toByte()
            i += 2
        }
        return data
    }

    /**
     * 将字节数组转换为十六进制字符串。
     */
    @JvmStatic
    fun byteArrayToHexString(input: ByteArray, offset: Int, length: Int, withPrefix: Boolean): String {
        val sb = StringBuilder()
        if (withPrefix) sb.append("0x")
        for (i in offset until offset + length) {
            sb.append(String.format("%02x", input[i].toInt() and 0xFF))
        }
        return sb.toString()
    }

    /**
     * 将字节数组转换为十六进制字符串，默认带 0x 前缀。
     */
    @JvmStatic
    fun byteArrayToHexString(input: ByteArray?): String? {
        if (input == null || input.isEmpty()) return null
        return byteArrayToHexString(input, 0, input.size, true)
    }

    /**
     * 将十六进制字符串解码为 UTF-8 字符串。
     */
    @JvmStatic
    fun hexToUtf8(hex: String): String {
        val clean = org.web3j.utils.Numeric.cleanHexPrefix(hex)
        val buffer = ByteBuffer.allocate(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            buffer.put(clean.substring(i, i + 2).toInt(16).toByte())
            i += 2
        }
        buffer.rewind()
        return StandardCharsets.UTF_8.decode(buffer).toString()
    }
}
