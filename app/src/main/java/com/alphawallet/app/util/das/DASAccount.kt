package com.alphawallet.app.util.das

import android.text.TextUtils

/**
 * Created by JB on 17/09/2021.
 */
class DASAccount {
    var owner_address_chain: String? = null
    var owner_address: String? = null
    lateinit var records: Array<DASRecord>

    val ethOwner: String?
        get() {
            return if (!TextUtils.isEmpty(owner_address_chain) && owner_address_chain.equals(
                    "ETH",
                    ignoreCase = true
                )
            ) {
                owner_address
            } else {
                null
            }
        }
}
