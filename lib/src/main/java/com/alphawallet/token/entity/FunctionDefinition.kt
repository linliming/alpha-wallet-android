package com.alphawallet.token.entity

import com.alphawallet.token.tools.TokenDefinition
import java.math.BigInteger

/**
 * Created by James on 10/11/2018.
 * Stormbird in Singapore
 */
class FunctionDefinition {
    @JvmField
    var contract: ContractInfo? = null

    @JvmField
    var method: String? = null
    var syntax: TokenDefinition.Syntax? = null

    @JvmField
    var asDefin: As? = null

    @JvmField
    var parameters: MutableList<MethodArg> = ArrayList()

    var result: String? = null
    var resultTime: Long = 0
    var tokenId: BigInteger? = null

    @JvmField
    var tx: EthereumTransaction? = null
    var namedTypeReturn: String? = null

    val tokenRequirement: Int
        get() {
            var count = 0
            for (arg in parameters) {
                if (arg.isTokenId) count++
            }

            if (count == 0) count = 1

            return count
        }
}
