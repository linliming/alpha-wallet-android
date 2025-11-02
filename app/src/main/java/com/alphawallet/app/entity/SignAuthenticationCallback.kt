package com.alphawallet.app.entity

import com.alphawallet.hardware.SignatureFromKey

/**
 * Created by James on 21/07/2019.
 * Stormbird in Sydney
 */
interface SignAuthenticationCallback {
    fun gotAuthorisation(gotAuth: Boolean)

    fun createdKey(keyAddress: String?) {
    }

    fun cancelAuthentication()

    fun gotSignature(signature: SignatureFromKey?)

    fun signingError(error: String?) {
    } //Handle signing error from hardware card
}
