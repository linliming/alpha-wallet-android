package com.alphawallet.app.entity

import android.content.Context
import android.text.TextUtils
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.ui.widget.entity.ENSHandler
import com.alphawallet.app.ui.widget.entity.StatusType
import com.alphawallet.app.util.BalanceUtils
import com.alphawallet.token.tools.ParseMagicLink
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Container describing the decoded arguments for a transaction input payload.
 *
 * The address of the contract, function name, and sender signature are captured elsewhere; this
 * class focuses solely on arguments parsed via [TransactionDecoder].
 */
class TransactionInput {
    @JvmField
    var functionData: FunctionData = FunctionData("Contract Call", ContractType.OTHER)

    @JvmField
    val addresses: MutableList<String> = ArrayList()

    @JvmField
    val arrayValues: MutableList<BigInteger> = ArrayList()

    @JvmField
    val sigData: MutableList<String> = ArrayList()

    @JvmField
    val miscData: MutableList<String> = ArrayList()

    @JvmField
    val hexArgs: MutableList<String> = ArrayList()

    @JvmField
    var tradeAddress: String = ""

    @JvmField
    var type: TransactionType = TransactionType.CONTRACT_CALL

    /**
     * Returns true when any of the decoded address arguments matches [address].
     */
    fun containsAddress(address: String): Boolean {
        val needle = Numeric.cleanHexPrefix(address)
        for (candidate in addresses) {
            if (candidate.contains(needle)) {
                return true
            }
        }
        return false
    }

    /**
     * Returns the first address argument decoded for this transaction (or an empty string).
     */
    fun getFirstAddress(): String = addresses.firstOrNull().orEmpty()

    /**
     * Returns the destination address associated with the transaction.
     *
     * The second address is chosen for transfer-style functions where the first argument is the
     * sender.
     */
    fun getDestinationAddress(): String {
        if (addresses.isEmpty()) return ""
        var address = getFirstAddress()
        when (functionData.functionName) {
            "transferFrom",
            "safeTransferFrom",
            "safeBatchTransferFrom" -> if (addresses.size > 1) {
                address = addresses[1]
            }
        }
        return address
    }

    /**
     * Returns the address argument at [index], or an empty string when out of bounds.
     */
    fun getAddress(index: Int): String = addresses.getOrNull(index).orEmpty()

    private fun getFirstValue(): String {
        if (miscData.isNotEmpty()) {
            val firstVal = miscData[0]
            return if (firstVal == ERC20_APPROVE_ALL) {
                "All"
            } else {
                BigInteger(firstVal, HEX_RADIX).toString(DECIMAL_RADIX)
            }
        }
        return "0"
    }

    private fun getFirstValueScaled(ctx: Context?, tokenDecimal: Int): String {
        if (miscData.isNotEmpty()) {
            val firstVal = miscData[0]
            val bi = BigInteger(firstVal, HEX_RADIX)
            val scaledVal = BigDecimal(bi).divide(
                BigDecimal.valueOf(Math.pow(10.0, tokenDecimal.toDouble())),
                SCALE_DECIMALS,
                RoundingMode.DOWN,
            )
            if (firstVal == ERC20_APPROVE_ALL || scaledVal.compareTo(ERC20_APPROVE_ALL_BD) >= 0) {
                return ctx?.getString(R.string.all) ?: "All"
            }
            return BalanceUtils.getScaledValueMinimal(BigDecimal(bi), tokenDecimal.toLong(), 4)
        }
        return "0"
    }

    /**
     * Looks up the human-readable title for the operation type.
     */
    fun getOperationTitle(ctx: Context): String = ctx.getString(TransactionLookup.typeToName(type))

    /**
     * Determines which string resource should be used when forming a "to"/"from" label.
     */
    fun getOperationToFrom(): Int = when (type) {
        TransactionType.MAGICLINK_SALE,
        TransactionType.MAGICLINK_TRANSFER,
        TransactionType.TRANSFER_TO,
        TransactionType.SEND -> R.string.to

        TransactionType.RECEIVED,
        TransactionType.RECEIVE_FROM,
        TransactionType.MAGICLINK_PURCHASE,
        TransactionType.MAGICLINK_PICKUP -> R.string.from_op

        TransactionType.APPROVE -> R.string.approve
        else -> 0
    }

