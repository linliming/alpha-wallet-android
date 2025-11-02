package com.alphawallet.app.entity

import java.math.BigDecimal

/**
 * Created by James on 23/02/2019.
 * Stormbird in Singapore
 */
class EthTypeParam
    (@JvmField var type: String, @JvmField var value: String) {
    init {
        if (type.contains("uint")) {
            //v is in exp form
            try {
                val convStr = value.toDouble().toString()
                val bi = BigDecimal(convStr)
                value = bi.toPlainString()
            } catch (e: NumberFormatException) {
                //do nothing
            }
        }
    }
}
