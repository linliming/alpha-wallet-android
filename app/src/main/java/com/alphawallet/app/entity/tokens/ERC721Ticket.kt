package com.alphawallet.app.entity.tokens

import com.alphawallet.app.R
import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.TicketRangeElement
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.TransactionInput
import com.alphawallet.app.entity.nftassets.NFTAsset
import com.alphawallet.app.entity.tokendata.TokenGroup
import com.alphawallet.app.repository.EventResult
import com.alphawallet.app.repository.entity.RealmToken
import com.alphawallet.app.util.Utils
import com.alphawallet.token.entity.TicketRange
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint8
import java.math.BigDecimal
import java.math.BigInteger

/**
 * ERC-721 Ticket token representation.
 * Stores owned token IDs (optionally as hex string) and exposes
 * helper methods used by the legacy ticket features.
 */
class ERC721Ticket(
    tokenInfo: TokenInfo,
    balances: List<BigInteger>,
    blancaTime: Long,
    networkName: String,
    type: ContractType
) : Token(tokenInfo, BigDecimal.ZERO, blancaTime, networkName, type) {

    private val balanceArray: MutableList<BigInteger> = balances.toMutableList()

    constructor(
        tokenInfo: TokenInfo,
        balances: String,
        blancaTime: Long,
        networkName: String,
        type: ContractType
    ) : this(
        tokenInfo,
        parseHexIdList(balances),
        blancaTime,
        networkName,
        type
    )

    init {
        group = TokenGroup.NFT
    }

    override fun getStringBalanceForUI(decimalPlaces: Int): String = getTokenCount().toString()

    override fun hasPositiveBalance(): Boolean = getTokenCount() > 0

    override fun getFullBalance(): String {
        return if (balanceArray.isEmpty()) {
            "no tokens"
        } else {
            Utils.bigIntListToString(balanceArray, true)
        }
    }

    override fun getTokenAssets(): Map<BigInteger, NFTAsset> {
        val assets = HashMap<BigInteger, NFTAsset>()
        for (tokenId in balanceArray) {
            assets[tokenId] = NFTAsset(tokenId)
        }
        return assets
    }

    override fun pruneIDList(idListStr: String, quantity: Int): List<BigInteger> {
        val idList = parseHexIdList(idListStr)
        if (quantity >= idList.size) return idList
        return idList.subList(0, quantity)
    }

    override fun getTokenCount(): Int {
        var count = 0
        for (id in balanceArray) {
            if (id != BigInteger.ZERO) count++
        }
        return count
    }

    override fun setRealmBalance(realmToken: RealmToken) {
        realmToken.balance = Utils.bigIntListToString(balanceArray, true)
    }

    override fun getContractType(): Int = R.string.ERC721T

    fun getPassToFunction(
        expiry: BigInteger,
        tokenIds: List<BigInteger>,
        v: Int,
        r: ByteArray,
        s: ByteArray,
        recipient: String
    ): Function {
        return Function(
            "passTo",
            listOf(
                Uint256(expiry),
                getDynArray(tokenIds),
                Uint8(v.toLong()),
                Bytes32(r),
                Bytes32(s),
                Address(recipient)
            ),
            emptyList()
        )
    }

    @Throws(NumberFormatException::class)
    override fun getTransferFunction(to: String, transferData: List<BigInteger>): Function {
        if (transferData.size > 1) {
            throw NumberFormatException("ERC721Ticket currently doesn't handle batch transfers.")
        }

        return Function(
            "safeTransferFrom",
            listOf(
                Address(getWallet()),
                Address(to),
                Uint256(transferData[0])
            ),
            emptyList()
        )
    }

    override fun contractTypeValid(): Boolean = true

    override fun hasArrayBalance(): Boolean = true

    override fun getArrayBalance(): List<BigInteger> = balanceArray

    override fun getNonZeroArrayBalance(): List<BigInteger> {
        val nonZeroValues = ArrayList<BigInteger>()
        for (value in balanceArray) {
            if (value != BigInteger.ZERO) nonZeroValues.add(value)
        }
        return nonZeroValues
    }

    override fun convertValue(prefix: String, vResult: EventResult, precision: Int): String {
        val value = vResult.value ?: ""
        return if (value.length > precision + 1) {
            prefix + "1"
        } else {
            "#$value"
        }
    }

    override fun getIsSent(transaction: Transaction): Boolean = transaction.isNFTSent(getWallet())

    override fun isERC721Ticket(): Boolean = true

    override fun isNonFungible(): Boolean = true

    override fun groupWithToken(
        currentGroupingRange: TicketRange,
        newElement: TicketRangeElement,
        currentGroupTime: Long
    ): Boolean = false

    override fun addAssetToTokenBalanceAssets(tokenId: BigInteger, asset: NFTAsset) {
        balanceArray.add(tokenId)
    }

    override fun getTransferValue(txInput: TransactionInput, transactionBalancePrecision: Int): String {
        return getTransferValueRaw(txInput).toString()
    }

    override fun getTransferValueRaw(txInput: TransactionInput): BigInteger {
        return if (txInput.arrayValues.size > 1) {
            BigInteger.valueOf(txInput.arrayValues.size.toLong())
        } else {
            BigInteger.ONE
        }
    }

    override fun getBalanceRaw(): BigDecimal = BigDecimal(getArrayBalance().size)

    override fun getStandardFunctions(): List<Int> = listOf(R.string.action_use, R.string.action_transfer)

    companion object {
        private fun parseHexIdList(integerString: String): List<BigInteger> {
            val result = mutableListOf<BigInteger>()
            if (integerString.isBlank()) return result

            val entries = integerString.split(",")
            for (entry in entries) {
                val trimmed = entry.trim()
                if (trimmed.isEmpty()) continue
                try {
                    val value = if (trimmed.startsWith("0x", ignoreCase = true)) {
                        BigInteger(trimmed.substring(2), 16)
                    } else {
                        BigInteger(trimmed, 16)
                    }
                    result.add(value)
                } catch (_: NumberFormatException) {
                    // ignore malformed entries
                }
            }
            return result
        }
    }
}
