package com.alphawallet.app.entity.tokens

import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import android.text.format.DateUtils
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.repository.EthereumNetworkBase
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.ui.widget.entity.ChainItem.CHAIN_ITEM_WEIGHT
import com.alphawallet.app.ui.widget.holder.TokenHolder.Companion.CHECK_MARK
import com.alphawallet.ethereum.EthereumNetworkBase.MAINNET_ID
import com.alphawallet.token.entity.ContractAddress
import java.math.BigInteger

/**
 * TokenCardMeta - 代币卡片元数据
 *
 * 用于代币列表、搜索与排序场景，包含：
 * - 合约地址、链信息、余额、上次更新等字段
 * - 排序权重(nameWeight)用于自定义排序
 * - 过滤文本(filterText)用于本地搜索
 *
 * 兼容 Parcelable 与 Comparable，以便在 Android 中进行序列化和排序。
 */
class TokenCardMeta : Comparable<TokenCardMeta>, Parcelable {

    val tokenId: String
    var lastUpdate: Long
    var lastTxUpdate: Long = 0
    private val nameWeight: Long
    val type: ContractType
    val balance: String
    private val filterText: String?
    val group: TokenGroup
    var isEnabled: Boolean = false

    @JvmOverloads
    constructor(
        chainId: Long,
        tokenAddress: String,
        balance: String,
        timeStamp: Long,
        svs: AssetDefinitionService?,
        name: String?,
        symbol: String?,
        type: ContractType,
        group: TokenGroup,
        attnId: String = "",
    ) {
        tokenId = buildTokenId(chainId, tokenAddress, group, attnId)
        lastUpdate = timeStamp
        this.type = type
        this.balance = balance
        nameWeight = calculateTokenNameWeight(
            chainId,
            tokenAddress,
            svs,
            name.orEmpty(),
            symbol.orEmpty(),
            isEthereum(),
            group,
            kotlin.math.abs(attnId.hashCode()),
        )
        this.filterText = "$symbol'$name"
        this.group = group
    }

    constructor(
        chainId: Long,
        tokenAddress: String,
        balance: String,
        timeStamp: Long,
        lastTxUpdate: Long,
        type: ContractType,
        group: TokenGroup,
    ) {
        tokenId = buildTokenId(chainId, tokenAddress, group, "")
        lastUpdate = timeStamp
        this.lastTxUpdate = lastTxUpdate
        this.type = type
        this.balance = balance
        nameWeight = 1000
        filterText = null
        this.group = group
    }

    constructor(token: Token, filterText: String) {
        tokenId = TokensRealmSource.databaseKey(token.tokenInfo.chainId, token.getAddress())
        lastUpdate = token.updateBlancaTime
        lastTxUpdate = token.lastTxCheck
        type = token.getInterfaceSpec()
        balance = token.balance.toString()
        nameWeight = calculateTokenNameWeight(
            token.tokenInfo.chainId,
            token.tokenInfo.address ?: "",
            null,
            token.getName() ?: "",
            token.getSymbol() ?: "",
            token.isEthereum(),
            token.group,
            0,
        )
        this.filterText = filterText
        group = token.group
        isEnabled = filterText.isEmpty() || filterText != CHECK_MARK
    }

    private constructor(parcel: Parcel) {
        tokenId = parcel.readString().orEmpty()
        lastUpdate = parcel.readLong()
        lastTxUpdate = parcel.readLong()
        nameWeight = parcel.readLong()
        type = ContractType.values()[parcel.readInt()]
        balance = parcel.readString().orEmpty()
        filterText = parcel.readString()
        group = TokenGroup.values()[parcel.readInt()]
    }

    fun getAttestationId(): String {
        return if (tokenId.endsWith(Attestation.ATTESTATION_SUFFIX)) {
            val firstSep = tokenId.indexOf('-')
            val secondSep = tokenId.indexOf('-', firstSep + 1)
            tokenId.substring(secondSep + 1, tokenId.length - Attestation.ATTESTATION_SUFFIX.length)
        } else {
            ""
        }
    }

    fun getNameWeight(): Long = nameWeight

    fun getContractAddress(): ContractAddress {
        return ContractAddress(chain, address)
    }

    fun getFilterText(): String? = filterText

    fun hasPositiveBalance(): Boolean = !balance.isNullOrEmpty() && balance != "0"

    fun hasValidName(): Boolean = nameWeight < Long.MAX_VALUE

    fun getTokenGroup(): TokenGroup = group

    fun isNFT(): Boolean = group == TokenGroup.NFT

