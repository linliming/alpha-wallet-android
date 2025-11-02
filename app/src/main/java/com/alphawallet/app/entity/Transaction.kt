package com.alphawallet.app.entity

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import com.alphawallet.app.R
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.repository.EventResult
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.ui.widget.entity.ENSHandler
import com.alphawallet.app.ui.widget.entity.StatusType
import com.alphawallet.app.util.Utils
import com.alphawallet.app.web3.entity.Web3Transaction
import com.alphawallet.token.tools.ParseMagicLink
import com.google.gson.annotations.SerializedName
import org.web3j.crypto.Hash
import org.web3j.rlp.RlpEncoder
import org.web3j.rlp.RlpList
import org.web3j.rlp.RlpString
import org.web3j.utils.Numeric
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Arrays

/**
 * Represents an Ethereum transaction through its full lifecycle (drafted, signed, broadcast, mined).
 */
class Transaction() : Parcelable {

    @SerializedName("id")
    var hash: String? = null
    var blockNumber: String? = null
    var timeStamp: Long = 0
    var nonce: Int = 0
    var from: String = ""
    var to: String = ""
    var value: String = ""
    var gas: String? = null
    var gasPrice: String = ""
    var gasUsed: String? = null
    var maxFeePerGas: String? = null
    var maxPriorityFee: String? = null
    var input: String = ""
    var error: String? = null
    var chainId: Long = 0
    var functionName: String = ""
    var isConstructor: Boolean = false
    var transactionInput: TransactionInput? = null

    companion object {
        const val CONSTRUCTOR = "Constructor"
        val decoder = TransactionDecoder()

        @JvmStatic
        var parser: ParseMagicLink? = null

        @JvmField
        val CREATOR: Parcelable.Creator<Transaction> = object : Parcelable.Creator<Transaction> {
            override fun createFromParcel(source: Parcel): Transaction = Transaction(source)
            override fun newArray(size: Int): Array<Transaction?> = arrayOfNulls(size)
        }
    }

    /**
     * Indicates whether the transaction is pending inclusion in a block.
     */
    fun isPending(): Boolean =
        blockNumber.isNullOrEmpty() || blockNumber == "0" || blockNumber == "-2"

    /**
     * Returns true when the transaction has an on-chain error flag.
     */
    fun hasError(): Boolean = error == "1"

    /**
     * Returns true when the transaction contains calldata beyond the standard prefix.
     */
    fun hasData(): Boolean = input?.length ?: 0 > 2

    /**
     * Constructs a simple transaction record.
     */
    constructor(
        hash: String?,
        error: String?,
        blockNumber: String?,
        timeStamp: Long,
        nonce: Int,
        from: String,
        to: String,
        value: String,
        gas: String?,
        gasPrice: String,
        input: String,
        gasUsed: String?,
        chainId: Long,
        isConstructor: Boolean,
    ) : this() {
        this.hash = hash
        this.error = error
        this.blockNumber = blockNumber
        this.timeStamp = timeStamp
        this.nonce = nonce
        this.from = from
        this.to = to
        this.value = value
        this.gas = gas
        this.gasPrice = gasPrice
        this.input = input
        this.gasUsed = gasUsed
        this.chainId = chainId
        this.isConstructor = isConstructor
    }

    /**
     * Builds a transaction from a [Web3Transaction] draft.
     */
    constructor(tx: Web3Transaction, chainId: Long, wallet: String) : this() {
        hash = ""
        error = ""
        blockNumber = ""
        timeStamp = System.currentTimeMillis() / 1000
        nonce = -1
        from = wallet
        to = tx.recipient.toString()
        value = tx.value.toString()
        gas = tx.gasLimit.toString()
        gasPrice = tx.gasPrice.toString()
        input = tx.payload.orEmpty()
        gasUsed = tx.gasLimit.toString()
        this.chainId = chainId
        isConstructor = tx.isConstructor
        maxFeePerGas = tx.maxFeePerGas.toString()
        maxPriorityFee = tx.maxPriorityFeePerGas.toString()
    }

