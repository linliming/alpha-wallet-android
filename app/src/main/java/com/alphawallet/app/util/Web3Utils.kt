package com.alphawallet.app.util

import com.alphawallet.app.R
import com.alphawallet.ethereum.EthereumNetworkBase.*
import com.alphawallet.token.entity.Signable
import com.alphawallet.token.entity.SignMessageType
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpList
import org.web3j.rlp.RlpString
import org.web3j.utils.Numeric
import java.util.*

const val ALPHAWALLET_REPO_NAME = "https://raw.githubusercontent.com/alphawallet/iconassets/master/"
const val TRUST_ICON_REPO_BASE = "https://raw.githubusercontent.com/trustwallet/assets/master/blockchains/"
private const val ICON_REPO_ADDRESS_TOKEN = "[TOKEN]"
private const val CHAIN_REPO_ADDRESS_TOKEN = "[CHAIN]"
private const val TOKEN_LOGO = "/logo.png"
private const val TRUST_ICON_REPO = TRUST_ICON_REPO_BASE + CHAIN_REPO_ADDRESS_TOKEN + "/assets/" + ICON_REPO_ADDRESS_TOKEN + TOKEN_LOGO
private const val ALPHAWALLET_ICON_REPO = ALPHAWALLET_REPO_NAME + ICON_REPO_ADDRESS_TOKEN + TOKEN_LOGO

private val twChainNames: Map<Long, String> = mapOf(
    CLASSIC_ID to "classic",
    GNOSIS_ID to "xdai",
    BINANCE_MAIN_ID to "smartchain",
    AVALANCHE_ID to "avalanche",
    OPTIMISTIC_MAIN_ID to "optimism",
    POLYGON_ID to "polygon",
    MAINNET_ID to "ethereum"
)

fun getSigningTitle(signable: Signable): Int {
    return when (signable.messageType) {
        SignMessageType.SIGN_PERSONAL_MESSAGE -> R.string.dialog_title_sign_personal_message
        SignMessageType.SIGN_TYPED_DATA,
        SignMessageType.SIGN_TYPED_DATA_V3,
        SignMessageType.SIGN_TYPED_DATA_V4 -> R.string.dialog_title_sign_typed_message
        else -> R.string.dialog_title_sign_message_sheet // SIGN_MESSAGE or default
    }
}

fun String?.getTokenAddrFromUrl(): String {
    if (this.isNullOrEmpty() || !this.startsWith(TRUST_ICON_REPO_BASE)) return ""
    val start = this.lastIndexOf("/assets/") + "/assets/".length
    val end = this.lastIndexOf(TOKEN_LOGO)
    return if (start > 0 && end > 0) {
        this.substring(start, end)
    } else {
        ""
    }
}

fun String?.getTokenAddrFromAWUrl(): String {
    if (this.isNullOrEmpty() || !this.startsWith(ALPHAWALLET_REPO_NAME)) return ""
    val start = ALPHAWALLET_REPO_NAME.length
    val end = this.lastIndexOf(TOKEN_LOGO)
    return if (end > 0 && end > start) {
        this.substring(start, end)
    } else {
        ""
    }
}

fun getTWTokenImageUrl(chainId: Long, address: String): String {
    val repoChain = twChainNames.getOrDefault(chainId, "ethereum")
    return TRUST_ICON_REPO
        .replace(ICON_REPO_ADDRESS_TOKEN, Keys.toChecksumAddress(address))
        .replace(CHAIN_REPO_ADDRESS_TOKEN, repoChain)
}

fun getTokenImageUrl(address: String): String {
    return ALPHAWALLET_ICON_REPO.replace(ICON_REPO_ADDRESS_TOKEN, address.lowercase(Locale.ROOT))
}

fun calculateContractAddress(account: String, nonce: Long): String {
    val addressAsBytes = Numeric.hexStringToByteArray(account)
    var calculatedAddressAsBytes = Hash.sha3(
        RlpEncoder.encode(
            RlpList(
                RlpString.create(addressAsBytes),
                RlpString.create(nonce)
            )
        )
    )
    calculatedAddressAsBytes = calculatedAddressAsBytes.copyOfRange(
        12,
        calculatedAddressAsBytes.size
    )
    return Keys.toChecksumAddress(Numeric.toHexString(calculatedAddressAsBytes))
}
