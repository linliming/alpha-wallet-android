package com.alphawallet.app.entity

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat

/**
 * Created by James on 25/02/2019.
 * Stormbird in Singapore
 */
class EIP681Request {
    private val PROTOCOL = "ethereum:"
    private val address: String
    private val weiAmount: BigDecimal
    private val chainId: Long
    private var contractAddress: String? = null

    constructor(displayAddress: String, chainId: Long, weiAmount: BigDecimal) {
        this.address = displayAddress
        this.chainId = chainId
        this.weiAmount = weiAmount
    }

    constructor(
        userAddress: String,
        contractAddress: String?,
        chainId: Long,
        weiAmount: BigDecimal
    ) {
        this.address = userAddress
        this.chainId = chainId
        this.weiAmount = weiAmount
        this.contractAddress = contractAddress
    }

    fun generateRequest(): String {
        val sb = StringBuilder()
        sb.append(PROTOCOL)
        sb.append(address)
        sb.append("@")
        sb.append(chainId)
        sb.append("?value=")
        sb.append(format(weiAmount))

        return sb.toString()
    }

    private fun format(x: BigDecimal): String {
        val formatter: NumberFormat = DecimalFormat("0.#E0")
        formatter.roundingMode = RoundingMode.HALF_UP
        formatter.maximumFractionDigits = 6
        return formatter.format(x).replace(",", ".")
    }

    fun generateERC20Request(): String {
        //ethereum:0x744d70fdbe2ba4cf95131626614a1763df805b9e/transfer?address=0x3d597789ea16054a084ac84ce87f50df9198f415&uint256=314e17
        val sb = StringBuilder()
        sb.append(PROTOCOL)
        sb.append(contractAddress)
        sb.append("@")
        sb.append(chainId)
        sb.append("/transfer?address=")
        sb.append(address)
        sb.append("?uint256=")
        sb.append(format(weiAmount))

        return sb.toString()
    }
}