    /**
     * Builds a transaction from Covalent data.
     */
    constructor(cTx: CovalentTransaction, chainId: Long, transactionTime: Long) : this() {
        if (cTx.to_address == null || cTx.to_address == "null") {
            isConstructor = true
            input = CONSTRUCTOR
            to = cTx.determineContractAddress()
        } else {
            to = cTx.to_address ?: ""
            input = "0x"
        }

        hash = cTx.tx_hash ?: ""
        blockNumber = cTx.block_height ?: ""
        timeStamp = transactionTime
        error = if (cTx.successful) "0" else "1"
        nonce = 0
        from = cTx.from_address ?: ""
        value = cTx.value ?: ""
        gas = cTx.gas_offered.toString()
        gasPrice = cTx.gas_price ?: ""
        gasUsed = cTx.gas_spent ?: ""
        this.chainId = chainId
    }

    /**
     * Builds a transaction from an on-chain transaction response.
     */
    constructor(
        ethTx: org.web3j.protocol.core.methods.response.Transaction,
        chainId: Long,
        isSuccess: Boolean,
        timeStamp: Long,
    ) : this() {
        val contractAddress = ethTx.creates ?: ""
        val nonceValue =
            ethTx.nonceRaw?.let { Numeric.toBigInt(it).intValueExact() } ?: 0

        if (!contractAddress.isNullOrEmpty()) {
            to = contractAddress
            isConstructor = true
            input = CONSTRUCTOR
        } else if (ethTx.to == null && ethTx.input != null && ethTx.input.startsWith("0x60")) {
            input = CONSTRUCTOR
            isConstructor = true
            to = calculateContractAddress(ethTx.from ?: "", nonceValue.toLong())
        } else {
            to = ethTx.to ?: ""
            input = ethTx.input ?: ""
        }

        hash = ethTx.hash ?: ""
        blockNumber = ethTx.blockNumberRaw ?: ""
        this.timeStamp = timeStamp
        error = if (isSuccess) "0" else "1"
        nonce = nonceValue
        from = ethTx.from ?: ""
        value = ethTx.value?.toString() ?: ""
        gas = ethTx.gas?.toString() ?: ""
        gasPrice = ethTx.gasPrice?.toString() ?: ""
        gasUsed = ethTx.gas?.toString() ?: ""
        this.chainId = chainId
        maxFeePerGas = ethTx.maxFeePerGasRaw?.let { ethTx.maxFeePerGas.toString() } ?: ""
        maxPriorityFee =
            ethTx.maxPriorityFeePerGasRaw?.let { ethTx.maxPriorityFeePerGas.toString() } ?: ""
    }

    /**
     * Builds a transaction from raw string data, inferring constructor status when possible.
     */
    constructor(
        hash: String?,
        isError: String?,
        blockNumber: String?,
        timeStamp: Long,
        nonce: Int,
        from: String,
        to: String,
        value: String,
        gas: String,
        gasPrice: String,
        input: String,
        gasUsed: String,
        chainId: Long,
        contractAddress: String?,
        functionName: String,
    ) : this() {
        this.hash = hash
        this.error = isError
        this.blockNumber = blockNumber
        this.timeStamp = timeStamp
        this.nonce = nonce
        this.from = from
        this.value = value
        this.gas = gas
        this.gasPrice = gasPrice
        this.input = input
        this.gasUsed = gasUsed
        this.chainId = chainId
        detectConstructor(contractAddress, from, nonce, to, input)
        this.functionName = functionName
    }

    /**
     * Builds a transaction including EIP-1559 fee data.
     */
    constructor(
        hash: String?,
        isError: String?,
        blockNumber: String?,
        timeStamp: Long,
        nonce: Int,
        from: String,
        to: String,
        value: String,
        gas: String,
        gasPrice: String,
        maxFeePerGas: String,
        maxPriorityFee: String,
        input: String,
        gasUsed: String,
        chainId: Long,
        contractAddress: String?,
    ) : this() {
        this.hash = hash
        this.error = isError
        this.blockNumber = blockNumber
        this.timeStamp = timeStamp
        this.nonce = nonce
        this.from = from
        this.value = value
        this.gas = gas
        this.gasPrice = gasPrice
        this.maxFeePerGas = maxFeePerGas
        this.maxPriorityFee = maxPriorityFee
        this.input = input
        this.gasUsed = gasUsed
        this.chainId = chainId
        detectConstructor(contractAddress, from, nonce, to, input)
        this.functionName = ""
    }

