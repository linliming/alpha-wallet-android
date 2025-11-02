package com.alphawallet.app.entity

import com.alphawallet.app.C
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.entity.tokens.TokenInfo
import com.alphawallet.ethereum.EthereumNetworkBase
import java.util.Arrays

object CustomViewSettings {
    const val primaryChain: Long = EthereumNetworkBase.MAINNET_ID
    private const val primaryChainName = C.ETHEREUM_NETWORK_NAME

    //You can use the settings in this file to customise the wallet appearance
    //IF you want to re-order the the way chains appear in the wallet, see this line in EthereumNetworkBase:
    //private static final List<Long> hasValue = new ArrayList<>(Arrays.asList( ...
    //... and read the comment above it
    //Ensures certain tokens are always visible, even if zero balance (see also 'showZeroBalance()' below).
    //See also lockedChains. You can also lock the chains that are displayed on.
    //If you leave the locked chains empty, the token will appear if the chain is selected
    val lockedTokens: List<TokenInfo> = mutableListOf()

    //List of chains that wallet can show
    //If blank, enable the user filter select dialog, if there are any entries here, the select network dialog is disabled
    //Note: you should always enable the chainId corresponding to the chainIDs in the lockedTokens.
    @JvmField
    val lockedChains: List<Long> = mutableListOf()

    val alwaysVisibleChains: List<Long> = Arrays.asList(
        EthereumNetworkBase.MAINNET_ID
    )

    fun alwaysShow(chainId: Long): Boolean {
        return alwaysVisibleChains.contains(chainId)
    }

    //TODO: Wallet can only show the above tokens
    private const val onlyShowTheseTokens = true

    //TODO: Not yet implemented; code will probably live in TokensService & TokenRealmSource
    fun onlyShowLockedTokens(): Boolean {
        return onlyShowTheseTokens
    }

    //Does main wallet page show tokens with zero balance? NB: any 'Locked' tokens above will always be shown
    fun showZeroBalance(): Boolean {
        return false
    }

    @JvmStatic
    fun tokenCanBeDisplayed(token: TokenCardMeta): Boolean {
        return token.type == ContractType.ETHEREUM || token.isEnabled || isLockedToken(
            token.chain,
            token.address
        )
    }

    private fun isLockedToken(chainId: Long, contractAddress: String): Boolean {
        for (tInfo in lockedTokens) {
            if (tInfo.chainId == chainId && tInfo.address.equals(
                    contractAddress,
                    ignoreCase = true
                )
            ) return true
        }

        return false
    }

    fun checkKnownTokens(tokenInfo: TokenInfo?): ContractType {
        return ContractType.OTHER
    }

    fun showContractAddress(token: Token?): Boolean {
        return true
    }

    @JvmStatic
    fun startupDelay(): Long {
        return 0
    }

    val imageOverride: Int
        get() = 0

    //Switch off dapp browser
    @JvmStatic
    fun hideDappBrowser(): Boolean {
        return false
    }

    //Hides the filter tab bar at the top of the wallet screen (ALL/CURRENCY/COLLECTIBLES)
    @JvmStatic
    fun hideTabBar(): Boolean {
        return false
    }

    //Use to switch off direct transfer, only use magiclink transfer
    @JvmStatic
    fun hasDirectTransfer(): Boolean {
        return true
    }

    //Allow multiple wallets (true) or single wallet mode (false)
    @JvmStatic
    fun canChangeWallets(): Boolean {
        return true
    }

    //Hide EIP681 generation (Payment request, generates a QR code another wallet user can scan to have all payment fields filled in)
    @JvmStatic
    fun hideEIP681(): Boolean {
        return false
    }

    //In main wallet menu, if wallet allows adding new tokens
    @JvmStatic
    fun canAddTokens(): Boolean {
        return true
    }

    //Implement minimal dappbrowser with no URL bar. You may want this if you want your browser to point to a specific website and only
    // allow navigation within that website
    // use this setting in conjunction with changing DEFAULT_HOMEPAGE in class EthereumNetworkBase
    @JvmStatic
    fun minimiseBrowserURLBar(): Boolean {
        return false
    }

    //Allow showing token management view
    @JvmStatic
    fun showManageTokens(): Boolean {
        return true
    }

    //Show all networks in Select Network screen. Set to `true` to show only filtered networks.
    @JvmStatic
    fun showAllNetworks(): Boolean {
        return false
    }

    val decimalFormat: String
        get() = "0.####E0"

    val decimalPlaces: Int
        get() = 5

    //set if the Input Amount defaults to Fiat or Crypto
    @JvmStatic
    fun inputAmountFiatDefault(): Boolean {
        return false
    }
}
