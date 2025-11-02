package com.alphawallet.app.entity

import com.alphawallet.app.entity.tokens.Token

interface BuyCryptoInterface {
    fun handleBuyFunction(token: Token?)
    fun handleGeneratePaymentRequest(token: Token?)
}
