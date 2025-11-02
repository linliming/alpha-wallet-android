package com.alphawallet.app.entity.tokens

import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.EasAttestation
import com.alphawallet.app.entity.NetworkInfo
import com.alphawallet.app.entity.attestation.ImportAttestation
import com.alphawallet.app.repository.entity.RealmAttestation
import com.alphawallet.app.repository.entity.RealmToken
import com.alphawallet.app.util.Utils
import com.alphawallet.app.walletconnect.util.toByteArray
import com.google.gson.Gson
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.charset.StandardCharsets

/**
 * Factory responsible for instantiating [Token] implementations based on
 * contract type, persisted realm data, or attestation payloads.
 */
class TokenFactory {

    fun createToken(
        originalInfo: TokenInfo,
        balance: BigDecimal,
        balances: List<BigInteger>?,
        updateBlancaTime: Long,
        type: ContractType,
        networkName: String,
        lastBlockCheck: Long,
    ): Token {
        var tokenInfo = originalInfo
        val token: Token = when (type) {
            ContractType.ERC875, ContractType.ERC875_LEGACY -> {
                val ticketBalances = balances ?: emptyList<BigInteger>()
                Ticket(tokenInfo, ticketBalances, updateBlancaTime, networkName, type)
            }
            ContractType.ERC721_TICKET -> {
                val ticketBalances = balances ?: emptyList<BigInteger>()
                ERC721Ticket(tokenInfo, ticketBalances, updateBlancaTime, networkName, type)
            }
            ContractType.ERC721,
            ContractType.ERC721_ENUMERABLE,
            ContractType.ERC721_LEGACY -> {
                if (tokenInfo.decimals != 0) {
                    tokenInfo = TokenInfo(
                        tokenInfo.address,
                        tokenInfo.name,
                        tokenInfo.symbol,
                        0,
                        tokenInfo.isEnabled,
                        tokenInfo.chainId,
                    )
                }
                val erc721 = ERC721Token(tokenInfo, null, balance, updateBlancaTime, networkName, type)
                if (balance.compareTo(BigDecimal.ZERO) >= 0) {
                    erc721.balance = balance
                }
                erc721
            }
            ContractType.ERC1155 -> {
                tokenInfo = TokenInfo(
                    tokenInfo.address,
                    tokenInfo.name,
                    tokenInfo.symbol,
                    0,
                    tokenInfo.isEnabled,
                    tokenInfo.chainId,
                )
                ERC1155Token(tokenInfo, null, updateBlancaTime, networkName).apply {
                    this.balance = balance
                }
            }
            else -> {
                Token(tokenInfo, balance, updateBlancaTime, networkName, type)
            }
        }

        token.lastBlockCheck = lastBlockCheck
        return token
    }

    fun createToken(
        originalInfo: TokenInfo,
        realmItem: RealmToken,
        updateBlancaTime: Long,
        networkName: String,
    ): Token {
        var tokenInfo = originalInfo
        var typeOrdinal = realmItem.interfaceSpec
        if (typeOrdinal > ContractType.CREATION.ordinal) {
            typeOrdinal = ContractType.NOT_SET.ordinal
        }

        val type = ContractType.values()[typeOrdinal]
        var realmBalance = realmItem.balance ?: "0"
        val decimalBalance = if (Utils.isNumeric(realmBalance)) {
            BigDecimal(realmBalance)
        } else {
            BigDecimal.ZERO
        }

        val token: Token = when (type) {
            ContractType.ETHEREUM_INVISIBLE -> {
                tokenInfo.isEnabled = false
                Token(tokenInfo, decimalBalance, updateBlancaTime, networkName, type).apply {
                    pendingBalance = decimalBalance
                }
            }
            ContractType.ETHEREUM -> {
                tokenInfo.isEnabled = true
                Token(tokenInfo, decimalBalance, updateBlancaTime, networkName, type).apply {
                    pendingBalance = decimalBalance
                }
            }
            ContractType.ERC20, ContractType.DYNAMIC_CONTRACT -> {
                Token(tokenInfo, decimalBalance, updateBlancaTime, networkName, type).apply {
                    pendingBalance = decimalBalance
                }
            }
            ContractType.ERC721_TICKET -> {
                if (realmBalance == "0") realmBalance = ""
                ERC721Ticket(tokenInfo, realmBalance, updateBlancaTime, networkName, type)
            }
            ContractType.ERC875, ContractType.ERC875_LEGACY -> {
                if (realmBalance == "0") realmBalance = ""
                Ticket(tokenInfo, realmBalance, updateBlancaTime, networkName, type)
            }
            ContractType.CURRENCY -> {
                Token(tokenInfo, decimalBalance, updateBlancaTime, networkName, ContractType.ETHEREUM).apply {
                    pendingBalance = decimalBalance
                }
            }
            ContractType.ERC721,
            ContractType.ERC721_LEGACY,
            ContractType.ERC721_ENUMERABLE,
            ContractType.ERC721_UNDETERMINED -> {
                ERC721Token(tokenInfo, null, decimalBalance, updateBlancaTime, networkName, type)
            }
            ContractType.ERC1155 -> {
                ERC1155Token(tokenInfo, null, updateBlancaTime, networkName)
            }
            else -> Token(tokenInfo, BigDecimal.ZERO, updateBlancaTime, networkName, type)
        }

        token.setupRealmToken(realmItem)
        return token
    }

