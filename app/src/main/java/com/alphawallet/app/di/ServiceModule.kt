package com.alphawallet.app.di

import com.alphawallet.app.interact.GenericWalletInteract
import com.alphawallet.app.repository.WalletRepositoryType
import com.alphawallet.app.router.TokenDetailRouter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent

@Module
@InstallIn(ServiceComponent::class)
/** A module to provide dependencies to services  */
class ServiceModule {
    @Provides
    fun provideGenericWalletInteract(walletRepository: WalletRepositoryType): GenericWalletInteract {
        return GenericWalletInteract(walletRepository)
    }

    @Provides
    fun provideTokenDetailRouter(): TokenDetailRouter {
        return TokenDetailRouter()
    }
}
