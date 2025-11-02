package com.alphawallet.app.repository.entity

import com.alphawallet.app.entity.GasPriceSpread
import com.alphawallet.app.entity.TXSpeed
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.math.BigInteger

open class RealmGasSpread : RealmObject() {

    @PrimaryKey
    var chainId: Long = 0L

    var rapid: String? = null
    var fast: String? = null
    var standard: String? = null
    var slow: String? = null
    var baseFee: String? = null
    var timeStamp: Long = 0L

    fun setGasSpread(spread: GasPriceSpread, time: Long) {
        rapid = addGasPrice(TXSpeed.RAPID, spread)
        fast = addGasPrice(TXSpeed.FAST, spread)
        standard = addGasPrice(TXSpeed.STANDARD, spread)
        slow = addGasPrice(TXSpeed.SLOW, spread)
        timeStamp = time
    }

    private fun addGasPrice(speed: TXSpeed, spread: GasPriceSpread): String {
        val fee = spread.getSelectedGasFee(speed)
        return fee?.gasPrice?.maxFeePerGas?.toString() ?: "0"
    }

    fun getGasFee(speed: TXSpeed): BigInteger {
        val value = when (speed) {
            TXSpeed.RAPID -> rapid
            TXSpeed.FAST -> fast
            TXSpeed.STANDARD -> standard
            TXSpeed.SLOW -> slow
            else -> null
        }

        return try {
            if (value.isNullOrEmpty()) BigInteger.ZERO else BigInteger(value)
        } catch (_: Exception) {
            BigInteger.ZERO
        }
    }

    fun getGasFees(): Map<TXSpeed, BigInteger> {
        val slowGas = slow?.let {
            if (it.contains(",")) it.split(",")[0] else it
        }

        return mapOf(
            TXSpeed.RAPID to (rapid?.let { BigInteger(it) } ?: BigInteger.ZERO),
            TXSpeed.FAST to (fast?.let { BigInteger(it) } ?: BigInteger.ZERO),
            TXSpeed.STANDARD to (standard?.let { BigInteger(it) } ?: BigInteger.ZERO),
            TXSpeed.SLOW to (slowGas?.let { BigInteger(it) } ?: BigInteger.ZERO)
        )
    }

    fun getGasPrice(): BigInteger {
        var gasPrice = getGasFee(TXSpeed.STANDARD)
        if (gasPrice > BigInteger.ZERO) {
            return gasPrice
        }

        for (txSpeed in TXSpeed.values()) {
            gasPrice = getGasFee(txSpeed)
            if (gasPrice > BigInteger.ZERO) {
                return gasPrice
            }
        }

        return BigInteger.ZERO
    }

    fun isLocked(): Boolean {
        val slowValue = slow ?: return false
        return if (slowValue.contains(",")) {
            val parts = slowValue.split(",")
            parts.getOrNull(1)?.firstOrNull() == '0'
        } else {
            false
        }
    }
}