    public constructor(parcel: Parcel) : this() {
        hash = parcel.readString() ?: ""
        error = parcel.readString() ?: ""
        blockNumber = parcel.readString() ?: ""
        timeStamp = parcel.readLong()
        nonce = parcel.readInt()
        from = parcel.readString() ?: ""
        to = parcel.readString() ?: ""
        value = parcel.readString() ?: ""
        gas = parcel.readString() ?: ""
        gasPrice = parcel.readString() ?: ""
        input = parcel.readString() ?: ""
        gasUsed = parcel.readString() ?: ""
        chainId = parcel.readLong()
        maxFeePerGas = parcel.readString() ?: ""
        maxPriorityFee = parcel.readString() ?: ""
        functionName = parcel.readString() ?: ""
    }

    /**
     * Detects constructor markers and updates state accordingly.
     */
    private fun detectConstructor(
        contractAddress: String?,
        from: String,
        nonce: Int,
        toValue: String,
        inputValue: String,
    ) {
        if (!contractAddress.isNullOrEmpty()) {
            val testAddress = Utils.calculateContractAddress(from, nonce.toLong())
            if (testAddress.equals(contractAddress, ignoreCase = true)) {
                to = contractAddress
                isConstructor = true
                input = CONSTRUCTOR
                return
            }
        }
        to = toValue
        input = inputValue
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(hash)
        dest.writeString(error)
        dest.writeString(blockNumber)
        dest.writeLong(timeStamp)
        dest.writeInt(nonce)
        dest.writeString(from)
        dest.writeString(to)
        dest.writeString(value)
        dest.writeString(gas)
        dest.writeString(gasPrice)
        dest.writeString(input)
        dest.writeString(gasUsed)
        dest.writeLong(chainId)
        dest.writeString(maxFeePerGas)
        dest.writeString(maxPriorityFee)
        dest.writeString(functionName)
    }

    /**
     * Determines whether the transaction is relevant to a given contract/wallet combination.
     */
    fun isRelated(contractAddress: String, walletAddress: String): Boolean {
        return when {
            contractAddress == "eth" -> input == "0x" || from.equals(walletAddress, ignoreCase = true)
            walletAddress.equals(contractAddress, ignoreCase = true) ->
                from.equals(walletAddress, ignoreCase = true) || to.equals(walletAddress, ignoreCase = true)
            to.equals(contractAddress, ignoreCase = true) -> true
            else -> getWalletInvolvedInTransaction(walletAddress)
        }
    }

    /**
     * Returns the formatted operation value including prefix signs when applicable.
     */
    fun getOperationResult(token: Token, precision: Int): String {
        return if (hasInput()) {
            decodeTransactionInput(token.getWallet())
            val opValue = transactionInput?.getOperationValue(token, this).orEmpty()
            val isSendOrReceive =
                !from.equals(to, ignoreCase = true) &&
                    (transactionInput?.isSendOrReceive(this) == true)
            val prefix =
                if (opValue.isEmpty() || opValue.startsWith("#") || !isSendOrReceive) {
                    ""
                } else if (token.getIsSent(this)) {
                    "- "
                } else {
                    "+ "
                }
            prefix + opValue
        } else {
            token.getTransactionValue(this, precision)
        }
    }

    /**
     * Returns the formatted operation value without precision adjustments.
     */
    fun getOperationResult(token: Token): String {
        return if (hasInput()) {
            decodeTransactionInput(token.getWallet())
            transactionInput?.getOperationValue(token, this).orEmpty()
        } else {
            token.getTransactionValue(this)
        }
    }

    /**
     * Indicates whether the token symbol should be shown for this operation.
     */
    fun shouldShowSymbol(token: Token): Boolean {
        return if (hasInput()) {
            decodeTransactionInput(token.getWallet())
            transactionInput?.shouldShowSymbol(token) ?: true
        } else {
            true
        }
    }

    /**
     * Returns the address the operation targets, or empty when not applicable.
     */
    fun getOperationTokenAddress(): String =
        if (hasInput()) to.orEmpty() else ""

    /**
     * Determines whether the transaction was priced using legacy gas fields.
     */
    fun isLegacyTransaction(): Boolean =
        try {
            gasPrice.isNotEmpty() && BigInteger(gasPrice) > BigInteger.ZERO
        } catch (e: Exception) {
            true
        }

