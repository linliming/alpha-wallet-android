package com.alphawallet.app.entity

import android.content.Context
import com.alphawallet.app.service.KeyService.AuthenticationLevel
import com.alphawallet.app.service.KeyService.UpgradeKeyResult

interface CreateWalletCallbackInterface {
    fun HDKeyCreated(address: String?, ctx: Context?, level: AuthenticationLevel?)
    fun keyFailure(message: String?)
    fun cancelAuthentication()
    fun fetchMnemonic(mnemonic: String?)
    fun keyUpgraded(result: UpgradeKeyResult?) {}
}
