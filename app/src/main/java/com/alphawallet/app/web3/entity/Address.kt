package com.alphawallet.app.web3.entity

import android.os.Parcel
import android.os.Parcelable
import com.alphawallet.app.util.Hex
import org.web3j.utils.Numeric
import java.util.Locale

/**
 * 轻量级的以太坊地址封装。
 *
 * @param address 一个原始的、未经处理的地址字符串，可以包含 "0x" 前缀或任意大小写。
 *
 * 特性：
 * - 内部会自动清理 `0x` 前缀并转为小写存储，保持表示一致。
 * - 提供 `Parcelable` 实现，便于在 Android 组件之间传递。
 * - 对外暴露 [toString] 以标准带前缀的形式输出。
 */
class Address(address: String) : Parcelable {

    /**
     * 存储规范化后的地址值（无 "0x" 前缀的小写形式）。
     */
    private val normalizedValue: String

    init {
        val trimmed = address.trim()
        require(trimmed.isNotEmpty()) { "Address can't be empty." }
        val lowercase = trimmed.lowercase(Locale.ROOT)
        normalizedValue = if (Hex.containsHexPrefix(lowercase)) {
            Hex.cleanHexPrefix(lowercase)
        } else {
            lowercase
        }
        require(normalizedValue.isNotEmpty()) { "Address can't be empty." }
    }

    /**
     * 将地址以 `0x` 前缀形式返回，便于直接展示或参与签名。
     */
    override fun toString(): String = Numeric.prependHexPrefix(normalizedValue)

    override fun hashCode(): Int = normalizedValue.hashCode()

    override fun equals(other: Any?): Boolean =
        other is Address && normalizedValue.equals(other.normalizedValue, ignoreCase = true)

    /**
     * 获取存储的原始地址（无 `0x` 前缀的小写形式）。
     */
    fun rawValue(): String = normalizedValue

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(normalizedValue)
    }

    override fun describeContents(): Int = 0

    companion object {
        /**
         * 空地址常量，使用全零填充。
         */
        @JvmField
        val EMPTY: Address = Address("0000000000000000000000000000000000000000")

        /**
         * Kotlin/Java 公共构造入口，为了与构造函数保持一致。
         * 在 Kotlin 中，直接调用 `Address(value)` 即可。
         */
        @JvmStatic
        fun from(value: String): Address = Address(value)

        @JvmField
        val CREATOR: Parcelable.Creator<Address> =
            object : Parcelable.Creator<Address> {
                // createFromParcel现在直接调用主构造函数
                override fun createFromParcel(source: Parcel): Address =
                    Address(source.readString().orEmpty())

                override fun newArray(size: Int): Array<Address?> = arrayOfNulls(size)
            }
    }
}
