package com.alphawallet.app.entity

import com.alphawallet.app.repository.OnRampRepository

class OnRampContract {
    @JvmField
    var symbol: String
    var provider: String

    constructor(symbol: String) {
        this.symbol = symbol
        this.provider = OnRampRepository.DEFAULT_PROVIDER
    }

    constructor() {
        this.symbol = ""
        this.provider = OnRampRepository.DEFAULT_PROVIDER
    }
}
