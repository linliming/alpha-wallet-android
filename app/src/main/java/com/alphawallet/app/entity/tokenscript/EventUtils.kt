package com.alphawallet.app.entity.tokenscript

import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.token.entity.AttributeInterface
import com.alphawallet.token.entity.ContractAddress
import com.alphawallet.token.entity.ContractInfo
import com.alphawallet.token.entity.EventDefinition
import com.alphawallet.token.entity.NamedType
import io.reactivex.Single
import org.web3j.abi.EventEncoder
import org.web3j.abi.EventValues
import org.web3j.abi.TypeEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.BytesType
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.Int
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Uint
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.*
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.EthBlock
import org.web3j.protocol.core.methods.response.EthLog
import org.web3j.protocol.core.methods.response.EthTransaction
import org.web3j.protocol.core.methods.response.Log
import org.web3j.tx.Contract.staticExtractEventParameters
import org.web3j.utils.Numeric
import timber.log.Timber
import java.io.IOException
import java.math.BigInteger
import java.util.Locale

/**
 * Created by JB on 23/03/2020.
 *
 * Class to contain event filter creation functions
 *
 * This class would be better placed entirely in the common library. However because the app uses web3j-Android and DMZ uses web3j
 * the function signatures are incompatible.
 *
 * The cleanest solution is to link the main web3j into the library. Web3j-Android and web3j currently have
 * incompatibilities. Web3j labs would like to eliminate Web3j-Android, which would clear things up very well
 * but this can only be done after we drop API23, even then it may not be possible immediately.
 *
 * Recommend: see if web3j base is compatible with Android API24+. If so, phase out API23 by warning users support will discontinue,
 * then do refactor and move these functions into the EventDefinition class in the library.
 *
 */
object EventUtils {
    @JvmStatic
    @Throws(Exception::class)
    fun generateLogFilter(
        ev: EventDefinition,
        tokenIds: List<BigInteger>,
        attrIf: AttributeInterface,
    ): EthFilter? = generateLogFilter(ev, null, tokenIds, attrIf)

    @JvmStatic
    @Throws(Exception::class)
    fun generateLogFilter(
        ev: EventDefinition,
        originToken: Token?,
        attrIf: AttributeInterface,
    ): EthFilter? = originToken?.let { generateLogFilter(ev, it, it.getUniqueTokenIds(), attrIf) }

    @JvmStatic
    @Throws(Exception::class)
    fun generateLogFilter(
        ev: EventDefinition,
        @Suppress("UNUSED_PARAMETER") originToken: Token?,
        tokenIds: List<BigInteger>,
        attrIf: AttributeInterface,
    ): EthFilter? {
        val contractInfo = ev.contract ?: return null

        val chainId = contractInfo.addresses.keys.iterator().next()
        val eventContractAddr = contractInfo.addresses[chainId]?.getOrNull(0) ?: return null

        val resolverEvent = generateEventFunction(ev)
        val filterTopic = ev.filterTopicIndex
        val filterTopicValue: String = ev.filterTopicValue ?: return null
        val topicIndex = ev.getTopicIndex(filterTopic)
        val type = ev.type ?: return null
        val indexedParams = type.getArgNames(true)

        var startBlock: DefaultBlockParameter = DefaultBlockParameterName.EARLIEST
        if (ev.readBlock > BigInteger.ZERO) {
            startBlock = DefaultBlockParameter.valueOf(ev.readBlock)
        }

        val filter = EthFilter(
            startBlock,
            DefaultBlockParameterName.LATEST,
            eventContractAddr
        ).addSingleTopic(EventEncoder.encode(resolverEvent))

        for (i in indexedParams.indices) {
            if (i == topicIndex) {
                if (!addTopicFilter(ev, contractInfo, filter, filterTopicValue, tokenIds, attrIf)) {
                    return null
                }
                break
            } else {
                filter.addSingleTopic(null)
            }
        }

        return filter
    }

