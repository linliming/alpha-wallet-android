package com.alphawallet.app.repository.entity

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmField

open class RealmTransaction : RealmObject() {

    @PrimaryKey
    var hash: String? = null
    var blockNumber: String? = null
    var timeStamp: Long = 0L
    var nonce: Int = 0
    var from: String? = null
    var to: String? = null
    var value: String? = null
    var gas: String? = null
    var gasPrice: String? = null
    var gasUsed: String? = null
    var input: String? = null
    var error: String? = null
    var maxFeePerGas: String? = null
    @RealmField(name = "maxPriorityFee")
    var priorityFee: String? = null
    var chainId: Long = 0L
    var expectedCompletion: Long = 0L
    var contractAddress: String? = null

    fun setMaxPriorityFee(maxPriorityFee: String?) {
        priorityFee = maxPriorityFee
    }

    val isPending: Boolean
        get() = blockNumber.isNullOrEmpty() || blockNumber == "0" || blockNumber == "-2"
}