    /**
     * Returns a user-facing label for the operation (eg. Pending, Transfer).
     */
    fun getOperationName(ctx: Context, token: Token, walletAddress: String): String? {
        var txName: String? = null
        if (isPending()) {
            txName = ctx.getString(R.string.status_pending)
        }
        if (hasInput()) {
            decodeTransactionInput(walletAddress)
            if (token.isEthereum() && shouldShowSymbol(token)) {
                transactionInput?.type = TransactionType.CONTRACT_CALL
            }
            return transactionInput?.getOperationTitle(ctx)
        }

        return txName
    }

    /**
     * Derives the transaction type from the token and wallet context.
     */
    fun getTransactionType(token: Token, walletAddress: String): TransactionType {
        return if (isPending()) {
            TransactionType.UNKNOWN
        } else if (hasInput()) {
            decodeTransactionInput(walletAddress)
            if (token.isEthereum() && shouldShowSymbol(token)) {
                transactionInput?.type = TransactionType.CONTRACT_CALL
            }
            transactionInput?.type ?: TransactionType.UNKNOWN
        } else {
            TransactionType.UNKNOWN
        }
    }

    /**
     * Indicates whether the transaction has input calldata of meaningful length.
     */
    fun hasInput(): Boolean = input?.length ?: 0 >= 10

    /**
     * Resolves whether the operation represents an incoming or outgoing transfer.
     */
    fun getOperationToFrom(walletAddress: String): Int {
        return if (hasInput()) {
            decodeTransactionInput(walletAddress)
            transactionInput?.getOperationToFrom() ?: 0
        } else {
            0
        }
    }

    /**
     * Selects the status icon type for the operation list.
     */
    fun getOperationImage(token: Token): StatusType {
        return when {
            hasError() -> StatusType.FAILED
            hasInput() -> {
                decodeTransactionInput(token.getWallet())
                transactionInput?.getOperationImage(this, token.getWallet()) ?: StatusType.NONE
            }
            else -> if (from.equals(token.getWallet(), ignoreCase = true)) StatusType.SENT else StatusType.RECEIVE
        }
    }

    /**
     * Returns the general transaction category for a given wallet.
     */
    fun getTransactionType(wallet: String): TransactionType {
        return when {
            hasError() -> TransactionType.UNKNOWN
            hasInput() -> {
                decodeTransactionInput(wallet)
                transactionInput?.type ?: TransactionType.UNKNOWN
            }
            else -> TransactionType.SEND_ETH
        }
    }

    /**
     * Extracts supplemental info (eg. intrinsic ETH value) attached to a contract call.
     */
    fun getSupplementalInfo(walletAddress: String, networkName: String): String {
        return if (hasInput()) {
            decodeTransactionInput(walletAddress)
            transactionInput?.getSupplimentalInfo(this, walletAddress, networkName).orEmpty()
        } else {
            ""
        }
    }

    /**
     * Provides a prefix indicating whether the operation adds or removes value.
     */
    fun getPrefix(token: Token): String {
        if (hasInput()) {
            decodeTransactionInput(token.getWallet())
            if (transactionInput?.isSendOrReceive(this) != true || token.isEthereum()) {
                return ""
            } else if (token.isERC721()) {
                return ""
            }
        }

        val isSent = token.getIsSent(this)
        val isSelf = from.equals(to, ignoreCase = true)
        return when {
            isSelf -> ""
            isSent -> "- "
            else -> "+ "
        }
    }

    /**
     * Returns the raw Decimal value involved in the transaction.
     */
    @Throws(Exception::class)
    fun getRawValue(walletAddress: String): BigDecimal {
        return if (hasInput()) {
            decodeTransactionInput(walletAddress)
            transactionInput?.getRawValue() ?: BigDecimal.ZERO
        } else {
            BigDecimal(value)
        }
    }

    /**
     * Returns the current status (pending, failed, rejected) of the transaction.
     */
    fun getTransactionStatus(): StatusType? {
        return when {
            hasError() -> StatusType.FAILED
            blockNumber == "-1" -> StatusType.REJECTED
            isPending() -> StatusType.PENDING
            else -> null
        }
    }

