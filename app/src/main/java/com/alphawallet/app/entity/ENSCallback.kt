package com.alphawallet.app.entity

/**
 * Created by James on 4/12/2018.
 * Stormbird in Singapore
 */
interface ENSCallback {
    fun ENSComplete()
    fun displayCheckingDialog(shouldShow: Boolean)
    fun ENSResolved(address: String?, ens: String?)
    fun ENSName(name: String?)
}
