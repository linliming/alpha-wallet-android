package com.alphawallet.app.entity

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.alphawallet.app.R
import com.alphawallet.app.ui.widget.entity.GasSpeed
import com.alphawallet.app.util.BalanceUtils.gweiToWei
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.util.EnumMap

/**
 * Captures gas options (EIP-1559 and legacy) returned by oracles and provides helpers
 * for selecting and updating custom fee tiers.
 */
class GasPriceSpread() : Parcelable {

    companion object {
        const val RAPID_SECONDS = 15L
        const val FAST_SECONDS = 60L
        const val STANDARD_SECONDS = 60L * 3
        const val SLOW_SECONDS = 60L * 10

        @JvmField
        val CREATOR: Parcelable.Creator<GasPriceSpread> = object : Parcelable.Creator<GasPriceSpread> {
            override fun createFromParcel(source: Parcel): GasPriceSpread = GasPriceSpread(source)
            override fun newArray(size: Int): Array<GasPriceSpread?> = arrayOfNulls(size)
        }
    }

    var timeStamp: Long = System.currentTimeMillis()
    var speedIndex: TXSpeed = TXSpeed.STANDARD
    private val fees: MutableMap<TXSpeed, GasSpeed> = EnumMap(TXSpeed::class.java)
    private var baseFee: BigInteger = BigInteger.ZERO
    private var lockedGas: Boolean = false

    /** Returns the gas configuration currently selected by the user. */
    fun getSelectedGasFee(currentGasSpeedIndex: TXSpeed): GasSpeed? = fees[currentGasSpeedIndex]

    /** Number of fee tiers available (including custom). */
    fun getEntrySize(): Int = fees.size

    /** Returns the fastest available gas tier, or null if none exist. */
    fun getQuickestGasSpeed(): GasSpeed? {
        for (speed in TXSpeed.values()) {
            val gasSpeed = fees[speed]
            if (gasSpeed != null) return gasSpeed
        }
        return null
    }

    /** Returns the slowest (cheapest) available gas tier, preferring STANDARD. */
    fun getSlowestGasSpeed(): GasSpeed? {
        return fees.values.minByOrNull { gasSpeed -> gasSpeed.gasPrice.maxFeePerGas }
    }

    /** Returns the next faster tier after the supplied speed, falling back to CUSTOM. */
    fun getNextSpeed(speed: TXSpeed): TXSpeed {
        var begin = false
        for (txSpeed in TXSpeed.values()) {
            val gasSpeed = fees[txSpeed] ?: continue
            if (txSpeed == speed) {
                begin = true
            } else if (begin) {
                return txSpeed
            }
        }
        return TXSpeed.CUSTOM
    }

    /** Maps an adapter position to a TXSpeed enum value. */
    fun getSelectedPosition(absoluteAdapterPosition: Int): TXSpeed {
        var index = 0
        for (txSpeed in TXSpeed.values()) {
            val gasSpeed = fees[txSpeed] ?: continue
            if (index == absoluteAdapterPosition) return txSpeed
            index++
        }
        return TXSpeed.CUSTOM
    }

    val isLockedGas: Boolean
        get() = lockedGas

    /** Maintains Java compatibility for callers checking lock state. */
    fun hasLockedGas(): Boolean = lockedGas

    /** Returns the legacy lock flag. */
    fun isLocked(): Boolean = lockedGas

    fun getBaseFee(): BigInteger = baseFee

    /** Adds a custom tier using an EIP-1559 fee structure. */
    fun addCustomGas(seconds: Long, fee: EIP1559FeeOracleResult) {
        val currentCustom = fees[TXSpeed.CUSTOM] ?: return
        fees[TXSpeed.CUSTOM] = GasSpeed(currentCustom.speed, seconds, fee)
    }

    /** Returns the currently selected EIP-1559 fee object. */
    fun getCurrentGasFee(): EIP1559FeeOracleResult = getGasSpeed().gasPrice

    /** Returns the estimated confirmation time for the current tier. */
    fun getCurrentTimeEstimate(): Long = getGasSpeed().seconds

    /** Returns the active gas tier, defaulting to STANDARD when unavailable. */
    fun getGasSpeed(): GasSpeed {
        return fees[speedIndex]
            ?: fees[TXSpeed.STANDARD]
            ?: fees.values.firstOrNull()
            ?: GasSpeed("", STANDARD_SECONDS, BigInteger.ZERO)
    }

    /** Returns true when the custom tier has been defined. */
    fun hasCustom(): Boolean {
        val custom = fees[TXSpeed.CUSTOM] ?: return false
        return custom.seconds != 0L
    }

    /** Returns true when at least one tier reports a non-zero fee. */
    fun isResultValid(): Boolean {
        return TXSpeed.values().any { speed ->
            val maxFee = fees[speed]?.gasPrice?.maxFeePerGas
            maxFee != null && maxFee > BigInteger.ZERO
        }
    }