    /**
     * Builds a context-aware description for the operation (eg. "Transfer to Alice").
     */
    fun getOperationDescription(
        ctx: Context,
        tx: Transaction,
        token: Token,
        tokensService: TokensService,
    ): String {
        return when (type) {
            TransactionType.APPROVE -> {
                val amount = getOperationValue(token, tx)
                val approveAddr = run {
                    val addr = getFirstAddress()
                    val approveToken = tokensService.getToken(tx.chainId, addr)
                    approveToken?.getShortName() ?: ENSHandler.matchENSOrFormat(ctx, addr)
                }
                ctx.getString(R.string.default_approve, amount, approveAddr)
            }

            TransactionType.TERMINATE_CONTRACT -> ENSHandler.matchENSOrFormat(ctx, tx.to)
            TransactionType.CONSTRUCTOR -> token.getFullName().toString()
            else -> {
                val operationResId = getOperationToFrom()
                if (operationResId != 0) {
                    ctx.getString(
                        R.string.operation_definition,
                        ctx.getString(operationResId),
                        ENSHandler.matchENSOrFormat(ctx, getOperationAddress(tx, token)),
                    )
                } else {
                    ENSHandler.matchENSOrFormat(ctx, tx.to)
                }
            }
        }
    }

    /**
     * Identifies the counter-party address (or descriptive string) relevant to the operation.
     */
    fun getOperationAddress(tx: Transaction, token: Token): String {
        return when (type) {
            TransactionType.MAGICLINK_TRANSFER,
            TransactionType.MAGICLINK_SALE,
            TransactionType.PASS_FROM,
            TransactionType.RECEIVED,
            TransactionType.TRANSFER_FROM,
            TransactionType.SAFE_TRANSFER -> tx.from

            TransactionType.MAGICLINK_PURCHASE,
            TransactionType.MAGICLINK_PICKUP -> tradeAddress

            TransactionType.PASS_TO,
            TransactionType.SEND,
            TransactionType.ALLOCATE_TO,
            TransactionType.APPROVE -> getDestinationAddress()

            TransactionType.REDEEM,
            TransactionType.ADMIN_REDEEM -> C.BURN_ADDRESS

            TransactionType.SAFE_BATCH_TRANSFER -> if (tx.to.equals(token.getWallet(), ignoreCase = true)) {
                tx.from
            } else {
                tx.to
            }

            TransactionType.BURN,
            TransactionType.MINT -> token.getFullName().toString()

            TransactionType.LOAD_NEW_TOKENS,
            TransactionType.CONSTRUCTOR,
            TransactionType.TERMINATE_CONTRACT -> token.getAddress()

            TransactionType.UNKNOWN_FUNCTION,
            TransactionType.INVALID_OPERATION,
            TransactionType.CONTRACT_CALL,
            TransactionType.TOKEN_SWAP,
            TransactionType.WITHDRAW,
            TransactionType.DEPOSIT,
            TransactionType.REMIX,
            TransactionType.COMMIT_NFT,
            TransactionType.SEND_ETH -> tx.to

            TransactionType.TRANSFER_TO -> getDestinationAddress()
            TransactionType.RECEIVE_FROM,
            TransactionType.MAGICLINK_SALE -> tx.from

            TransactionType.UNKNOWN -> TODO()
            TransactionType.ILLEGAL_VALUE -> TODO()
        }
    }

    private fun getMagicLinkAddress(tx: Transaction): String {
        var address = tx.from
        if (tx.error != "0") return address
        try {
            val sig = Transaction.decoder.getSignatureData(this)
            val ticketIndexArray = Transaction.decoder.getIndices(this)
            val expiryStr = miscData[0]
            val expiry = expiryStr.toLong(HEX_RADIX)
            val priceWei = BigInteger(tx.value)
            val contractAddress = tx.to

            val parser = ParseMagicLink(CryptoFunctions(), EthereumNetworkRepository.extraChainsCompat())
            val tradeBytes = parser.getTradeBytes(ticketIndexArray, contractAddress, priceWei, expiry)
            val key = Sign.signedMessageToKey(tradeBytes, sig)
            address = Numeric.prependHexPrefix(Keys.getAddress(key))
        } catch (e: Exception) {
            address = tx.from
        }
        return address
    }

