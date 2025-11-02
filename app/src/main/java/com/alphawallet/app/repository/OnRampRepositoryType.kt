package com.alphawallet.app.repository

import com.alphawallet.app.entity.OnRampContract
import com.alphawallet.app.entity.tokens.Token

interface OnRampRepositoryType {
    fun getUri(address: String?, token: Token?): String?

    fun getContract(token: Token?): OnRampContract
}