    /** Finds the adapter index for the supplied speed. */
    fun findItem(currentGasSpeedIndex: TXSpeed): Int {
        var index = 0
        for (txSpeed in TXSpeed.values()) {
            if (txSpeed == currentGasSpeedIndex) return index
            if (fees[txSpeed] != null) index++
        }
        return 0
    }

    /** Returns the custom tier, if present. */
    fun getCustom(): GasSpeed? = fees[TXSpeed.CUSTOM]

    /** Returns a snapshot of the legacy gas prices for persistence. */
    fun getGasFees(): Map<TXSpeed, BigInteger> {
        val map = EnumMap<TXSpeed, BigInteger>(TXSpeed::class.java)
        fees.forEach { (speed, gasSpeed) ->
            map[speed] = gasSpeed.gasPrice.maxFeePerGas
        }
        return map
    }

    /** Applies an EIP-1559 custom tier using the current base fee. */
    fun setCustom(maxFeePerGas: BigInteger, maxPriorityFeePerGas: BigInteger, fastSeconds: Long) {
        val custom = fees[TXSpeed.CUSTOM] ?: return
        val base = fees[TXSpeed.RAPID]?.gasPrice?.baseFee ?: custom.gasPrice.baseFee
        fees[TXSpeed.CUSTOM] = GasSpeed(
            custom.speed,
            fastSeconds,
            EIP1559FeeOracleResult(maxFeePerGas, maxPriorityFeePerGas, base),
        )
    }

    /** Applies a custom tier using an existing GasSpeed value. */
    fun setCustom(gasSpeed: GasSpeed?) {
        gasSpeed ?: return
        val base = fees[TXSpeed.RAPID]?.gasPrice?.baseFee ?: gasSpeed.gasPrice.baseFee
        fees[TXSpeed.CUSTOM] = GasSpeed(
            gasSpeed.speed,
            gasSpeed.seconds,
            EIP1559FeeOracleResult(gasSpeed.gasPrice.maxFeePerGas, gasSpeed.gasPrice.priorityFee, base),
        )
    }

    /** Applies a legacy (pre-1559) custom tier. */
    fun setCustom(gasPrice: BigInteger, fastSeconds: Long) {
        val custom = fees[TXSpeed.CUSTOM] ?: return
        fees[TXSpeed.CUSTOM] = GasSpeed(custom.speed, fastSeconds, gasPrice)
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(timeStamp)
        dest.writeInt(fees.size)
        dest.writeInt(speedIndex.ordinal)
        dest.writeByte(if (lockedGas) 1 else 0)
        fees.forEach { (speed, value) ->
            dest.writeInt(speed.ordinal)
            dest.writeParcelable(value, flags)
        }
    }

    // region Constructors

    constructor(ctx: Context, result: Map<Int, EIP1559FeeOracleResult>?) : this() {
        lockedGas = false
        timeStamp = System.currentTimeMillis()
        if (result.isNullOrEmpty()) return
        setComponents(ctx, result)
        val standard = fees[TXSpeed.STANDARD]?.gasPrice ?: return
        fees[TXSpeed.CUSTOM] = GasSpeed(
            ctx.getString(R.string.speed_custom),
            STANDARD_SECONDS,
            copyFee(standard),
        )
    }

    constructor(ctx: Context, gasSpread: GasPriceSpread, result: Map<Int, EIP1559FeeOracleResult>?) : this() {
        lockedGas = false
        timeStamp = System.currentTimeMillis()
        if (result.isNullOrEmpty()) return
        setComponents(ctx, result)
        val custom = gasSpread.getSelectedGasFee(TXSpeed.CUSTOM) ?: return
        fees[TXSpeed.CUSTOM] = GasSpeed(
            ctx.getString(R.string.speed_custom),
            custom.seconds,
            copyFee(custom.gasPrice),
        )
    }

    constructor(ctx: Context, maxFeePerGas: BigInteger, maxPriorityFeePerGas: BigInteger) : this() {
        lockedGas = false
        timeStamp = System.currentTimeMillis()
        val baseFeeApprox = maxFeePerGas.subtract(maxPriorityFeePerGas.divide(BigInteger.valueOf(2)))
        val standardFee = EIP1559FeeOracleResult(maxFeePerGas, maxPriorityFeePerGas, baseFeeApprox)
        fees[TXSpeed.STANDARD] = GasSpeed(ctx.getString(R.string.speed_average), STANDARD_SECONDS, standardFee)
        fees[TXSpeed.CUSTOM] = GasSpeed(ctx.getString(R.string.speed_custom), STANDARD_SECONDS, copyFee(standardFee))
    }

