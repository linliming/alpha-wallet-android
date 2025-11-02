package com.alphawallet.app.entity

import android.os.Parcel
import android.os.Parcelable
import com.alphawallet.app.util.BalanceUtils
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Captures the fee values returned by an EIP-1559 gas oracle, ensuring nullable values fall back to a safe minimum.
 */
class EIP1559FeeOracleResult(
    maxFee: BigInteger?,
    maxPriority: BigInteger?,
    val baseFee: BigInteger,
) : Parcelable {

    val maxFeePerGas: BigInteger = fixGasPriceReturn(maxFee)
    val priorityFee: BigInteger = fixGasPriceReturn(maxPriority)

    constructor(other: EIP1559FeeOracleResult) : this(other.maxFeePerGas, other.priorityFee, other.baseFee)

    private constructor(parcel: Parcel) : this(
        maxFee = parcel.readString()?.let { BigInteger(it, 16) },
        maxPriority = parcel.readString()?.let { BigInteger(it, 16) },
        baseFee = parcel.readString()?.let { BigInteger(it, 16) } ?: BigInteger.ZERO,
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(maxFeePerGas.toString(16))
        dest.writeString(priorityFee.toString(16))
        dest.writeString(baseFee.toString(16))
    }

    override fun describeContents(): Int = 0

    private fun fixGasPriceReturn(input: BigInteger?): BigInteger =
        input ?: BalanceUtils.gweiToWei(BigDecimal.ONE)

    @Suppress("unused")
    private fun minOneGwei(input: BigInteger): BigInteger =
        input.max(BalanceUtils.gweiToWei(BigDecimal.ONE))

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<EIP1559FeeOracleResult> =
            object : Parcelable.Creator<EIP1559FeeOracleResult> {
                override fun createFromParcel(source: Parcel): EIP1559FeeOracleResult =
                    EIP1559FeeOracleResult(source)

                override fun newArray(size: Int): Array<EIP1559FeeOracleResult?> = arrayOfNulls(size)
            }
    }
}