    fun createToken(tokenInfo: TokenInfo, type: ContractType, networkName: String): Token {
        var info = tokenInfo
        val currentTime = System.currentTimeMillis()
        return when (type) {
            ContractType.ERC875, ContractType.ERC875_LEGACY ->
                Ticket(info, mutableListOf<BigInteger>(), currentTime, networkName, type)

            ContractType.ERC721_TICKET ->
                ERC721Ticket(info, mutableListOf<BigInteger>(), currentTime, networkName, type)

            ContractType.ERC721,
            ContractType.ERC721_LEGACY,
            ContractType.ERC721_UNDETERMINED,
            ContractType.ERC721_ENUMERABLE ->
                ERC721Token(info, null, BigDecimal.ZERO, currentTime, networkName, type)

            ContractType.ETHEREUM -> {
                val split = info.address?.split("-") ?: emptyList()
                info = TokenInfo(
                    split.getOrElse(0) { info.address },
                    info.name,
                    info.symbol,
                    info.decimals,
                    true,
                    info.chainId,
                )
                Token(info, BigDecimal.ZERO, currentTime, networkName, type).apply {
                    pendingBalance = BigDecimal.ZERO
                }
            }

            ContractType.ERC1155 ->
                ERC1155Token(info, null, currentTime, networkName)

            ContractType.ERC20,
            ContractType.DYNAMIC_CONTRACT -> {
                info = TokenInfo(
                    info.address,
                    info.name,
                    info.symbol,
                    info.decimals,
                    true,
                    info.chainId,
                )
                Token(info, BigDecimal.ZERO, currentTime, networkName, type)
            }
            else -> {
                info = TokenInfo(
                    info.address,
                    info.name,
                    info.symbol,
                    info.decimals,
                    true,
                    info.chainId,
                )
                Token(info, BigDecimal.ZERO, currentTime, networkName, type)
            }
        }
    }

    fun createTokenInfo(realmItem: RealmToken): TokenInfo {
        return TokenInfo(
            realmItem.tokenAddress,
            realmItem.name,
            realmItem.symbol,
            realmItem.decimals,
            realmItem.isEnabled,
            realmItem.chainId,
        )
    }

    fun createAttestation(
        realmAttestation: RealmAttestation,
        baseToken: Token?,
        networkInfo: NetworkInfo,
        wallet: String,
    ): Token {
        val parsed = Utils.parseEASAttestation(realmAttestation.getAttestationLink())
        val jsonAttestation = Utils.toAttestationJson(parsed)
        return if (jsonAttestation.isNullOrEmpty()) {
            getLegacyAttestation(realmAttestation, baseToken, networkInfo, wallet)
        } else {
            val easAttn = Gson().fromJson(jsonAttestation, EasAttestation::class.java)
            val signer = ImportAttestation.recoverSigner(easAttn)
            val tokenInfo = createAttestationTokenInfo(baseToken, networkInfo, realmAttestation.getTokenAddress())
            val attestation = Attestation(
                tokenInfo,
                networkInfo.name,
                realmAttestation.getAttestationLink().toByteArray(StandardCharsets.UTF_8),
            )
            attestation.setTokenWallet(wallet)
            attestation.loadAttestationData(realmAttestation, signer)
            attestation
        }
    }

    private fun createAttestationTokenInfo(
        token: Token?,
        info: NetworkInfo,
        tokenAddress: String,
    ): TokenInfo {
        return token?.tokenInfo ?: Attestation.getDefaultAttestationInfo(info.chainId, tokenAddress)
    }

    private fun getLegacyAttestation(
        realmAttestation: RealmAttestation,
        token: Token?,
        info: NetworkInfo,
        wallet: String,
    ): Token {
        val tokenInfo = token?.tokenInfo ?: Attestation.getDefaultAttestationInfo(info.chainId, realmAttestation.getTokenAddress())
        val attestation = Attestation(
            tokenInfo,
            info.shortName,
            realmAttestation.attestation.orEmpty().toByteArray(),
        )
        attestation.loadAttestationData(realmAttestation, "")
        attestation.setTokenWallet(wallet)
        return attestation
    }
}