    /**
     * Deduces the operation type for the supplied transaction and wallet context.
     */
    fun setOperationType(tx: Transaction?, walletAddress: String?) {
        if (tx != null && (tx.isConstructor || (tx.input != null && tx.input == Transaction.CONSTRUCTOR))) {
            type = TransactionType.CONSTRUCTOR
            return
        }

        if (tx != null && tx.input != null && tx.input == "0x") {
            type = TransactionType.SEND_ETH
            return
        }

        type = when (functionData.functionName) {
            "trade" -> interpretTradeData(tx, walletAddress)
            "safeTransferFrom" -> interpretSafeTransferFrom(walletAddress)
            "safeBatchTransferFrom" -> TransactionType.SAFE_BATCH_TRANSFER
            "transferFrom" -> interpretTransferFrom(walletAddress)
            "transfer" -> interpretTransfer(walletAddress)
            "allocateTo" -> TransactionType.ALLOCATE_TO
            "approve" -> TransactionType.APPROVE
            "loadNewTickets" -> TransactionType.LOAD_NEW_TOKENS
            "passTo" -> interpretPassTo(tx, walletAddress)
            "endContract",
            "selfdestruct",
            "kill" -> TransactionType.TERMINATE_CONTRACT

            "swapExactTokensForTokens" -> TransactionType.TOKEN_SWAP
            "withdraw" -> TransactionType.WITHDRAW
            "deposit" -> TransactionType.DEPOSIT
            "remix" -> TransactionType.REMIX
            "mint" -> TransactionType.MINT
            "burn" -> TransactionType.BURN
            "commitNFT" -> TransactionType.COMMIT_NFT
            else -> TransactionType.CONTRACT_CALL
        }
    }

    private fun interpretTradeData(tx: Transaction?, walletAddr: String?): TransactionType {
        if (tx == null) {
            return TransactionType.MAGICLINK_TRANSFER
        }

        val priceWei = BigInteger(tx.value)
        tradeAddress = getMagicLinkAddress(tx)
        return if (priceWei == BigInteger.ZERO) {
            if (tradeAddress.equals(walletAddr, ignoreCase = true)) {
                TransactionType.MAGICLINK_TRANSFER
            } else {
                TransactionType.MAGICLINK_PICKUP
            }
        } else {
            if (tradeAddress.equals(walletAddr, ignoreCase = true)) {
                TransactionType.MAGICLINK_SALE
            } else {
                TransactionType.MAGICLINK_PURCHASE
            }
        }
    }

    private fun interpretPassTo(tx: Transaction?, walletAddr: String?): TransactionType {
        if (tx == null) {
            return TransactionType.PASS_TO
        }

        tradeAddress = getMagicLinkAddress(tx)
        return if (tradeAddress.equals(walletAddr, ignoreCase = true)) {
            TransactionType.PASS_FROM
        } else {
            TransactionType.PASS_TO
        }
    }

    private fun interpretSafeTransferFrom(walletAddr: String?): TransactionType {
        val destinationAddr = getDestinationAddress()
        val fromAddr = getFirstAddress()
        return when {
            walletAddr == null -> TransactionType.SAFE_TRANSFER
            destinationAddr == C.BURN_ADDRESS -> TransactionType.BURN
            fromAddr == C.BURN_ADDRESS -> TransactionType.MINT
            !destinationAddr.equals(walletAddr, ignoreCase = true) -> TransactionType.SAFE_TRANSFER
            else -> TransactionType.TRANSFER_TO
        }
    }

    private fun interpretTransferFrom(walletAddr: String?): TransactionType {
        val destinationAddr = getDestinationAddress()
        return when {
            walletAddr == null -> TransactionType.TRANSFER_FROM
            destinationAddr == C.BURN_ADDRESS -> TransactionType.REDEEM
            !destinationAddr.equals(walletAddr, ignoreCase = true) -> TransactionType.TRANSFER_FROM
            else -> TransactionType.TRANSFER_TO
        }
    }

    private fun interpretTransfer(walletAddr: String?): TransactionType {
        return when {
            walletAddr == null -> TransactionType.TRANSFER_TO
            getDestinationAddress().equals(walletAddr, ignoreCase = true) -> TransactionType.RECEIVED
            else -> TransactionType.SEND
        }
    }

    /**
     * Provides contextual suffix text for certain operations (magic link sales/purchases).
     */
    fun getSupplimentalInfo(tx: Transaction, walletAddress: String, networkName: String): String {
        return when (type) {
            TransactionType.MAGICLINK_SALE -> "(+" + BalanceUtils.getScaledValue(tx.value, C.ETHER_DECIMALS.toLong()) + " $networkName)"
            TransactionType.MAGICLINK_PURCHASE -> "(-" + BalanceUtils.getScaledValue(tx.value, C.ETHER_DECIMALS.toLong()) + " $networkName)"
            else -> ""
        }
    }

