package com.alphawallet.app.entity

import org.web3j.abi.datatypes.generated.Uint16

class TransferFromEventResponse {
    @JvmField
    var _from: String? = null

    var _to: String? = null

    @JvmField
    var _indices: List<Uint16>? = null
}