    fun calculateBalanceUpdateWeight(): Float {
        var updateWeight = 0f
        val timeDiff = (System.currentTimeMillis() - lastUpdate) / DateUtils.SECOND_IN_MILLIS

        updateWeight = when {
            isEthereum() ->
                if (timeDiff > 30) 2.0f else 1.0f

            hasValidName() && isNFT() ->
                if (timeDiff > 30) 0.25f else 0.01f

            hasValidName() && isEnabled ->
                if (hasPositiveBalance()) 1.0f else 0.1f

            hasValidName() -> 0.1f

            else -> updateWeight
        }

        return updateWeight
    }

    fun getUID(): Long = tokenId.hashCode().toLong()

    fun equals(other: TokenCardMeta): Boolean = tokenId.equals(other.tokenId, ignoreCase = true)

    fun calculateUpdateFrequency(): Long {
        val seconds =
            if (isEnabled) {
                if (hasPositiveBalance()) 30 else 120
            } else {
                300
            }
        return seconds * DateUtils.SECOND_IN_MILLIS
    }

    fun isEthereum(): Boolean = type == ContractType.ETHEREUM

    val address: String
        get() = tokenId.substring(0, tokenId.indexOf('-'))

    val chain: Long
        get() {
            val parts = tokenId.split("-")
            return if (parts.size > 1) parts[1].toLong() else MAINNET_ID
        }

    fun getTokenID(): BigInteger {
        val parts = tokenId.split("-")
        return if (parts.size > 2) {
            try {
                BigInteger(parts[2])
            } catch (_: NumberFormatException) {
                BigInteger.ZERO
            }
        } else {
            BigInteger.ZERO
        }
    }

    private fun calculateTokenNameWeight(
        chainId: Long,
        tokenAddress: String,
        svs: AssetDefinitionService?,
        tokenName: String,
        symbol: String,
        isEth: Boolean,
        group: TokenGroup,
        attnId: Int,
    ): Long {
        var weight = 1000L
        var name = svs?.getTokenName(chainId, tokenAddress, 1)
        name = if (name != null) name + symbol else tokenName + symbol

        val overrideToken = EthereumNetworkRepository.getOverrideTokenCompat()
        if (chainId == overrideToken.chainId && tokenAddress.equals(overrideToken.address, ignoreCase = true)) {
            return CHAIN_ITEM_WEIGHT + 1
        } else if (isEth) {
            return CHAIN_ITEM_WEIGHT + 1 + EthereumNetworkBase.getChainOrdinal(chainId)
        }

        if (name.isNullOrEmpty()) {
            val base = Long.MAX_VALUE - kotlin.math.abs(tokenAddress.hashCode().toLong())
            return if (hasPositiveBalance()) base else base / 2
        }

        var i = 4
        var pos = 0
        while (i >= 0 && pos < name.length) {
            val c = name[pos++]
            val weightComponent = tokeniseCharacter(c)
            if (weightComponent > 0) {
                val component = (Math.pow(26.0, i.toDouble()) * weightComponent).toInt()
                weight += component.toLong()
                i--
            }
        }

        weight += (tokenAddress.hashCode() % 1753).toLong()
        if (weight < 2) weight = 2

        if (group == TokenGroup.ATTESTATION) {
            weight += (attnId + 1)
        }

        return weight
    }

    private fun tokeniseCharacter(c: Char): Int {
        var weight = Character.toLowerCase(c) - 'a' + 1
        weight = when {
            weight > 'z'.code -> weight % 10
            weight < 0 -> 1 + (c.code - '0'.code)
            else -> weight + 10
        }
        return weight
    }

    override fun compareTo(other: TokenCardMeta): Int {
        var result = nameWeight - other.nameWeight
        if (result < Int.MIN_VALUE) result = Int.MIN_VALUE.toLong()
        if (result > Int.MAX_VALUE) result = Int.MAX_VALUE.toLong()
        return result.toInt()
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(tokenId)
        dest.writeLong(lastUpdate)
        dest.writeLong(lastTxUpdate)
        dest.writeLong(nameWeight)
        dest.writeInt(type.ordinal)
        dest.writeString(balance)
        dest.writeString(filterText)
        dest.writeInt(group.ordinal)
    }

    companion object CREATOR : Parcelable.Creator<TokenCardMeta> {
        override fun createFromParcel(parcel: Parcel): TokenCardMeta = TokenCardMeta(parcel)
        override fun newArray(size: Int): Array<TokenCardMeta?> = arrayOfNulls(size)

        private fun buildTokenId(
            chainId: Long,
            tokenAddress: String,
            group: TokenGroup,
            attnId: String,
        ): String {
            val base = TokensRealmSource.databaseKey(chainId, tokenAddress)
            val attnPart = if (!TextUtils.isEmpty(attnId)) "-$attnId" else ""
            val suffix = if (group == TokenGroup.ATTESTATION) Attestation.ATTESTATION_SUFFIX else ""
            return base + attnPart + suffix
        }

        fun groupWeight(group: TokenGroup): Long = group.ordinal + 1L
    }
}