    /**
     * Returns the human-readable value associated with the operation (amount, count, etc.).
     */
    fun getOperationValue(token: Token, tx: Transaction): String {
        var operationValue = ""
        var addSymbol = true
        when (type) {
            TransactionType.PASS_TO,
            TransactionType.PASS_FROM,
            TransactionType.REDEEM,
            TransactionType.ADMIN_REDEEM,
            TransactionType.MAGICLINK_TRANSFER,
            TransactionType.MAGICLINK_SALE,
            TransactionType.MAGICLINK_PURCHASE,
            TransactionType.MAGICLINK_PICKUP -> operationValue = arrayValues.size.toString()

            TransactionType.APPROVE,
            TransactionType.ALLOCATE_TO -> {
                operationValue = getApproveValue(token)
                if (token.isNonFungible()) addSymbol = false
            }

            TransactionType.TRANSFER_TO,
            TransactionType.RECEIVE_FROM,
            TransactionType.TRANSFER_FROM,
            TransactionType.RECEIVED,
            TransactionType.SEND,
            TransactionType.BURN,
            TransactionType.MINT -> operationValue = tx.transactionInput?.let {
                token.getTransferValue(it, TRANSACTION_BALANCE_PRECISION)
            } ?: ""

            TransactionType.REMIX,
            TransactionType.COMMIT_NFT -> {
                operationValue = ""
                addSymbol = false
            }

            TransactionType.LOAD_NEW_TOKENS -> operationValue = arrayValues.size.toString()

            TransactionType.CONSTRUCTOR,
            TransactionType.TERMINATE_CONTRACT -> Unit

            TransactionType.CONTRACT_CALL,
            TransactionType.TOKEN_SWAP,
            TransactionType.WITHDRAW -> addSymbol = false

            TransactionType.DEPOSIT -> {
                operationValue = token.getTransactionValue(tx, TRANSACTION_BALANCE_PRECISION)
                addSymbol = true
            }

            TransactionType.UNKNOWN_FUNCTION,
            TransactionType.INVALID_OPERATION,
            TransactionType.SAFE_TRANSFER,
            TransactionType.SAFE_BATCH_TRANSFER,
            TransactionType.SEND_ETH -> Unit

            TransactionType.UNKNOWN -> TODO()
            TransactionType.ILLEGAL_VALUE -> TODO()
        }

        if (addSymbol) {
            operationValue = if (!token.isEthereum()) {
                if (operationValue.isNotEmpty()) "$operationValue ${token.getShortSymbol()}" else ""
            } else if (!TextUtils.isEmpty(operationValue)) {
                "$operationValue ${token.getShortSymbol()}"
            } else {
                ""
            }
        }

        return operationValue
    }

    private fun getApproveValue(token: Token): String {
        return if (token.isNonFungible() && miscData.isNotEmpty()) {
            val bi = BigInteger(miscData[0], HEX_RADIX)
            "#${bi.toString(DECIMAL_RADIX)}"
        } else {
            getFirstValueScaled(null, token.tokenInfo.decimals)
        }
    }

    /**
     * Indicates whether the operation display should append the token symbol.
     */
    fun shouldShowSymbol(token: Token): Boolean = when (type) {
        TransactionType.CONTRACT_CALL,
        TransactionType.TOKEN_SWAP,
        TransactionType.WITHDRAW -> false
        else -> !token.isEthereum()
    }

    /**
     * Returns the UI status state associated with the operation (sent, received, etc.).
     */
    fun getOperationImage(tx: Transaction, walletAddress: String): StatusType {
        return when (type) {
            TransactionType.PASS_TO,
            TransactionType.SEND,
            TransactionType.REDEEM,
            TransactionType.ADMIN_REDEEM,
            TransactionType.MAGICLINK_TRANSFER,
            TransactionType.MAGICLINK_SALE,
            TransactionType.TRANSFER_FROM,
            TransactionType.APPROVE,
            TransactionType.ALLOCATE_TO,
            TransactionType.BURN -> StatusType.SENT

            TransactionType.PASS_FROM,
            TransactionType.MAGICLINK_PURCHASE,
            TransactionType.MAGICLINK_PICKUP,
            TransactionType.TRANSFER_TO,
            TransactionType.RECEIVE_FROM,
            TransactionType.RECEIVED,
            TransactionType.MINT -> StatusType.RECEIVE

            TransactionType.REMIX -> StatusType.NONE

            TransactionType.LOAD_NEW_TOKENS,
            TransactionType.CONSTRUCTOR,
            TransactionType.TERMINATE_CONTRACT -> StatusType.CONSTRUCTOR

            else -> if (tx.from.equals(walletAddress, ignoreCase = true)) {
                if (tx.to.equals(tx.from, ignoreCase = true)) StatusType.SELF else StatusType.SENT
            } else {
                StatusType.RECEIVE
            }
        }
    }