    @JvmStatic
    fun getSelectVal(ev: EventDefinition, ethLog: EthLog.LogResult<*>): String {
        val resolverEvent = generateEventFunction(ev)
        val eventValues = staticExtractEventParameters(resolverEvent, ethLog.get() as Log)
        val selectIndexInNonIndexed = ev.getSelectIndex(false)
        val selectIndexInIndexed = ev.getSelectIndex(true)

        return when {
            selectIndexInNonIndexed >= 0 -> getValueFromParams(
                eventValues.nonIndexedValues,
                selectIndexInNonIndexed
            )

            selectIndexInIndexed >= 0 -> getValueFromParams(
                eventValues.indexedValues,
                selectIndexInIndexed
            )

            else -> ""
        }
    }

    @JvmStatic
    fun getTopicVal(ev: EventDefinition, ethLog: EthLog.LogResult<*>): String {
        val resolverEvent = generateEventFunction(ev)
        val eventValues = staticExtractEventParameters(resolverEvent, ethLog.get() as Log)
        val filterTopic = ev.filterTopicIndex
        val topicIndex = ev.getTopicIndex(filterTopic)
        return getValueFromParams(eventValues.indexedValues, topicIndex)
    }

    @JvmStatic
    fun getBlockDetails(blockHash: String, web3j: Web3j): Single<EthBlock> {
        return Single.fromCallable {
            try {
                web3j.ethGetBlockByHash(blockHash.trim(), false).send()
            } catch (e: IOException) {
                Timber.e(e)
                EthBlock()
            } catch (e: NullPointerException) {
                Timber.e(e)
                EthBlock()
            }
        }
    }

    @JvmStatic
    fun getTransactionDetails(blockHash: String, web3j: Web3j): Single<EthTransaction> {
        return Single.fromCallable {
            try {
                web3j.ethGetTransactionByHash(blockHash.trim()).send()
            } catch (e: IOException) {
                Timber.e(e)
                EthTransaction()
            } catch (e: NullPointerException) {
                Timber.e(e)
                EthTransaction()
            }
        }
    }

    private fun getValueFromParams(responseParams: List<Type<*>>, selectIndex: kotlin.Int): String {
        val typeValue = responseParams[selectIndex]
        var typeName = typeValue.typeAsString
        var i = typeName.length - 1
        while (i >= 0 && typeName[i].isDigit()) {
            i--
        }
        typeName = typeName.substring(0, i + 1)

        return when (typeName.lowercase(Locale.US)) {
            "string",
            "address",
            "uint",
            "int",
            "bool",
            "fixed" -> typeValue.value.toString()

            "bytes" -> {
                val value = typeValue.value as ByteArray
                Numeric.toHexString(value)
            }

            else -> "Unexpected type: ${typeValue.typeAsString}"
        }
    }

