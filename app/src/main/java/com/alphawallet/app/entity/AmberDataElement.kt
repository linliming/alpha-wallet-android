package com.alphawallet.app.entity

class AmberDataElement {
    var changeInPrice: Double = 0.0
    var changeInPriceHourly: Double = 0.0
    var changeInPriceDaily: Double = 0.0
    var changeInPriceWeekly: Double = 0.0
    var currentPrice: Double = 0.0
    var name: String? = null
    var address: String? = null
    var blockchain: AmberDataBlockchainElement? = null
    var symbol: String? = null
    var specifications: Array<String> = TODO()
}
