package com.alphawallet.app.interact

import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.repository.TokenRepositoryType
import com.alphawallet.token.entity.ContractAddress
import io.reactivex.Completable

class ChangeTokenEnableInteract
    (private val tokenRepository: TokenRepositoryType) {

    fun setEnable(wallet: Wallet?, cAddr: ContractAddress?, enabled: Boolean): Completable {
        tokenRepository.setEnable(wallet, cAddr, enabled)
        tokenRepository.setVisibilityChanged(wallet, cAddr)
        return Completable.fromAction {}
    }
}