    private fun generateFunctionDefinition(
        args: List<NamedType.SequenceElement>,
    ): List<TypeReference<out Type<*>>> {
        val paramList = mutableListOf<TypeReference<out Type<*>>>()
        for (element in args) {
            when (element.type) {
                "int" -> paramList.add(object : TypeReference<Int>(element.indexed) {})
                "int8" -> paramList.add(object : TypeReference<Int8>(element.indexed) {})
                "int16" -> paramList.add(object : TypeReference<Int16>(element.indexed) {})
                "int24" -> paramList.add(object : TypeReference<Int24>(element.indexed) {})
                "int32" -> paramList.add(object : TypeReference<Int32>(element.indexed) {})
                "int40" -> paramList.add(object : TypeReference<Int40>(element.indexed) {})
                "int48" -> paramList.add(object : TypeReference<Int48>(element.indexed) {})
                "int56" -> paramList.add(object : TypeReference<Int56>(element.indexed) {})
                "int64" -> paramList.add(object : TypeReference<Int64>(element.indexed) {})
                "int72" -> paramList.add(object : TypeReference<Int72>(element.indexed) {})
                "int80" -> paramList.add(object : TypeReference<Int80>(element.indexed) {})
                "int88" -> paramList.add(object : TypeReference<Int88>(element.indexed) {})
                "int96" -> paramList.add(object : TypeReference<Int96>(element.indexed) {})
                "int104" -> paramList.add(object : TypeReference<Int104>(element.indexed) {})
                "int112" -> paramList.add(object : TypeReference<Int112>(element.indexed) {})
                "int120" -> paramList.add(object : TypeReference<Int120>(element.indexed) {})
                "int128" -> paramList.add(object : TypeReference<Int128>(element.indexed) {})
                "int136" -> paramList.add(object : TypeReference<Int136>(element.indexed) {})
                "int144" -> paramList.add(object : TypeReference<Int144>(element.indexed) {})
                "int152" -> paramList.add(object : TypeReference<Int152>(element.indexed) {})
                "int160" -> paramList.add(object : TypeReference<Int160>(element.indexed) {})
                "int168" -> paramList.add(object : TypeReference<Int168>(element.indexed) {})
                "int176" -> paramList.add(object : TypeReference<Int176>(element.indexed) {})
                "int184" -> paramList.add(object : TypeReference<Int184>(element.indexed) {})
                "int192" -> paramList.add(object : TypeReference<Int192>(element.indexed) {})
                "int200" -> paramList.add(object : TypeReference<Int200>(element.indexed) {})
                "int208" -> paramList.add(object : TypeReference<Int208>(element.indexed) {})
                "int216" -> paramList.add(object : TypeReference<Int216>(element.indexed) {})
                "int224" -> paramList.add(object : TypeReference<Int224>(element.indexed) {})
                "int232" -> paramList.add(object : TypeReference<Int232>(element.indexed) {})
                "int240" -> paramList.add(object : TypeReference<Int240>(element.indexed) {})
                "int248" -> paramList.add(object : TypeReference<Int248>(element.indexed) {})
                "int256" -> paramList.add(object : TypeReference<Int256>(element.indexed) {})
                "uint" -> paramList.add(object : TypeReference<Uint>(element.indexed) {})
                "uint8" -> paramList.add(object : TypeReference<Uint8>(element.indexed) {})
                "uint16" -> paramList.add(object : TypeReference<Uint16>(element.indexed) {})
                "uint24" -> paramList.add(object : TypeReference<Uint24>(element.indexed) {})
                "uint32" -> paramList.add(object : TypeReference<Uint32>(element.indexed) {})
                "uint40" -> paramList.add(object : TypeReference<Uint40>(element.indexed) {})
                "uint48" -> paramList.add(object : TypeReference<Uint48>(element.indexed) {})
                "uint56" -> paramList.add(object : TypeReference<Uint56>(element.indexed) {})
                "uint64" -> paramList.add(object : TypeReference<Uint64>(element.indexed) {})
                "uint72" -> paramList.add(object : TypeReference<Uint72>(element.indexed) {})
                "uint80" -> paramList.add(object : TypeReference<Uint80>(element.indexed) {})
                "uint88" -> paramList.add(object : TypeReference<Uint88>(element.indexed) {})
                "uint96" -> paramList.add(object : TypeReference<Uint96>(element.indexed) {})
                "uint104" -> paramList.add(object : TypeReference<Uint104>(element.indexed) {})
                "uint112" -> paramList.add(object : TypeReference<Uint112>(element.indexed) {})
                "uint120" -> paramList.add(object : TypeReference<Uint120>(element.indexed) {})
                "uint128" -> paramList.add(object : TypeReference<Uint128>(element.indexed) {})
                "uint136" -> paramList.add(object : TypeReference<Uint136>(element.indexed) {})
                "uint144" -> paramList.add(object : TypeReference<Uint144>(element.indexed) {})
                "uint152" -> paramList.add(object : TypeReference<Uint152>(element.indexed) {})
                "uint160" -> paramList.add(object : TypeReference<Uint160>(element.indexed) {})
                "uint168" -> paramList.add(object : TypeReference<Uint168>(element.indexed) {})
                "uint176" -> paramList.add(object : TypeReference<Uint176>(element.indexed) {})
                "uint184" -> paramList.add(object : TypeReference<Uint184>(element.indexed) {})
                "uint192" -> paramList.add(object : TypeReference<Uint192>(element.indexed) {})
                "uint200" -> paramList.add(object : TypeReference<Uint200>(element.indexed) {})
                "uint208" -> paramList.add(object : TypeReference<Uint208>(element.indexed) {})
                "uint216" -> paramList.add(object : TypeReference<Uint216>(element.indexed) {})
                "uint224" -> paramList.add(object : TypeReference<Uint224>(element.indexed) {})
                "uint232" -> paramList.add(object : TypeReference<Uint232>(element.indexed) {})
                "uint240" -> paramList.add(object : TypeReference<Uint240>(element.indexed) {})
                "uint248" -> paramList.add(object : TypeReference<Uint248>(element.indexed) {})
                "uint256" -> paramList.add(object : TypeReference<Uint256>(element.indexed) {})
                "address" -> paramList.add(object : TypeReference<Address>(element.indexed) {})
                "string" -> paramList.add(object : TypeReference<Utf8String>(element.indexed) {})
                "bytes" -> paramList.add(object : TypeReference<BytesType>(element.indexed) {})
                "bytes1" -> paramList.add(object : TypeReference<Bytes1>(element.indexed) {})
                "bytes2" -> paramList.add(object : TypeReference<Bytes2>(element.indexed) {})
                "bytes3" -> paramList.add(object : TypeReference<Bytes3>(element.indexed) {})
                "bytes4" -> paramList.add(object : TypeReference<Bytes4>(element.indexed) {})
                "bytes5" -> paramList.add(object : TypeReference<Bytes5>(element.indexed) {})
                "bytes6" -> paramList.add(object : TypeReference<Bytes6>(element.indexed) {})
                "bytes7" -> paramList.add(object : TypeReference<Bytes7>(element.indexed) {})
                "bytes8" -> paramList.add(object : TypeReference<Bytes8>(element.indexed) {})
                "bytes9" -> paramList.add(object : TypeReference<Bytes9>(element.indexed) {})
                "bytes10" -> paramList.add(object : TypeReference<Bytes10>(element.indexed) {})
                "bytes11" -> paramList.add(object : TypeReference<Bytes11>(element.indexed) {})
                "bytes12" -> paramList.add(object : TypeReference<Bytes12>(element.indexed) {})
                "bytes13" -> paramList.add(object : TypeReference<Bytes13>(element.indexed) {})
                "bytes14" -> paramList.add(object : TypeReference<Bytes14>(element.indexed) {})
                "bytes15" -> paramList.add(object : TypeReference<Bytes15>(element.indexed) {})
                "bytes16" -> paramList.add(object : TypeReference<Bytes16>(element.indexed) {})
                "bytes17" -> paramList.add(object : TypeReference<Bytes17>(element.indexed) {})
                "bytes18" -> paramList.add(object : TypeReference<Bytes18>(element.indexed) {})
                "bytes19" -> paramList.add(object : TypeReference<Bytes19>(element.indexed) {})
                "bytes20" -> paramList.add(object : TypeReference<Bytes20>(element.indexed) {})
                "bytes21" -> paramList.add(object : TypeReference<Bytes21>(element.indexed) {})
                "bytes22" -> paramList.add(object : TypeReference<Bytes22>(element.indexed) {})
                "bytes23" -> paramList.add(object : TypeReference<Bytes23>(element.indexed) {})
                "bytes24" -> paramList.add(object : TypeReference<Bytes24>(element.indexed) {})
                "bytes25" -> paramList.add(object : TypeReference<Bytes25>(element.indexed) {})
                "bytes26" -> paramList.add(object : TypeReference<Bytes26>(element.indexed) {})
                "bytes27" -> paramList.add(object : TypeReference<Bytes27>(element.indexed) {})
                "bytes28" -> paramList.add(object : TypeReference<Bytes28>(element.indexed) {})
                "bytes29" -> paramList.add(object : TypeReference<Bytes29>(element.indexed) {})
                "bytes30" -> paramList.add(object : TypeReference<Bytes30>(element.indexed) {})
                "bytes31" -> paramList.add(object : TypeReference<Bytes31>(element.indexed) {})
                "bytes32" -> paramList.add(object : TypeReference<Bytes32>(element.indexed) {})
                else -> Timber.d("NOT IMPLEMENTED: ${element.type}")
            }
        }

        return paramList
    }

