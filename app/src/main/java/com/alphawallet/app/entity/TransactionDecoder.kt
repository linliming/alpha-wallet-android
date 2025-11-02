package com.alphawallet.app.entity

import com.alphawallet.app.web3.entity.Web3Transaction
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import timber.log.Timber
import java.math.BigInteger

/**
 * Parses transaction input payloads and extracts typed argument data.
 */
class TransactionDecoder {
    private var parseIndex: Int = 0
    private val functionList: MutableMap<String, FunctionData> = mutableMapOf()
    private var state: ReadState = ReadState.ARGS
    private var sigCount: Int = 0

    init {
        setupKnownFunctions()
    }

    private fun getUnknownFunction(): FunctionData = FunctionData("Contract Call", ContractType.OTHER)

    /**
     * Decodes a hex-encoded transaction input string into a [TransactionInput] structure.
     */
    fun decodeInput(input: String?): TransactionInput {
        var parseState = ParseStage.PARSE_FUNCTION
        parseIndex = 0
        val decoded = TransactionInput()

        if (input.isNullOrEmpty() || input.length < FUNCTION_LENGTH) {
            decoded.functionData = getUnknownFunction()
            return decoded
        }

        try {
            while (parseIndex < input.length && parseState != ParseStage.FINISH) {
                parseState = when (parseState) {
                    ParseStage.PARSE_FUNCTION -> setFunction(decoded, readBytes(input, FUNCTION_LENGTH))
                    ParseStage.PARSE_ARGS -> getParams(decoded, input)
                    ParseStage.FINISH -> ParseStage.FINISH
                    ParseStage.ERROR -> ParseStage.FINISH
                }
                if (parseIndex < 0) break
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        decoded.setOperationType(null, null)
        return decoded
    }

    /**
     * Decodes [tx] and determines the operation type based on the provided wallet context.
     */
    fun decodeInput(tx: Transaction, walletAddress: String?): TransactionInput {
        val decoded = decodeInput(tx.input)
        decoded.setOperationType(tx, walletAddress)
        return decoded
    }

    /**
     * Decodes a pending [web3Tx] payload and classifies it using the supplied wallet address.
     */
    fun decodeInput(web3Tx: Web3Transaction, chainId: Long, walletAddress: String): TransactionInput {
        val decoded = decodeInput(web3Tx.payload)
        val tx = Transaction(web3Tx, chainId, walletAddress)
        decoded.setOperationType(tx, walletAddress)
        return decoded
    }

    private fun setFunction(thisData: TransactionInput, input: String): ParseStage {
        val data = functionList[input]
        return if (data != null) {
            thisData.functionData = data
            thisData.arrayValues.clear()
            thisData.addresses.clear()
            thisData.sigData.clear()
            thisData.miscData.clear()
            thisData.functionData.functionRawHex = input
            ParseStage.PARSE_ARGS
        } else {
            thisData.functionData = getUnknownFunction()
            thisData.functionData.functionRawHex = input
            ParseStage.ERROR
        }
    }

    private fun getParams(thisData: TransactionInput, input: String): ParseStage {
        state = ReadState.ARGS
        var count: BigInteger
        val builder = StringBuilder()
        val args = thisData.functionData.args ?: return ParseStage.FINISH

        for (type in args) {
            var argData = read256bits(input)
            if (argData == "0") break
            when (type) {
                "bytes" -> {
                    builder.setLength(0)
                    argData = read256bits(input)
                    val dataCount = Numeric.toBigInt(argData)
                    val hexBytes = readBytes(input, dataCount.toInt())
                    thisData.miscData.add(hexBytes)
                    thisData.hexArgs.add(Numeric.prependHexPrefix(hexBytes))
                }

                "string" -> {
                    count = BigInteger(argData, HEX_RADIX)
                    builder.setLength(0)
                    argData = read256bits(input)
                    if (count.toInt() > argData.length) {
                        count = BigInteger.valueOf(argData.length.toLong())
                    }
                    var index = 0
                    while (index < count.toInt() * 2 && index + 2 <= argData.length) {
                        val value = argData.substring(index, index + 2).toInt(HEX_RADIX)
                        builder.append(value.toChar())
                        index += 2
                    }
                    val ascii = builder.toString()
                    thisData.miscData.add(Numeric.cleanHexPrefix(ascii))
                    thisData.hexArgs.add(String(Numeric.hexStringToByteArray(ascii)))
                }

                "address" -> {
                    if (argData.length >= ADDRESS_WORD_LENGTH - Keys.ADDRESS_LENGTH_IN_HEX) {
                        val addr = Numeric.prependHexPrefix(argData.substring(ADDRESS_WORD_LENGTH - Keys.ADDRESS_LENGTH_IN_HEX))
                        thisData.addresses.add(addr)
                        thisData.hexArgs.add(addr)
                    }
                }

                "bytes32" -> addArg(thisData, argData)

                "bytes32[]",
                "uint16[]",
                "uint256[]" -> {
                    count = BigInteger(argData, HEX_RADIX)
                    for (i in 0 until count.toInt()) {
                        val inputData = read256bits(input)
                        thisData.arrayValues.add(BigInteger(inputData, HEX_RADIX))
                        thisData.hexArgs.add(inputData)
                        if (inputData == "0") break
                    }
                }

                "uint256",
                "uint" -> addArg(thisData, argData)

                "uint8" -> {
                    if (thisData.functionData.hasSig) {
                        state = ReadState.SIGNATURE
                        sigCount = 0
                    }
                    addArg(thisData, argData)
                }

                "nodata" -> Unit

                "bool" -> {
                    val value = BigInteger(argData, HEX_RADIX)
                    thisData.hexArgs.add(if (value.toLong() == 0L) "false" else "true")
                }

                else -> Unit
            }
        }
        return ParseStage.FINISH
    }

    private fun addArg(thisData: TransactionInput, input: String) {
        when (state) {
            ReadState.ARGS -> thisData.miscData.add(Numeric.cleanHexPrefix(input))
            ReadState.SIGNATURE -> {
                thisData.sigData.add(input)
                if (++sigCount == SIGNATURE_COMPONENTS) state = ReadState.ARGS
            }
        }
        thisData.hexArgs.add(input)
    }

    private fun readBytes(input: String, bytes: Int): String {
        return if (parseIndex + bytes <= input.length) {
            val value = input.substring(parseIndex, parseIndex + bytes)
            parseIndex += bytes
            value
        } else {
            "0"
        }
    }

    private fun read256bits(input: String): String {
        return if (parseIndex + WORD_SIZE <= input.length) {
            val value = input.substring(parseIndex, parseIndex + WORD_SIZE)
            parseIndex += WORD_SIZE
            value
        } else {
            "0"
        }
    }

    private fun addFunction(method: String, type: ContractType, hasSig: Boolean) {
        val methodId = buildMethodId(method)
        val data = functionList[methodId]
        if (data != null) {
            data.addType(type)
        } else {
            functionList[methodId] = FunctionData(method, type, hasSig)
        }
    }

    private fun setupKnownFunctions() {
        addFunction("transferFrom(address,address,uint16[])", ContractType.ERC875_LEGACY, false)
        addFunction("transfer(address,uint16[])", ContractType.ERC875_LEGACY, false)
        addFunction("trade(uint256,uint16[],uint8,bytes32,bytes32)", ContractType.ERC875_LEGACY, true)
        addFunction("passTo(uint256,uint16[],uint8,bytes32,bytes32,address)", ContractType.ERC875_LEGACY, true)
        addFunction("loadNewTickets(bytes32[])", ContractType.ERC875_LEGACY, false)
        addFunction("balanceOf(address)", ContractType.ERC875_LEGACY, false)

        addFunction("transfer(address,uint256)", ContractType.ERC20, false)
        addFunction("transfer(address,uint)", ContractType.ERC20, false)
        addFunction("transferFrom(address,address,uint256)", ContractType.ERC20, false)
        addFunction("approve(address,uint256)", ContractType.ERC20, false)
        addFunction("approve(address,uint)", ContractType.ERC20, false)
        addFunction("allocateTo(address,uint256)", ContractType.ERC20, false)
        addFunction("allowance(address,address)", ContractType.ERC20, false)
        addFunction("transferFrom(address,address,uint)", ContractType.ERC20, false)
        addFunction("approveAndCall(address,uint,bytes)", ContractType.ERC20, false)
        addFunction("balanceOf(address)", ContractType.ERC20, false)
        addFunction("transferAnyERC20Token(address,uint)", ContractType.ERC20, false)
        addFunction("delegate(address)", ContractType.ERC20, false)
        addFunction("mint(address,uint)", ContractType.ERC20, false)
        addFunction("swapExactTokensForTokens(uint256,uint256,address[],address,uint256)", ContractType.ERC20, false)
        addFunction("withdraw(address,uint256,address)", ContractType.ERC20, false)
        addFunction("deposit(address,uint256,address,uint16)", ContractType.ERC20, false)
        addFunction("deposit()", ContractType.ERC20, false)

        addFunction("transferFrom(address,address,uint256[])", ContractType.ERC875, false)
        addFunction("transfer(address,uint256[])", ContractType.ERC875, false)
        addFunction("trade(uint256,uint256[],uint8,bytes32,bytes32)", ContractType.ERC875, true)
        addFunction("passTo(uint256,uint256[],uint8,bytes32,bytes32,address)", ContractType.ERC875, true)
        addFunction("loadNewTickets(uint256[])", ContractType.ERC875, false)
        addFunction("balanceOf(address)", ContractType.ERC875, false)

        addFunction("endContract()", ContractType.CREATION, false)
        addFunction("selfdestruct()", ContractType.CREATION, false)
        addFunction("kill()", ContractType.CREATION, false)

        addFunction("safeTransferFrom(address,address,uint256,bytes)", ContractType.ERC721, false)
        addFunction("safeTransferFrom(address,address,uint256)", ContractType.ERC721, false)
        addFunction("transferFrom(address,address,uint256)", ContractType.ERC721, false)
        addFunction("approve(address,uint256)", ContractType.ERC721, false)
        addFunction("setApprovalForAll(address,bool)", ContractType.ERC721, false)
        addFunction("getApproved(address,address,uint256)", ContractType.ERC721, false)
        addFunction("isApprovedForAll(address,address)", ContractType.ERC721, false)
        addFunction("transfer(address,uint256)", ContractType.ERC721_LEGACY, false)
        addFunction("giveBirth(uint256,uint256)", ContractType.ERC721, false)
        addFunction("breedWithAuto(uint256,uint256)", ContractType.ERC721, false)
        addFunction("ownerOf(uint256)", ContractType.ERC721, false)
        addFunction("createSaleAuction(uint256,uint256,uint256,uint256)", ContractType.ERC721, false)
        addFunction("mixGenes(uint256,uint256,uint256)", ContractType.ERC721, false)
        addFunction("tokensOfOwner(address)", ContractType.ERC721, false)
        addFunction("store(uint256)", ContractType.ERC721, false)
        addFunction("remix(uint256,bytes)", ContractType.ERC721, false)

        addFunction("safeTransferFrom(address,address,uint256,uint256,bytes)", ContractType.ERC1155, false)
        addFunction("safeBatchTransferFrom(address,address,uint256[],uint256[],bytes)", ContractType.ERC1155, false)

        addFunction("dropCurrency(uint32,uint32,uint32,uint8,bytes32,bytes32,address)", ContractType.CURRENCY, true)
        addFunction("withdraw(uint256)", ContractType.CURRENCY, false)

        addFunctionImmediate("commitNFT()", "0x521d83f0", ContractType.ERC721, false)
    }

    private fun addFunctionImmediate(functionBody: String, functionHash: String, type: ContractType, hasSig: Boolean) {
        val data = functionList[functionHash]
        if (data != null) {
            data.addType(type)
        } else {
            functionList[functionHash] = FunctionData(functionBody, type, hasSig)
        }
    }

    /**
     * Adds an additional function signature for scanning/decoding at runtime.
     */
    fun addScanFunction(methodSignature: String, hasSig: Boolean) {
        addFunction(methodSignature, ContractType.OTHER, hasSig)
    }

    /**
     * Attempts to infer the contract type based on known function signatures in the bytecode.
     */
    fun getContractType(input: String): ContractType {
        if (input.length < FUNCTION_LENGTH) return ContractType.OTHER
        val functionCount = mutableMapOf<ContractType, Int>()
        var highestType = ContractType.OTHER
        var highestCount = 0

        val balanceMethod = Numeric.cleanHexPrefix(buildMethodId("balanceOf(address)"))
        val isStormbird = Numeric.cleanHexPrefix(buildMethodId("isStormBirdContract()"))
        val isStormbird2 = Numeric.cleanHexPrefix(buildMethodId("isStormBird()"))
        val trade = Numeric.cleanHexPrefix(buildMethodId("trade(uint256,uint256[],uint8,bytes32,bytes32)"))
        val tradeLegacy = Numeric.cleanHexPrefix(buildMethodId("trade(uint256,uint16[],uint8,bytes32,bytes32)"))

        if (input.contains(balanceMethod)) {
            if (input.contains(isStormbird) ||
                input.contains(isStormbird2) ||
                input.contains(tradeLegacy) ||
                input.contains(trade)
            ) {
                return if (input.contains(tradeLegacy)) ContractType.ERC875_LEGACY else ContractType.ERC875
            }
        } else {
            return ContractType.OTHER
        }

        for ((signature, data) in functionList) {
            val cleanSig = Numeric.cleanHexPrefix(signature)
            if (input.indexOf(cleanSig) >= 0) {
                data.contractType.forEach { contractType ->
                    val count = (functionCount[contractType] ?: 0) + 1
                    functionCount[contractType] = count
                    if (count > highestCount) {
                        highestCount = count
                        highestType = contractType
                    }
                }
            }
        }

        highestType = when {
            highestType == ContractType.ERC721 && functionCount.containsKey(ContractType.ERC721_LEGACY) -> ContractType.ERC721_LEGACY
            functionCount.containsKey(ContractType.ERC20) -> ContractType.ERC20
            else -> highestType
        }

        return highestType
    }

    /**
     * Extracts the embedded signature data (v,r,s) when present.
     */
    fun getSignatureData(data: TransactionInput): Sign.SignatureData? {
        if (data.functionData.hasSig && data.sigData.size == SIGNATURE_COMPONENTS) {
            val vBi = BigInteger(data.sigData[0], HEX_RADIX)
            val rBi = BigInteger(data.sigData[1], HEX_RADIX)
            val sBi = BigInteger(data.sigData[2], HEX_RADIX)
            val v = vBi.toByte()
            val r = Numeric.toBytesPadded(rBi, SIGNATURE_WORD)
            val s = Numeric.toBytesPadded(sBi, SIGNATURE_WORD)
            return Sign.SignatureData(v, r, s)
        }
        return null
    }

    /**
     * Returns any integer indices parsed from the transaction input.
     */
    fun getIndices(data: TransactionInput?): IntArray? {
        val values = data?.arrayValues ?: return null
        val indices = IntArray(values.size)
        for (i in values.indices) {
            indices[i] = values[i].toInt()
        }
        return indices
    }

    private enum class ReadState {
        ARGS,
        SIGNATURE
    }

    private enum class ParseStage {
        PARSE_FUNCTION,
        PARSE_ARGS,
        FINISH,
        ERROR
    }

    companion object {
        const val FUNCTION_LENGTH = 10
        private const val HEX_RADIX = 16
        private const val ADDRESS_WORD_LENGTH = 64
        private const val WORD_SIZE = 64
        private const val SIGNATURE_COMPONENTS = 3
        private const val SIGNATURE_WORD = 32
        private val endContractSignatures = mutableListOf<String>()

        /**
         * Calculates the 4-byte method selector from the provided function signature.
         */
        @JvmStatic
        fun buildMethodId(methodSignature: String): String {
            val input = methodSignature.toByteArray()
            val hash = Hash.sha3(input)
            return Numeric.toHexString(hash).substring(0, FUNCTION_LENGTH)
        }

        /**
         * Checks whether the given method selector corresponds to a contract self-destruct call.
         */
        @JvmStatic
        fun isEndContract(input: String?): Boolean {
            if (input == null || input.length != FUNCTION_LENGTH) return false
            if (endContractSignatures.isEmpty()) buildEndContractSigs()
            return endContractSignatures.any { it == input }
        }

        private fun buildEndContractSigs() {
            endContractSignatures.add(buildMethodId("endContract()"))
            endContractSignatures.add(buildMethodId("selfdestruct()"))
            endContractSignatures.add(buildMethodId("kill()"))
        }
    }
}
