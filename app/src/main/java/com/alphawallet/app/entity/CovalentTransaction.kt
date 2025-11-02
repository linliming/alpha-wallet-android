package com.alphawallet.app.entity

import com.alphawallet.app.util.Utils
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Locale

class CovalentTransaction {
    @JvmField
    var block_signed_at: String? = null
    @JvmField
    var block_height: String? = null
    @JvmField
    var tx_hash: String? = null
    @JvmField
    var tx_offset: Int = 0
    @JvmField
    var successful: Boolean = false
    @JvmField
    var from_address: String? = null
    @JvmField
    var from_address_label: String? = null
    @JvmField
    var to_address: String? = null
    @JvmField
    var to_address_label: String? = null
    @JvmField
    var value: String? = null
    @JvmField
    var value_quote: Double = 0.0
    @JvmField
    var gas_offered: Long = 0
    @JvmField
    var gas_spent: String? = null
    @JvmField
    var gas_price: String? = null
    @JvmField
    var input: String? = null
    @JvmField
    var gas_quote: Double = 0.0
    @JvmField
    var gas_quote_rate: Double = 0.0
    @JvmField
    var log_events: Array<LogEvent>? = null

    inner class LogEvent {
        @JvmField
        var sender_contract_decimals: Int = 0
        @JvmField
        var sender_name: String? = null
        @JvmField
        var sender_address: String? = null
        @JvmField
        var sender_contract_ticker_symbol: String? = null
        @JvmField
        var decoded: LogDecode? = null
        @JvmField
        var raw_log_topics: Array<String>? = null

        @Throws(Exception::class)
        fun getParams(): Map<String, Param> {
            val params: MutableMap<String, Param> = HashMap()
            val decodedParams = decoded?.params ?: return params
            val topics = raw_log_topics ?: emptyArray()

            decodedParams.forEachIndexed { index, logParam ->
                val name = logParam.name ?: return@forEachIndexed
                val rawLogValue = topics.getOrNull(index + 1) ?: ""
                val rawValue = when {
                    logParam.value.isNullOrEmpty() || logParam.value == "null" -> rawLogValue
                    else -> logParam.value!!
                }

                val param = Param().apply {
                    type = logParam.type
                    value = rawValue
                    if (!logParam.type.isNullOrEmpty() &&
                        (logParam.type!!.startsWith("uint") || logParam.type!!.startsWith("int"))
                    ) {
                        valueBI = Utils.stringToBigInteger(rawValue)
                    }
                }

                params[name] = param
            }

            return params
        }
    }

    inner class Param {
        @JvmField
        var type: String? = null
        @JvmField
        var value: String? = null
        @JvmField
        var valueBI: BigInteger? = null
    }

    inner class LogDecode {
        @JvmField
        var name: String? = null
        @JvmField
        var signature: String? = null
        @JvmField
        var params: Array<LogParam>? = null
    }

    inner class LogParam {
        @JvmField
        var name: String? = null
        @JvmField
        var type: String? = null
        @JvmField
        var value: String? = null
    }

    fun determineContractAddress(): String {
        val events = log_events ?: return ""
        for (logEvent in events) {
            val sender = logEvent.sender_address
            if (sender != null) {
                return sender
            }
        }
        return ""
    }

    @Throws(Exception::class)
    private fun getEtherscanTransferEvent(logEvent: LogEvent?): EtherscanEvent? {
        if (logEvent?.decoded?.name != "Transfer") return null

        val event = EtherscanEvent()
        event.tokenDecimal = logEvent.sender_contract_decimals.toString()
        event.timeStamp = format.parse(block_signed_at!!).time / 1000
        event.hash = tx_hash
        event.nonce = 0
        event.tokenName = logEvent.sender_name
        event.tokenSymbol = logEvent.sender_contract_ticker_symbol
        event.contractAddress = logEvent.sender_address
        event.blockNumber = block_height

        val logParams = logEvent.getParams()
        event.from = logParams["from"]?.value ?: ""
        event.to = logParams["to"]?.value ?: ""
        event.tokenID = logParams["tokenId"]?.valueBI?.toString() ?: ""
        event.value = logParams["value"]?.valueBI?.toString() ?: ""

        event.gasUsed = gas_spent
        event.gasPrice = gas_price
        event.gas = gas_offered.toString()

        return event
    }

    @Throws(Exception::class)
    private fun fetchRawTransaction(info: NetworkInfo): Transaction {
        val transactionTime = format.parse(block_signed_at!!).time / 1000
        return Transaction(this, info.chainId, transactionTime)
    }

    companion object {
        private val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

        @JvmStatic
        fun toEtherscanTransactions(
            transactions: Array<CovalentTransaction>,
            info: NetworkInfo
        ): Array<EtherscanTransaction> {
            val converted = ArrayList<EtherscanTransaction>()
            for (transaction in transactions) {
                try {
                    val rawTransaction = transaction.fetchRawTransaction(info)
                    converted.add(EtherscanTransaction(transaction, rawTransaction))
                } catch (_: Exception) {
                    // ignore malformed entries
                }
            }
            return converted.toTypedArray()
        }

        @JvmStatic
        fun toEtherscanEvents(transactions: Array<CovalentTransaction>): Array<EtherscanEvent> {
            val converted = ArrayList<EtherscanEvent>()
            for (transaction in transactions) {
                try {
                    val events = transaction.log_events ?: continue
                    for (logEvent in events) {
                        if (logEvent.decoded?.name == "Transfer") {
                            transaction.getEtherscanTransferEvent(logEvent)?.let {
                                converted.add(it)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // ignore malformed entries
                }
            }
            return converted.toTypedArray()
        }

        @JvmStatic
        fun toRawEtherscanTransactions(
            transactions: Array<CovalentTransaction>,
            info: NetworkInfo
        ): Array<EtherscanTransaction?> {
            val converted = ArrayList<EtherscanTransaction>()
            for (transaction in transactions) {
                try {
                    val events = transaction.log_events
                    if (events == null) {
                        val rawTransaction = transaction.fetchRawTransaction(info)
                        converted.add(EtherscanTransaction(transaction, rawTransaction))
                    } else {
                        var hasTransfer = false
                        for (logEvent in events) {
                            if (logEvent.decoded?.name == "Transfer") {
                                hasTransfer = true
                                break
                            }
                        }

                        if (!hasTransfer) {
                            val rawTransaction = transaction.fetchRawTransaction(info)
                            converted.add(EtherscanTransaction(transaction, rawTransaction))
                        }
                    }
                } catch (_: Exception) {
                    // ignore malformed entries
                }
            }
            return converted.toTypedArray()
        }
    }
}