    /**
     * Adds core transaction fields into the supplied map for templating.
     */
    fun addTransactionElements(resultMap: MutableMap<String, EventResult>) {
        resultMap["__hash"] = EventResult("", hash.orEmpty())
        resultMap["__to"] = EventResult("", to)
        resultMap["__from"] = EventResult("", from)
        resultMap["__value"] = EventResult("", value)
        resultMap["__chainId"] = EventResult("", chainId.toString())
    }

    /**
     * Returns the human-readable event label, when available.
     */
    fun getEventName(walletAddress: String): String {
        if (hasInput()) {
            decodeTransactionInput(walletAddress)
            return transactionInput?.getOperationEvent(walletAddress).orEmpty()
        }
        return ""
    }

    /**
     * Chooses a colour resource for supplemental info strings.
     */
    fun getSupplementalColour(supplementalTxt: String): Int {
        if (supplementalTxt.isNotEmpty()) {
            return when (supplementalTxt.getOrNull(1)) {
                '-' -> R.color.negative
                '+' -> R.color.positive
                else -> R.color.text_primary
            }
        }
        return R.color.text_primary
    }

    /**
     * Resolves the destination address for the transaction in the context of a token.
     */
    fun getDestination(token: Token?): String {
        if (token == null) return ""
        return if (hasInput()) {
            decodeTransactionInput(token.getWallet())
            transactionInput?.getOperationAddress(this, token).orEmpty()
        } else {
            token.getAddress()
        }
    }

    /**
     * Produces a user-facing description for the transaction operation.
     */
    fun getOperationDetail(
        ctx: Context,
        token: Token,
        tService: TokensService,
    ): String {
        return if (hasInput()) {
            decodeTransactionInput(token.getWallet())
            transactionInput?.getOperationDescription(ctx, this, token, tService).orEmpty()
        } else {
            ctx.getString(
                R.string.operation_definition,
                ctx.getString(R.string.to),
                ENSHandler.matchENSOrFormat(ctx, to),
            )
        }
    }

    /**
     * Ensures the transaction input is decoded before function-specific access.
     */
    private fun decodeTransactionInput(walletAddress: String?) {
        if (transactionInput == null && hasInput() && Utils.isAddressValid(walletAddress)) {
            walletAddress?.let {
                transactionInput = decoder.decodeInput(this, it)
            }
        }
    }

    /**
     * Checks if the transaction involves the supplied wallet address.
     */
    fun getWalletInvolvedInTransaction(walletAddr: String): Boolean {
        decodeTransactionInput(walletAddr)
        return when {
            transactionInput?.functionData != null && transactionInput?.containsAddress(walletAddr) == true -> true
            from.equals(walletAddr, ignoreCase = true) -> true
            to.equals(walletAddr, ignoreCase = true) -> true
            else -> {
                val rawInput = input.orEmpty()
                walletAddr.isNotEmpty() && rawInput.length > 40 &&
                    rawInput.contains(Numeric.cleanHexPrefix(walletAddr.lowercase()))
            }
        }
    }

    /**
     * Indicates whether an NFT transfer was sent by the supplied wallet.
     */
    fun isNFTSent(walletAddress: String): Boolean {
        return if (hasInput()) {
            decodeTransactionInput(walletAddress)
            transactionInput?.isSent() ?: true
        } else {
            true
        }
    }

    /**
     * Indicates whether value moved away from the supplied wallet.
     */
    fun getIsSent(walletAddress: String): Boolean {
        return if (hasInput()) {
            decodeTransactionInput(walletAddress)
            transactionInput?.isSent() ?: false
        } else {
            from.equals(walletAddress, ignoreCase = true)
        }
    }

    /**
     * Returns true when the operation reflects a balance change for the wallet.
     */
    fun isValueChange(walletAddress: String): Boolean {
        return if (hasInput()) {
            decodeTransactionInput(walletAddress)
            transactionInput?.isSendOrReceive(this) ?: false
        } else {
            true
        }
    }

    /**
     * Calculates the address created by a contract deployment.
     */
    private fun calculateContractAddress(account: String, nonce: Long): String {
        val addressBytes = Numeric.hexStringToByteArray(account)
        var calculated =
            Hash.sha3(
                RlpEncoder.encode(
                    RlpList(
                        RlpString.create(addressBytes),
                        RlpString.create(nonce),
                    ),
                ),
            )
        calculated = Arrays.copyOfRange(calculated, 12, calculated.size)
        return Numeric.toHexString(calculated)
    }
}
