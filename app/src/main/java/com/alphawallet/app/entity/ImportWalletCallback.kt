package com.alphawallet.app.entity

import com.alphawallet.app.entity.cryptokeys.KeyEncodingType
import com.alphawallet.app.service.KeyService.AuthenticationLevel

interface ImportWalletCallback {
    fun walletValidated(address: String?, type: KeyEncodingType?, level: AuthenticationLevel?)
}
