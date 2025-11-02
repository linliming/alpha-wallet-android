package com.alphawallet.app.repository


import android.util.Pair
import com.alphawallet.app.entity.ContractLocator
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.ImageEntry
import com.alphawallet.app.entity.TransferFromEventResponse
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.entity.tokendata.TokenTicker
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.tokens.TokenCardMeta
import com.alphawallet.app.entity.tokens.TokenInfo
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.token.entity.ContractAddress
import io.realm.Realm
import java.math.BigDecimal
import java.math.BigInteger

interface TokenRepositoryType {
    suspend fun fetchActiveTokenBalance(
        walletAddress: String?,
        token: Token?,
    ): Token?

    suspend fun updateTokenBalance(
        walletAddress: String?,
        token: Token?,
    ): BigDecimal?

    suspend fun getTokenResponse(
        address: String?,
        chainId: Long,
        method: String?,
    ): ContractLocator?

    suspend fun checkInterface(
        token: Token?,
        wallet: Wallet?,
    ): Token?

    fun setEnable(
        wallet: Wallet?,
        cAddr: ContractAddress?,
        isEnabled: Boolean,
    )

    fun setVisibilityChanged(
        wallet: Wallet?,
        cAddr: ContractAddress?,
    )

    suspend fun update(
        address: String?,
        chainId: Long,
        type: ContractType?,
    ): TokenInfo?

    suspend fun burnListenerObservable(contractAddress: String?): TransferFromEventResponse?

    suspend fun getEthTicker(chainId: Long): TokenTicker?

    fun getTokenTicker(token: Token?): TokenTicker?

    suspend fun fetchLatestBlockNumber(chainId: Long): BigInteger?

    fun fetchToken(
        chainId: Long,
        walletAddress: String?,
        address: String?,
    ): Token?

    fun getTokenImageUrl(
        chainId: Long,
        address: String?,
    ): String?

    suspend fun storeTokens(
        wallet: Wallet?,
        tokens: Array<Token?>?,
    ): Array<Token?>?

    suspend fun resolveENS(
        chainId: Long,
        address: String?,
    ): String?

    fun updateAssets(
        wallet: String?,
        erc721Token: Token?,
        additions: List<BigInteger?>?,
        removals: List<BigInteger?>?,
    )

    fun storeAsset(
        currentAddress: String?,
        token: Token?,
        tokenId: BigInteger?,
        asset: NFTAsset?,
    )

    fun initNFTAssets(
        wallet: Wallet?,
        token: Token?,
    ): Token?

    suspend fun determineCommonType(tokenInfo: TokenInfo?): ContractType?

    suspend fun fetchIsRedeemed(
        token: Token?,
        tokenId: BigInteger?,
    ): Boolean?

    fun addImageUrl(entries: List<ImageEntry?>?)

    fun updateLocalAddress(walletAddress: String?)

    fun deleteRealmTokens(
        wallet: Wallet?,
        tcmList: List<TokenCardMeta?>?,
    )

    suspend fun fetchTokenMetas(
        wallet: Wallet?,
        networkFilters: List<Long?>?,
        svs: AssetDefinitionService?,
    ): Array<TokenCardMeta?>?

    suspend fun fetchAllTokenMetas(
        wallet: Wallet?,
        networkFilters: List<Long?>?,
        searchTerm: String?,
    ): Array<TokenCardMeta?>?

    suspend fun fetchTokensThatMayNeedUpdating(
        walletAddress: String?,
        networkFilters: List<Long?>?,
    ): Array<Token?>?

    suspend fun fetchAllTokensWithBlankName(
        walletAddress: String?,
        networkFilters: List<Long?>?,
    ): Array<ContractAddress?>?

    fun fetchTokenMetasForUpdate(
        wallet: Wallet?,
        networkFilters: List<Long?>?,
    ): Array<TokenCardMeta?>?

    fun getRealmInstance(wallet: Wallet?): Realm?

    // TODO
    val tickerRealmInstance: Realm?

    suspend fun fetchChainBalance(
        walletAddress: String?,
        chainId: Long,
    ): BigDecimal?

    suspend fun fixFullNames(
        wallet: Wallet?,
        svs: AssetDefinitionService?,
    ): Int?

    fun isEnabled(newToken: Token?): Boolean

    suspend fun getTotalValue(
        currentAddress: String?,
        networkFilters: List<Long?>?,
    ): Pair<Double?, Double?>?

    suspend fun getTickerUpdateList(networkFilter: List<Long?>?): List<String?>?

    fun getTokenGroup(
        chainId: Long,
        address: String?,
        type: ContractType?,
    ): TokenGroup?

    suspend fun storeTokenInfo(
        wallet: Wallet?,
        tInfo: TokenInfo?,
        type: ContractType?,
    ): TokenInfo?

    fun fetchAttestation(
        chainId: Long,
        currentAddress: String?,
        toLowerCase: String?,
        attnId: String?,
    ): Token?

    fun fetchAttestations(
        chainId: Long,
        walletAddress: String?,
        tokenAddress: String?,
    ): List<Token?>?
}
