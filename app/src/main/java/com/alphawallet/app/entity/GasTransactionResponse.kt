package com.alphawallet.app.entity

import android.util.SparseArray
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

/**
 * Models the legacy Etherchain gas oracle response and offers utilities to prune redundant ranges.
 */
class GasTransactionResponse {

    @SerializedName("fast")
    @Expose
    private var fast: Float? = null

    @SerializedName("fastest")
    @Expose
    private var fastest: Float? = null

    @SerializedName("safeLow")
    @Expose
    private var safeLow: Float? = null

    @SerializedName("average")
    @Expose
    private var average: Float? = null

    @SerializedName("block_time")
    @Expose
    private var blockTime: Float? = null

    @SerializedName("blockNum")
    @Expose
    private var blockNum: Float? = null

    @SerializedName("speed")
    @Expose
    private var speed: Float? = null

    @SerializedName("safeLowWait")
    @Expose
    private var safeLowWait: Float? = null

    @SerializedName("avgWait")
    @Expose
    private var avgWait: Float? = null

    @SerializedName("fastWait")
    @Expose
    private var fastWait: Float? = null

    @SerializedName("fastestWait")
    @Expose
    private var fastestWait: Float? = null

    @SerializedName("gasPriceRange")
    @Expose
    private var result: Map<String, Float>? = null

    private var arrayResult: SparseArray<Float>? = null

    fun getFast(): Float? = fast

    fun getFastest(): Float? = fastest

    fun getSafeLow(): Float? = safeLow

    fun getAverage(): Float? = average

    fun getBlockTime(): Float? = blockTime

    fun getBlockNum(): Float? = blockNum

    fun getSpeed(): Float? = speed

    fun getSafeLowWait(): Float? = safeLowWait

    fun getAvgWait(): Float? = avgWait

    fun getFastWait(): Float? = fastWait

    fun getFastestWait(): Float? = fastestWait

    fun getResult(): SparseArray<Float>? = arrayResult

    /**
     * Prune duplicate prices off the edges of the range and create sorted minimal array of prices.
     */
    fun truncatePriceRange() {
        val source = result ?: return
        val priceSet = SparseArray<Float>()
        source.forEach { (price, wait) ->
            priceSet.put(price.toInt(), wait)
        }

        for (index in priceSet.size() - 1 downTo 1) {
            val thisPrice = priceSet.keyAt(index)
            val nextPrice = priceSet.keyAt(index - 1)
            val thisWaitTime = priceSet.valueAt(index)
            val nextWaitTime = priceSet.valueAt(index - 1)
            if (thisWaitTime == nextWaitTime) {
                priceSet.delete(maxOf(thisPrice, nextPrice))
            }
        }

        val trimmed = SparseArray<Float>()
        for (index in 0 until priceSet.size()) {
            val value = priceSet.valueAt(index)
            if (value != null) {
                trimmed.put(priceSet.keyAt(index), value)
            }
        }
        arrayResult = trimmed
    }
}

