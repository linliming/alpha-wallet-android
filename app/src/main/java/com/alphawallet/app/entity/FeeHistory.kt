package com.alphawallet.app.entity

/**
 * Created by JB on 18/12/2021.
 */
class FeeHistory {
    var baseFeePerGas: Array<String> = arrayOf()
    var gasUsedRatio: DoubleArray = doubleArrayOf()
    var oldestBlock: String? = null
    var reward: Array<Array<String>> = Array<Array<String>>(
        0,
        init = TODO()
    )
}