    /**
     * Retrieves a raw numeric value for the operation (used for sorting or analytics).
     */
    fun getRawValue(): BigDecimal {
        return if (arrayValues.isNotEmpty()) {
            BigDecimal(arrayValues.size)
        } else {
            BigDecimal(getFirstValue())
        }
    }

    /**
     * Returns the event keyword describing this operation for analytics.
     */
    fun getOperationEvent(walletAddress: String): String {
        return when (type) {
            TransactionType.SEND,
            TransactionType.TRANSFER_FROM,
            TransactionType.BURN -> "sent"

            TransactionType.TRANSFER_TO,
            TransactionType.RECEIVE_FROM,
            TransactionType.MINT,
            TransactionType.RECEIVED -> "received"

            TransactionType.APPROVE -> "ownerApproved"

            else -> if (getDestinationAddress().equals(walletAddress, ignoreCase = true)) {
                "received"
            } else {
                "sent"
            }
        }
    }

    /**
     * Indicates whether this operation represents value leaving the wallet.
     */
    fun isSent(): Boolean {
        return when (type) {
            TransactionType.SEND,
            TransactionType.PASS_TO,
            TransactionType.REDEEM,
            TransactionType.ADMIN_REDEEM,
            TransactionType.MAGICLINK_TRANSFER,
            TransactionType.MAGICLINK_SALE,
            TransactionType.RECEIVE_FROM,
            TransactionType.TRANSFER_FROM,
            TransactionType.BURN,
            TransactionType.APPROVE,
            TransactionType.ALLOCATE_TO -> true

            TransactionType.PASS_FROM,
            TransactionType.MAGICLINK_PURCHASE,
            TransactionType.MAGICLINK_PICKUP,
            TransactionType.TRANSFER_TO,
            TransactionType.MINT,
            TransactionType.RECEIVED -> false

            else -> true
        }
    }

    /**
     * Returns true when the operation is a simple send/receive style transfer.
     */
    fun isSendOrReceive(tx: Transaction): Boolean {
        return when (type) {
            TransactionType.SEND,
            TransactionType.PASS_TO,
            TransactionType.PASS_FROM,
            TransactionType.REDEEM,
            TransactionType.ADMIN_REDEEM,
            TransactionType.MAGICLINK_TRANSFER,
            TransactionType.MAGICLINK_SALE,
            TransactionType.MAGICLINK_PURCHASE,
            TransactionType.MAGICLINK_PICKUP,
            TransactionType.TRANSFER_TO,
            TransactionType.RECEIVE_FROM,
            TransactionType.MINT,
            TransactionType.BURN,
            TransactionType.TRANSFER_FROM,
            TransactionType.RECEIVED,
            TransactionType.REMIX -> true
            else -> tx.value != "0"
        }
    }

    /**
     * Builds a representation of the function call using the decoded argument list.
     */
    fun buildFunctionCallText(): String {
        val args = hexArgs.joinToString(", ") { arg ->
            if (arg.startsWith("0")) truncateValue(arg) else arg
        }
        return "${functionData.functionName}($args)"
    }

    private fun truncateValue(arg: String): String {
        return try {
            BigInteger(arg, HEX_RADIX).toString(HEX_RADIX)
        } catch (_: Exception) {
            arg
        }
    }

    companion object {
        private const val HEX_RADIX = 16
        private const val DECIMAL_RADIX = 10
        private const val SCALE_DECIMALS = 18

        private const val ERC20_APPROVE_ALL =
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

        private val ERC20_APPROVE_ALL_BD = BigDecimal.valueOf(9.999).movePointRight(17)

        private val TRANSACTION_BALANCE_PRECISION =
            com.alphawallet.app.ui.widget.holder.TransactionHolder.TRANSACTION_BALANCE_PRECISION
    }
}
