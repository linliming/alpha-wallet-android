package com.alphawallet.app.entity.tokendata

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.SerializedName

/**
 * 代币行情数据模型。
 *
 * 记录最新价格、涨跌幅、币种符号与更新时间等信息，
 * 既用于本地缓存也用于列表展示，支持 Parcelable 方便组件间传输。
 */
data class TokenTicker(
    /** 当前价格（以字符串存储，便于直接展示或转为 BigDecimal） */
    val price: String = "0",
    /** 24 小时涨跌幅百分比 */
    @SerializedName("percent_change_24h")
    val percentChange24h: String = "0.0",
    /** 价格对应的货币符号，例如 USD、CNY */
    val priceSymbol: String = "USD",
    /** 价格信息附带的图标链接 */
    val image: String = "",
    /** 数据最近一次更新的时间戳（毫秒） */
    val updateTime: Long = 0,
) : Parcelable {

    /**
     * 返回当前价格数据的陈旧时间（毫秒）。
     */
    fun getTickerAgeMillis(): Long = System.currentTimeMillis() - updateTime

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(price)
        dest.writeString(percentChange24h)
        dest.writeString(priceSymbol)
        dest.writeString(image)
        dest.writeLong(updateTime)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<TokenTicker> =
            object : Parcelable.Creator<TokenTicker> {
                override fun createFromParcel(source: Parcel): TokenTicker =
                    TokenTicker(
                        price = source.readString().orEmpty(),
                        percentChange24h = source.readString().orEmpty(),
                        priceSymbol = source.readString().orEmpty(),
                        image = source.readString().orEmpty(),
                        updateTime = source.readLong(),
                    )

                override fun newArray(size: Int): Array<TokenTicker?> = arrayOfNulls(size)
            }
    }
}
