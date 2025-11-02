package com.alphawallet.app.entity

/**
 * Created by James on 19/07/2019.
 * Stormbird in Sydney
 */
interface PinAuthenticationCallbackInterface {
    fun completeAuthentication(taskCode: Operation?)
    fun failedAuthentication(taskCode: Operation?)
}
