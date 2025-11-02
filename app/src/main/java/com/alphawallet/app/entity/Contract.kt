package com.alphawallet.app.entity

import com.alphawallet.app.entity.tokens.TokenInfo
import java.math.BigDecimal

/**
 * Created by James on 26/02/2019.
 * Stormbird in Singapore
 */

/**
 * This is a superclass for all contracts.
 * A pure 'contract' class is never created hence declaring as an abstract.
 * Examples:
 * - ERC20 Token
 * - ERC875 Token
 * - ERC721 Token
 */
abstract class Contract {
    var tokenInfo: TokenInfo? = null
    var balance: BigDecimal? = null
    var updateBlancaTime: Long = 0
    var balanceIsLive: Boolean = false
    private val tokenWallet: String? = null
    private val tokenNetwork: Short = 0
    private val requiresAuxRefresh = true
    protected var contractType: ContractType? = null
    var lastBlockCheck: Long = 0
}
