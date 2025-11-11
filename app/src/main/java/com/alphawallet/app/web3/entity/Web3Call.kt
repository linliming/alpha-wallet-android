package com.alphawallet.app.web3.entity

import com.alphawallet.app.util.Utils
import org.web3j.protocol.core.DefaultBlockParameter
import java.math.BigInteger

/**
 * Created by JB on 21/07/2020.
 */
class Web3Call
    (
    @JvmField val to: Address,
    @JvmField val blockParam: DefaultBlockParameter,
    @JvmField val payload: String,
    value: String?,
    gasLimit: String?,
    @JvmField val leafPosition: Long
) {
    @JvmField
    val value: BigInteger? = if (value != null) Utils.stringToBigInteger(value) else null
    @JvmField
    val gasLimit: BigInteger? = if (gasLimit != null) Utils.stringToBigInteger(gasLimit) else null
}
