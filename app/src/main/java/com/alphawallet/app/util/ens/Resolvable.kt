package com.alphawallet.app.util.ens

interface Resolvable {
    fun resolve(ensName: String?): String?
}
