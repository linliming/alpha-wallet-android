package com.alphawallet.app.entity

/**
 * Created by James on 9/06/2019.
 * Stormbird in Sydney
 */
interface AuthenticationCallback {
    fun authenticatePass(callbackId: Operation?)
    fun authenticateFail(fail: String?, failType: AuthenticationFailType?, callbackId: Operation?)
    fun legacyAuthRequired(callbackId: Operation?, dialogTitle: String?, desc: String?)
}