    private fun generateEventFunction(ev: EventDefinition): Event {
        val type = requireNotNull(ev.type) { "EventDefinition.type is null: $ev" }
        val name = requireNotNull(type.name) { "EventDefinition.type.name is null: $ev" }

        @Suppress("UNCHECKED_CAST")
        val eventArgSpec =
            generateFunctionDefinition(type.sequenceArgs) as List<TypeReference<Type<*>>>
        return Event(name, eventArgSpec)
    }

    @Throws(Exception::class)
    private fun addTopicFilter(
        ev: EventDefinition,
        contractInfo: ContractInfo,
        filter: EthFilter,
        filterTopicValue: String,
        tokenIds: List<BigInteger>,
        attrIf: AttributeInterface,
    ): Boolean {
        return when (filterTopicValue) {
            "tokenId" -> {
                when {
                    tokenIds.isEmpty() -> false
                    tokenIds.size == 1 -> {
                        filter.addSingleTopic(
                            Numeric.prependHexPrefix(
                                TypeEncoder.encode(Uint256(tokenIds[0]))
                            )
                        )
                        true
                    }

                    else -> {
                        val optionals = tokenIds.map {
                            Numeric.prependHexPrefix(TypeEncoder.encode(Uint256(it)))
                        }
                        filter.addOptionalTopics(*optionals.toTypedArray())
                        true
                    }
                }
            }

            "ownerAddress" -> {
                filter.addSingleTopic(
                    Numeric.prependHexPrefix(
                        TypeEncoder.encode(Address(attrIf.walletAddr))
                    )
                )
                true
            }

            else -> {
                val attr = attrIf.fetchAttribute(contractInfo, filterTopicValue)
                if (attr != null) {
                    val tokenAddr = ContractAddress(ev.eventChainId, ev.eventContractAddress)
                    when {
                        tokenIds.isEmpty() -> false
                        tokenIds.size == 1 -> {
                            val attrResult = attrIf.fetchAttrResult(tokenAddr, attr, tokenIds[0])
                            filter.addSingleTopic(
                                Numeric.prependHexPrefix(
                                    TypeEncoder.encode(Uint256(attrResult.value))
                                )
                            )
                            true
                        }

                        else -> {
                            val optionals = tokenIds.map { uid ->
                                val attrResult = attrIf.fetchAttrResult(tokenAddr, attr, uid)
                                Numeric.prependHexPrefix(
                                    TypeEncoder.encode(Uint256(attrResult.value))
                                )
                            }
                            filter.addOptionalTopics(*optionals.toTypedArray())
                            true
                        }
                    }
                } else {
                    throw Exception("Unresolved event filter name: $filterTopicValue")
                }
            }
        }
    }

