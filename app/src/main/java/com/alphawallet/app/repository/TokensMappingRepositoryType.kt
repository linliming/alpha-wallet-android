package com.alphawallet.app.repository

import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.token.entity.ContractAddress

interface TokensMappingRepositoryType {
    fun getTokenGroup(chainId: Long, address: String?, type: ContractType?): TokenGroup?

    fun getBaseToken(chainId: Long, address: String?): ContractAddress?
}