    constructor(currentAvGasPrice: BigInteger, lockedGas: Boolean) : this() {
        this.lockedGas = lockedGas
        timeStamp = System.currentTimeMillis()
        val multiplier = BigDecimal("1.2")
        val rapidValue = BigDecimal(currentAvGasPrice).multiply(multiplier).toBigInteger()
        fees[TXSpeed.FAST] = GasSpeed("", FAST_SECONDS, rapidValue)
        fees[TXSpeed.STANDARD] = GasSpeed("", STANDARD_SECONDS, currentAvGasPrice)
    }

    constructor(ctx: Context, gasPrice: BigInteger) : this() {
        lockedGas = false
        timeStamp = System.currentTimeMillis()
        fees[TXSpeed.STANDARD] = GasSpeed(ctx.getString(R.string.speed_average), STANDARD_SECONDS, gasPrice)
        fees[TXSpeed.CUSTOM] = GasSpeed(ctx.getString(R.string.speed_custom), STANDARD_SECONDS, gasPrice)
    }

    constructor(ctx: Context, apiReturn: String) : this() {
        lockedGas = false
        timeStamp = System.currentTimeMillis()
        parseEIP1559Result(ctx, apiReturn)
    }

    constructor(apiReturn: String) : this() {
        lockedGas = false
        timeStamp = System.currentTimeMillis()
        parseLegacyResult(apiReturn)
    }

    constructor(
        ctx: Context,
        gasSpread: GasPriceSpread?,
        timestamp: Long,
        feeMap: Map<TXSpeed, BigInteger>,
        locked: Boolean,
    ) : this() {
        lockedGas = locked
        timeStamp = timestamp
        addLegacyGasFee(ctx, TXSpeed.RAPID, RAPID_SECONDS, R.string.speed_rapid, feeMap)
        addLegacyGasFee(ctx, TXSpeed.FAST, FAST_SECONDS, R.string.speed_fast, feeMap)
        addLegacyGasFee(ctx, TXSpeed.STANDARD, STANDARD_SECONDS, R.string.speed_average, feeMap)
        addLegacyGasFee(ctx, TXSpeed.SLOW, SLOW_SECONDS, R.string.speed_slow, feeMap)

        val custom = gasSpread?.getSelectedGasFee(TXSpeed.CUSTOM)
        if (custom != null) {
            fees[TXSpeed.CUSTOM] = GasSpeed(
                ctx.getString(R.string.speed_custom),
                custom.seconds,
                custom.gasPrice.maxFeePerGas,
            )
        } else {
            feeMap[TXSpeed.STANDARD]?.let { standardPrice ->
                fees[TXSpeed.CUSTOM] = GasSpeed(
                    ctx.getString(R.string.speed_custom),
                    STANDARD_SECONDS,
                    standardPrice,
                )
            }
        }
    }

    private constructor(parcel: Parcel) : this() {
        timeStamp = parcel.readLong()
        val feeCount = parcel.readInt()
        val feeIndex = parcel.readInt()
        speedIndex = TXSpeed.values()[feeIndex]
        lockedGas = parcel.readByte().toInt() == 1
        repeat(feeCount) {
            val entry = parcel.readInt()
            val gasSpeed = parcel.readParcelable<GasSpeed>(GasSpeed::class.java.classLoader)
            if (gasSpeed != null) {
                fees[TXSpeed.values()[entry]] = gasSpeed
            }
        }
    }

    // endregion

    /**
     * Populates the internal fee map with EIP-1559 data from an oracle response.
     */
    private fun setComponents(ctx: Context, result: Map<Int, EIP1559FeeOracleResult>) {
        if (result.isEmpty()) return
        val quarter = if (result.size >= 4) result.size / 4 else 0
        result[0]?.let {
            fees[TXSpeed.RAPID] = GasSpeed(ctx.getString(R.string.speed_rapid), RAPID_SECONDS, EIP1559FeeOracleResult(it))
        }
        result[quarter]?.let {
            fees[TXSpeed.FAST] = GasSpeed(ctx.getString(R.string.speed_fast), FAST_SECONDS, EIP1559FeeOracleResult(it))
        }
        result[quarter * 2]?.let {
            fees[TXSpeed.STANDARD] = GasSpeed(ctx.getString(R.string.speed_average), STANDARD_SECONDS, EIP1559FeeOracleResult(it))
        }
        result[result.size - 1]?.let {
            fees[TXSpeed.SLOW] = GasSpeed(ctx.getString(R.string.speed_slow), SLOW_SECONDS, EIP1559FeeOracleResult(it))
        }

        val standard = fees[TXSpeed.STANDARD]?.gasPrice ?: return
        for (speed in TXSpeed.values()) {
            if (speed == TXSpeed.STANDARD) continue
            val gasSpeed = fees[speed]?.gasPrice ?: continue
            if (gasSpeed.priorityFee == standard.priorityFee && gasSpeed.maxFeePerGas == standard.maxFeePerGas) {
                fees.remove(speed)
            }
        }
    }

