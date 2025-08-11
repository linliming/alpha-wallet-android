package com.alphawallet.token.entity

/**
 * Created by James on 2/05/2019.
 * Stormbird in Sydney
 */
open class ContractInfo {
    @JvmField
    val contractInterface: String

    @JvmField
    val addresses: MutableMap<Long, List<String>> = HashMap()

    constructor(contractType: String, addresses: Map<Long, List<String>>) {
        this.contractInterface = contractType
        this.addresses.putAll(addresses)
    }

    constructor(contractType: String) {
        this.contractInterface = contractType
    }

    fun hasContractTokenScript(
        chainId: Long,
        address: String,
    ): Boolean {
        val addrs = addresses[chainId]
        return addrs != null && addrs.contains(address)
    }

    fun getfirstChainId(): Long =
        if (addresses.keys.size > 0) {
            addresses.keys.iterator().next()
        } else {
            0
        }

    val firstAddress: String
        get() {
            val chainId = getfirstChainId()
            return if (addresses[chainId]!!.size > 0) addresses[chainId]!![0] else ""
        }
}
