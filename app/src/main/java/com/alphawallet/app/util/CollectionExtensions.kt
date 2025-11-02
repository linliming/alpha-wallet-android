package com.alphawallet.app.util

import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.util.*
import kotlin.collections.ArrayList

fun Array<Long>.longArrayToString(): String {
    return this.joinToString(separator = ",")
}

fun String.toLongList(): List<Long> {
    return this.split(",").mapNotNull {
        try {
            it.toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }
}

fun List<BigInteger>.toIntArray(): IntArray {
    return this.map { it.intValue() }.toIntArray()
}

fun List<BigInteger>.bigIntListToString(keepZeros: Boolean = false): String {
    return this.filter { keepZeros || it != BigInteger.ZERO }
        .joinToString(separator = ",") { Numeric.toHexStringNoPrefix(it) }
}

fun String.toIntegerList(): List<Int> {
    return try {
        this.split(",").map { it.trim().toInt() }
    } catch (e: Exception) {
        emptyList()
    }
}

fun List<Int>.integerListToString(keepZeros: Boolean = false): String {
    return this.filter { keepZeros || it != 0 }
        .joinToString(separator = ",")
}

fun List<BigInteger>.toIdMap(): Map<BigInteger, BigInteger> {
    return this.groupingBy { it }
        .eachCount()
        .mapValues { it.value.toBigInteger() }
}

fun decodeDynamicArray(output: String): List<Type<*>> {
    val adaptive = org.web3j.abi.Utils.convert(
        Collections.singletonList(object : TypeReference<DynamicArray<Utf8String>>() {})
    )
    return try {
        FunctionReturnDecoder.decode(output, adaptive)
    } catch (e: Exception) {
        emptyList()
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> asAList(responseValues: List<Type<*>>): List<T> {
    val converted = ArrayList<T>()
    if (responseValues.isEmpty()) {
        return converted
    }
    (responseValues[0] as DynamicArray<*>).value.forEach { objUri ->
        try {
            converted.add((objUri as Type<*>).value.toString() as T)
        } catch (e: ClassCastException) {
            //
        }
    }
    return converted
}
