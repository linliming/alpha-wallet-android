package com.alphawallet.app.entity

import android.text.TextUtils
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Parses Ethereum URIs/QR payloads (eg. `ethereum:0x123...@1/transfer?value=1&gas=21000`) to a structured `QRResult`.
 */
class EthereumProtocolParser {
    companion object {
        const val ADDRESS_LENGTH = 42
    }

    /**
     * Parses a protocol/data pair into a [QRResult], or returns `null` if the payload is invalid.
     */
    fun readProtocol(protocol: String, data: String): QRResult? {
        val args = mutableListOf<EthTypeParam>()
        var readState = ParseState.ADDRESS
        var type: String? = null
        return try {
            val stream = tokeniseStream(data)
            if (stream.isEmpty()) return null

            val address = stream.first()
            if (address.type != DataType.STRING ||
                address.value.isNullOrEmpty() ||
                (address.value.startsWith("0x") && address.value.length != ADDRESS_LENGTH)
            ) {
                return null
            }

            val result = QRResult(protocol, address.value)

            for (item in stream) {
                when (item.type) {
                    DataType.SLASH -> readState = ParseState.FUNCTION
                    DataType.QUESTION -> Unit
                    DataType.AT -> readState = ParseState.CHAIN_ID
                    DataType.AMPERSAND -> readState = ParseState.READ_DIRECTIVE
                    DataType.STRING -> when (readState) {
                        ParseState.ADDRESS -> readState = ParseState.READ_DIRECTIVE
                        ParseState.FUNCTION -> {
                            result.setFunction(item.value ?: "")
                            readState = ParseState.READ_TYPE
                        }
                        ParseState.READ_PARAM_VALUE -> {
                            type?.let { args.add(EthTypeParam(it, item.value.orEmpty())) }
                            readState = ParseState.READ_DIRECTIVE
                            type = null
                        }
                        ParseState.VALUE -> {
                            result.weiValue = item.getValueBI()
                            readState = ParseState.READ_DIRECTIVE
                        }
                        ParseState.GAS_PRICE -> {
                            result.gasPrice = item.getValueBI()
                            readState = ParseState.READ_DIRECTIVE
                        }
                        ParseState.GAS_LIMIT -> {
                            result.gasLimit = item.getValueBI()
                            readState = ParseState.READ_DIRECTIVE
                        }
                        ParseState.CHAIN_ID -> {
                            result.chainId = item.getValueBI().toLong()
                            readState = ParseState.READ_DIRECTIVE
                        }
                        ParseState.READ_TYPE -> {
                            type = item.value
                            readState = ParseState.READ_PARAM_VALUE
                        }
                        ParseState.READ_DIRECTIVE -> {
                            readState = interpretDirective(item)
                            if (readState == ParseState.READ_PARAM_VALUE) {
                                type = item.value
                            }
                        }
                        ParseState.ERROR -> throw IllegalArgumentException("Invalid QR code segment: ${item.value}")
                    }
                    else -> Unit
                }
            }

            result.createFunctionPrototype(args)
            result
        } catch (e: Exception) {
            Timber.e(e)
            null
        }
    }

    /** Translates directive keywords (eg. `value`, `gasLimit`) into the corresponding [ParseState]. */
    private fun interpretDirective(item: DataItem): ParseState =
        when (item.value) {
            "value" -> ParseState.VALUE
            "gasPrice" -> ParseState.GAS_PRICE
            "gasLimit" -> ParseState.GAS_LIMIT
            else -> ParseState.READ_PARAM_VALUE
        }

    /** Splits the QR payload into typed tokens ready for state-machine parsing. */
    private fun tokeniseStream(data: String): List<DataItem> {
        val stream = mutableListOf<DataItem>()
        val tokens = data.split("(?=[/&@?=])".toRegex())
        for (item in tokens) {
            if (TextUtils.isEmpty(item)) continue
            when (item[0]) {
                '@' -> {
                    stream.add(DataItem(DataType.AT))
                    addString(stream, item)
                }
                '/' -> {
                    stream.add(DataItem(DataType.SLASH))
                    addString(stream, item)
                }
                '?' -> {
                    stream.add(DataItem(DataType.QUESTION))
                    addString(stream, item)
                }
                '=' -> {
                    stream.add(DataItem(DataType.EQUAL))
                    addString(stream, item)
                }
                '&' -> {
                    stream.add(DataItem(DataType.AMPERSAND))
                    addString(stream, item)
                }
                else -> stream.add(DataItem(item))
            }
        }
        return stream
    }

    /** Appends the remaining substring (minus the directive delimiter) as a string token. */
    private fun addString(stream: MutableList<DataItem>, item: String) {
        if (item.length > 1) {
            stream.add(DataItem(item.substring(1)))
        }
    }

    private class DataItem {
        val type: DataType
        val value: String?

        constructor(type: DataType) {
            this.type = type
            this.value = null
        }

        constructor(value: String) {
            this.type = DataType.STRING
            this.value = value
        }

        /** Parses the stored string into a [BigInteger], returning zero when the value is invalid. */
        fun getValueBI(): BigInteger {
            return try {
                BigDecimal(value).toBigInteger()
            } catch (_: Exception) {
                BigInteger.ZERO
            }
        }
    }

    private enum class DataType {
        SLASH, AT, QUESTION, EQUAL, AMPERSAND, STRING
    }

    private enum class ParseState {
        ADDRESS,
        VALUE,
        FUNCTION,
        GAS_PRICE,
        GAS_LIMIT,
        CHAIN_ID,
        READ_TYPE,
        READ_PARAM_VALUE,
        READ_DIRECTIVE,
        ERROR
    }
}
