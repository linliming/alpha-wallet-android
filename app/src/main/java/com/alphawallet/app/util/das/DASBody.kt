package com.alphawallet.app.util.das

/**
 * Created by JB on 17/09/2021.
 */
class DASBody {
    var jsonrpc: String? = null
    var id: Int = 0
    var result: DASResult? = null
    @JvmField
    val records: MutableMap<String, DASRecord> = HashMap()

    fun buildMap() {
        val accountRecords = result?.data?.account_data?.records ?: return

        for (record in accountRecords) {
            if (record.key!= null){
                records[record.key.orEmpty()] = record
            }
        }
    }

    val ethOwner: String?
        get() = result?.data?.account_data?.ethOwner
}
