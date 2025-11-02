package com.alphawallet.token.entity

import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.regex.Pattern

/**
 * Created by JB on 21/03/2020.
 */
class EventDefinition {
    var contract: ContractInfo? = null
    var attributeName: String? = null //TransactionResult: method
    var type: NamedType? = null
    var filter: String? = null
    var select: String? = null
    var readBlock: BigInteger = BigInteger.ZERO
    var parentAttribute: Attribute? = null
    var activityName: String? = null

    val filterTopicValue: String?
        get() {
            // This regex splits up the "filterArgName=${filterValue}" directive and gets the 'filterValue'
            val m =
                Pattern.compile("\\$\\{([^}]+)\\}").matcher(filter)
            return if (m.find() && m.groupCount() >= 1) m.group(1) else null
        }

    val filterTopicIndex: String
        get() {
            // Get the filter name from the directive and strip whitespace
            val item =
                filter!!.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            return item[0].replace("\\s+".toRegex(), "")
        }

    fun getTopicIndex(filterTopic: String?): Int {
        if (type == null || filterTopic == null) return -1
        return type!!.getTopicIndex(filterTopic)
    }

    fun getSelectIndex(indexed: Boolean): Int {
        var index = 0
        var found = false
        for (label in type!!.getArgNames(indexed)) {
            if (label == select) {
                found = true
                break
            } else {
                index++
            }
        }

        return if (found) index else -1
    }

    val eventChainId: Long
        get() {
            return if (parentAttribute != null && parentAttribute!!.getOriginContract() != null) {
                parentAttribute!!.getOriginContract()!!.addresses.keys.iterator().next()
            } else {
                contract!!.addresses.keys.iterator().next()
            }
        }

    val eventContractAddress: String
        get() {
            val chainId = eventChainId
            val contractAddress =
                if (parentAttribute != null && parentAttribute!!.getOriginContract() != null) {
                    parentAttribute!!.getOriginContract()!!.addresses[chainId]!![0]
                } else {
                    contract!!.addresses[chainId]!![0]
                }

            return contractAddress
        }

    fun getNonIndexedIndex(name: String?): Int {
        if (type == null || name == null) return -1
        return type!!.getNonIndexedIndex(name)
    }

    fun equals(ev: EventDefinition): Boolean {
        return if (contract!!.getfirstChainId() == ev.contract!!.getfirstChainId() && contract!!.firstAddress.equals(
                ev.contract!!.firstAddress, ignoreCase = true
            ) &&
            filter == ev.filter && type!!.name == ev.type!!.name && ((activityName != null && ev.activityName != null && activityName == ev.activityName) ||
                    (attributeName != null && ev.attributeName != null && attributeName == ev.attributeName))
        ) {
            true
        } else {
            false
        }
    }

    val eventKey: String
        get() = getEventKey(
            contract!!.getfirstChainId(),
            contract!!.firstAddress,
            activityName,
            attributeName
        )

    companion object {
        fun getEventKey(
            chainId: Long,
            eventAddress: String,
            activityName: String?,
            attributeName: String?
        ): String {
            val sb = StringBuilder()
            try {
                val digest = MessageDigest.getInstance("MD5")
                digest.update(longToByteArray(chainId))
                digest.update(eventAddress.toByteArray())
                if (activityName != null) digest.update(activityName.toByteArray())
                if (attributeName != null) digest.update(attributeName.toByteArray())

                val bytes = digest.digest()
                for (aByte in bytes) {
                    sb.append(((aByte.toInt() and 0xff) + 0x100).toString(16).substring(1))
                }
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            }

            return sb.toString()
        }

        private fun intToByteArray(a: Int): ByteArray {
            val ret = ByteArray(4)
            ret[3] = (a and 0xFF).toByte()
            ret[2] = ((a shr 8) and 0xFF).toByte()
            ret[1] = ((a shr 16) and 0xFF).toByte()
            ret[0] = ((a shr 24) and 0xFF).toByte()
            return ret
        }

        private fun longToByteArray(a: Long): ByteArray {
            val ret = ByteArray(8)
            ret[7] = (a and 0xFFL).toByte()
            ret[6] = ((a shr 8) and 0xFFL).toByte()
            ret[5] = ((a shr 16) and 0xFFL).toByte()
            ret[4] = ((a shr 24) and 0xFFL).toByte()
            ret[3] = ((a shr 32) and 0xFFL).toByte()
            ret[2] = ((a shr 40) and 0xFFL).toByte()
            ret[1] = ((a shr 48) and 0xFFL).toByte()
            ret[0] = ((a shr 56) and 0xFFL).toByte()
            return ret
        }
    }
}
