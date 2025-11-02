package com.alphawallet.app.entity

import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.token.entity.ContractAddress

class TokensMapping {
    @JvmField
    var contracts: List<ContractAddress>? = null
    private var group: String? = null

    fun getGroup(): TokenGroup {
        if (group == null) return TokenGroup.ASSET

        return when (group) {
            "Assets" -> TokenGroup.ASSET
            "Governance" -> TokenGroup.GOVERNANCE
            "DeFi" -> TokenGroup.DEFI
            "Spam" -> TokenGroup.SPAM
            else -> TokenGroup.ASSET
        }
    }

    fun setGroup(group: String?) {
        this.group = group
    }
}
