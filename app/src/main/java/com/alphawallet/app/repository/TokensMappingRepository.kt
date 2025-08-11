package com.alphawallet.app.repository

import android.content.Context
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.TokensMapping
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.util.Utils
import com.alphawallet.token.entity.ContractAddress
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class TokensMappingRepository(private val context: Context) : TokensMappingRepositoryType {
    private var tokenMap: MutableMap<String, TokenGroup>? = null
    private var contractMappings: MutableMap<String, ContractAddress>? = null

    init {
        init()
    }

    private fun init() {
        if (tokenMap == null || contractMappings == null) {
            createMap(Utils.loadJSONFromAsset(context, TOKENS_JSON_FILENAME))
        }
    }

    private fun createMap(mapping: String) {
        tokenMap = HashMap()
        contractMappings = HashMap()
        val tokensMapping = Gson().fromJson<Array<TokensMapping>>(
            mapping,
            object : TypeToken<Array<TokensMapping?>?>() {
            }.type
        )

        if (tokensMapping != null) {
            for (entry in tokensMapping) {
                var baseAddress: ContractAddress? = null
                for (address in entry.contracts) {
                    tokenMap.putIfAbsent(address.addressKey, entry.group)
                    if (baseAddress == null) {
                        baseAddress = address
                    } else {
                        contractMappings.putIfAbsent(
                            address.addressKey,
                            baseAddress
                        ) // make a note of contracts that mirror base addresses - this should be used in the
                    }
                }
            }
        }
    }

    override fun getTokenGroup(chainId: Long, address: String, type: ContractType): TokenGroup {
        if (tokenMap == null) init()

        var result = TokenGroup.ASSET

        val g = tokenMap!![ContractAddress.toAddressKey(chainId, address)]
        if (g != null) {
            result = g
        }

        if (result == TokenGroup.SPAM) {
            return result
        }

        return when (type) {
            ContractType.NOT_SET, ContractType.OTHER, ContractType.ETHEREUM, ContractType.CURRENCY, ContractType.CREATION, ContractType.DELETED_ACCOUNT, ContractType.ERC20 -> result

            ContractType.ERC721, ContractType.ERC721_ENUMERABLE, ContractType.ERC875_LEGACY, ContractType.ERC875, ContractType.ERC1155, ContractType.ERC721_LEGACY, ContractType.ERC721_TICKET, ContractType.ERC721_UNDETERMINED -> TokenGroup.NFT
            else -> result

        }
    }

    /**
     * Return the base token this token was initially derived from or self if there's no mapping
     * @param chainId
     * @param address
     * @return
     */
    override fun getBaseToken(chainId: Long, address: String): ContractAddress? {
        return contractMappings!!.getOrDefault(
            ContractAddress.toAddressKey(chainId, address),
            ContractAddress(chainId, address)
        )
    }

    companion object {
        private const val TOKENS_JSON_FILENAME = "tokens.json"
    }
}
