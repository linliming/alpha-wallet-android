package com.alphawallet.app.entity.coinbasepay

class DestinationWallet
    (@field:Transient val type: Type, var address: String, list: List<String>?) {
    var blockchains: List<String>? = null
    var assets: List<String>? = null

    init {
        if (type == Type.ASSETS) {
            this.assets = list
        } else {
            this.blockchains = list
        }
    }

    enum class Type {
        ASSETS,
        BLOCKCHAINS
    }
}
