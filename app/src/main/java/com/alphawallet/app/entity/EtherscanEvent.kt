package com.alphawallet.app.entity

import android.text.TextUtils
import com.alphawallet.app.repository.TokenRepository
import org.web3j.utils.Numeric
import java.math.BigInteger

/**
 * Models a single transfer event returned by the Etherscan API and provides helpers to turn it into wallet transactions.
 */
class EtherscanEvent {
    var blockNumber: String? = null
    var timeStamp: Long = 0
    var hash: String? = null
    var nonce: Int = 0
    var blockHash: String? = null
    var from: String? = null
    var contractAddress: String? = null
    var to: String? = null
    var tokenID: String? = null
    var value: String? = null
    var tokenName: String? = null
    var tokenValue: String? = null
    var tokenSymbol: String? = null
    var tokenDecimal: String? = null
    var input: String? = null
    var tokenIDs: Array<String>? = null
    var values: Array<String>? = null
    var gas: String? = null
    var gasPrice: String? = null
    var gasUsed: String? = null

    /**
     * Builds a fungible transfer [Transaction] for this event, defaulting to zero when the value is missing.
     */
    fun createTransaction(networkInfo: NetworkInfo): Transaction {
        var valueBI = BigInteger.ZERO
        val rawValue = value
        if (!rawValue.isNullOrEmpty() && rawValue.first().isDigit()) {
            valueBI = BigInteger(rawValue)
        }

        val encodedInput = Numeric.toHexString(
            TokenRepository.createTokenTransferData(to.orEmpty(), valueBI)
        )

        return Transaction(
            hash.orEmpty(),
            "0",
            blockNumber.orEmpty(),
            timeStamp,
            nonce,
            from.orEmpty(),
            contractAddress.orEmpty(),
            "0",
            gas.orEmpty(),
            gasPrice.orEmpty(),
            encodedInput,
            gasUsed.orEmpty(),
            networkInfo.chainId,
            false,
        )
    }

    /**
     * Builds an ERC-721 transfer [Transaction], defaulting the tokenId to one when it cannot be parsed.
     */
    fun createNFTTransaction(networkInfo: NetworkInfo): Transaction {
        val tokenId = try {
            BigInteger(tokenID)
        } catch (_: Exception) {
            BigInteger.ONE
        }

        val encodedInput = Numeric.toHexString(
            TokenRepository.createERC721TransferFunction(from.orEmpty(), to.orEmpty(), contractAddress.orEmpty(), tokenId)
        )

        return Transaction(
            hash.orEmpty(),
            "0",
            blockNumber.orEmpty(),
            timeStamp,
            nonce,
            from.orEmpty(),
            contractAddress.orEmpty(),
            "0",
            gas.orEmpty(),
            gasPrice.orEmpty(),
            encodedInput,
            gasUsed.orEmpty(),
            networkInfo.chainId,
            false,
        )
    }

    /**
     * Determines whether any event in this contract batch carries ERC-1155 style metadata.
     */
    fun isERC1155(contractEventList: List<EtherscanEvent>): Boolean {
        for (event in contractEventList) {
            if (!TextUtils.isEmpty(event.tokenValue) ||
                (event.tokenIDs != null && event.tokenIDs?.isNotEmpty() == true)
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Ensures `tokenID` is populated using the first entry from `tokenIDs` when the primary field is empty.
     */
    fun patchFirstTokenID() {
        if (tokenID == null) {
            val ids = tokenIDs
            if (ids != null && ids.isNotEmpty()) {
                tokenID = ids[0]
            }
        }
    }
}

