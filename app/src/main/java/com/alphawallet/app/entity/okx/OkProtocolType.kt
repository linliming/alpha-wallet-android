package com.alphawallet.app.entity.okx

import com.alphawallet.app.entity.ContractType

enum class OkProtocolType
    (val value: String) {
    ERC_20("token_20"),
    ERC_721("token_721"),
    ERC_1155("token_1155");

    companion object {
        fun getStandardType(type: OkProtocolType): ContractType {
            return when (type) {
                ERC_20 -> {
                    ContractType.ERC20
                }

                ERC_721 -> {
                    ContractType.ERC721
                }

                ERC_1155 -> {
                    ContractType.ERC1155
                }
            }

            return ContractType.ERC20
        }
    }
}