    /**
     * Applies legacy gas estimates to the fee map.
     */
    private fun addLegacyGasFee(
        ctx: Context,
        speed: TXSpeed,
        seconds: Long,
        labelRes: Int,
        feeMap: Map<TXSpeed, BigInteger>,
    ) {
        val gasPrice = feeMap[speed]
        if (gasPrice != null && gasPrice > BigInteger.ZERO) {
            fees[speed] = GasSpeed(ctx.getString(labelRes), seconds, gasPrice)
        }
    }

    /**
     * Parses a JSON payload from the EIP-1559 gas oracle.
     */
    private fun parseEIP1559Result(ctx: Context, apiReturn: String) {
        var baseFeeEstimate = BigDecimal.ZERO
        try {
            val result = JSONObject(apiReturn)
            if (result.has("estimatedBaseFee")) {
                baseFeeEstimate = BigDecimal(result.getString("estimatedBaseFee"))
            }
            val low = readFeeResult(result, "low", baseFeeEstimate)
            val medium = readFeeResult(result, "medium", baseFeeEstimate)
            val high = readFeeResult(result, "high", baseFeeEstimate)
            if (low == null || medium == null || high == null) return

            val rapidPriorityFee = BigDecimal(high.priorityFee).multiply(BigDecimal("1.2")).toBigInteger()
            val rapid = EIP1559FeeOracleResult(high.maxFeePerGas, rapidPriorityFee, gweiToWei(baseFeeEstimate))

            fees[TXSpeed.SLOW] = GasSpeed(ctx.getString(R.string.speed_slow), SLOW_SECONDS, low)
            fees[TXSpeed.STANDARD] = GasSpeed(ctx.getString(R.string.speed_average), STANDARD_SECONDS, medium)
            fees[TXSpeed.FAST] = GasSpeed(ctx.getString(R.string.speed_fast), FAST_SECONDS, high)
            fees[TXSpeed.RAPID] = GasSpeed(ctx.getString(R.string.speed_rapid), RAPID_SECONDS, rapid)
            baseFee = gweiToWei(baseFeeEstimate)
        } catch (e: JSONException) {
            Timber.w(e)
        }
    }

    /**
     * Parses a legacy Etherscan gas oracle response.
     */
    private fun parseLegacyResult(apiReturn: String) {
        var rapid = BigDecimal.ZERO
        var fast = BigDecimal.ZERO
        var standard = BigDecimal.ZERO
        var slow = BigDecimal.ZERO
        var base = BigDecimal.ZERO
        try {
            val result = JSONObject(apiReturn)
            if (result.has("result")) {
                val data = result.getJSONObject("result")
                fast = BigDecimal(data.getString("FastGasPrice"))
                rapid = fast.multiply(BigDecimal("1.2"))
                standard = BigDecimal(data.getString("ProposeGasPrice"))
                slow = BigDecimal(data.getString("SafeGasPrice"))
                base = BigDecimal(data.optString("suggestBaseFee", "0"))
            }
        } catch (e: JSONException) {
            Timber.w(e)
        }

        fees[TXSpeed.RAPID] = GasSpeed("", RAPID_SECONDS, gweiToWei(rapid))
        fees[TXSpeed.FAST] = GasSpeed("", FAST_SECONDS, gweiToWei(fast))
        fees[TXSpeed.STANDARD] = GasSpeed("", STANDARD_SECONDS, gweiToWei(standard))
        fees[TXSpeed.SLOW] = GasSpeed("", SLOW_SECONDS, gweiToWei(slow))
        baseFee = gweiToWei(base)
    }

    /**
     * Reads a single fee tier from the JSON payload.
     */
    private fun readFeeResult(result: JSONObject, speed: String, baseFee: BigDecimal): EIP1559FeeOracleResult? {
        return try {
            if (result.has(speed)) {
                val thisSpeed = result.getJSONObject(speed)
                val maxFeePerGas = BigDecimal(thisSpeed.getString("suggestedMaxFeePerGas"))
                val priorityFee = BigDecimal(thisSpeed.getString("suggestedMaxPriorityFeePerGas"))
                EIP1559FeeOracleResult(gweiToWei(maxFeePerGas), gweiToWei(priorityFee), gweiToWei(baseFee))
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e("Infura GasOracle read failing; please adjust your Infura API settings.")
            null
        }
    }

    /**
     * Creates a deep copy of an EIP-1559 result to avoid shared references.
     */
    private fun copyFee(fee: EIP1559FeeOracleResult): EIP1559FeeOracleResult = EIP1559FeeOracleResult(fee)
}