    @JvmStatic
    fun getAllTopics(ev: EventDefinition, log: EthLog.LogResult<*>): String {
        val resolverEvent = generateEventFunction(ev)
        val eventValues = staticExtractEventParameters(resolverEvent, log.get() as Log)

        val sb = StringBuilder()
        var first = true
        val type = requireNotNull(ev.type) { "EventDefinition.type is null: $ev" }
        for (element in type.sequenceArgs) {
            if (!first) sb.append(",")
            sb.append(element.name)
            sb.append(",")
            sb.append(element.type)
            sb.append(",")
            val result = element.name?.let {
                getEventResult(ev, it, eventValues)
            }
            sb.append(result)
            first = false
        }

        return sb.toString()
    }

    private fun getEventResult(
        ev: EventDefinition,
        name: String,
        eventValues: EventValues,
    ): String {
        val indexed = ev.getTopicIndex(name)
        val nonIndexed = ev.getNonIndexedIndex(name)
        return if (indexed >= 0) {
            eventValues.indexedValues[indexed].value.toString()
        } else {
            eventValues.nonIndexedValues[nonIndexed].value.toString()
        }
    }

    @JvmStatic
    fun getTokenId(ev: EventDefinition, log: EthLog.LogResult<*>): BigInteger {
        val filterTopicValue = ev.filterTopicValue
        return if (filterTopicValue == "tokenId") {
            val tokenIdStr = getTopicVal(ev, log)
            if (tokenIdStr.startsWith("0x")) {
                Numeric.toBigInt(tokenIdStr)
            } else {
                BigInteger(tokenIdStr)
            }
        } else {
            BigInteger.ZERO
        }
    }
}
