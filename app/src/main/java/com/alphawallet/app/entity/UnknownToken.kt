package com.alphawallet.app.entity

/**
 * Created by James on 18/03/2019.
 * Stormbird in Singapore
 */
class UnknownToken
    (var chainId: Long, @JvmField var address: String, @JvmField var isPopular: Boolean) {
    var name: String? = null
}
