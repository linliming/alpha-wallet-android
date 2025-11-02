package com.alphawallet.app.di

import com.alphawallet.app.entity.AnalyticsProperties
import com.alphawallet.app.interact.ChangeTokenEnableInteract
import com.alphawallet.app.interact.CreateTransactionInteract
import com.alphawallet.app.interact.DeleteWalletInteract
import com.alphawallet.app.interact.ExportWalletInteract
import com.alphawallet.app.interact.FetchTokensInteract
import com.alphawallet.app.interact.FetchTransactionsInteract
import com.alphawallet.app.interact.FetchWalletsInteract
import com.alphawallet.app.interact.FindDefaultNetworkInteract
import com.alphawallet.app.interact.GenericWalletInteract
import com.alphawallet.app.interact.ImportWalletInteract
import com.alphawallet.app.interact.MemPoolInteract
import com.alphawallet.app.interact.SetDefaultWalletInteract
import com.alphawallet.app.interact.SignatureGenerateInteract
import com.alphawallet.app.repository.CurrencyRepository
import com.alphawallet.app.repository.CurrencyRepositoryType
import com.alphawallet.app.repository.EthereumNetworkRepositoryType
import com.alphawallet.app.repository.LocaleRepository
import com.alphawallet.app.repository.LocaleRepositoryType
import com.alphawallet.app.repository.PreferenceRepositoryType
import com.alphawallet.app.repository.TokenRepositoryType
import com.alphawallet.app.repository.TransactionRepositoryType
import com.alphawallet.app.repository.WalletRepositoryType
import com.alphawallet.app.router.CoinbasePayRouter
import com.alphawallet.app.router.ExternalBrowserRouter
import com.alphawallet.app.router.HomeRouter
import com.alphawallet.app.router.ImportTokenRouter
import com.alphawallet.app.router.ImportWalletRouter
import com.alphawallet.app.router.ManageWalletsRouter
import com.alphawallet.app.router.MyAddressRouter
import com.alphawallet.app.router.RedeemSignatureDisplayRouter
import com.alphawallet.app.router.SellDetailRouter
import com.alphawallet.app.router.TokenDetailRouter
import com.alphawallet.app.router.TransferTicketDetailRouter
import com.alphawallet.app.service.AnalyticsServiceType
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
/** Module for providing dependencies to viewModels.
 * All bindings of modules from BuildersModule is shifted here as they were injected in activity for ViewModelFactory but not needed in Hilt
 */
class ViewModelModule {
    @Provides
    fun provideFetchWalletInteract(walletRepository: WalletRepositoryType): FetchWalletsInteract {
        return FetchWalletsInteract(walletRepository)
    }

    @Provides
    fun provideSetDefaultAccountInteract(accountRepository: WalletRepositoryType): SetDefaultWalletInteract {
        return SetDefaultWalletInteract(accountRepository)
    }

    @Provides
    fun provideImportAccountRouter(): ImportWalletRouter {
        return ImportWalletRouter()
    }

    @Provides
    fun provideHomeRouter(): HomeRouter {
        return HomeRouter()
    }

    @Provides
    fun provideFindDefaultNetworkInteract(
        networkRepository: EthereumNetworkRepositoryType
    ): FindDefaultNetworkInteract {
        return FindDefaultNetworkInteract(networkRepository)
    }

    @Provides
    fun provideImportWalletInteract(
        walletRepository: WalletRepositoryType
    ): ImportWalletInteract {
        return ImportWalletInteract(walletRepository)
    }

    @Provides
    fun externalBrowserRouter(): ExternalBrowserRouter {
        return ExternalBrowserRouter()
    }

    @Provides
    fun provideFetchTransactionsInteract(
        transactionRepository: TransactionRepositoryType,
        tokenRepositoryType: TokenRepositoryType
    ): FetchTransactionsInteract {
        return FetchTransactionsInteract(transactionRepository, tokenRepositoryType)
    }

    @Provides
    fun provideCreateTransactionInteract(
        transactionRepository: TransactionRepositoryType,
        analyticsService: AnalyticsServiceType<AnalyticsProperties?>
    ): CreateTransactionInteract {
        return CreateTransactionInteract(transactionRepository, analyticsService)
    }

    @Provides
    fun provideMyAddressRouter(): MyAddressRouter {
        return MyAddressRouter()
    }

    @Provides
    fun provideCoinbasePayRouter(): CoinbasePayRouter {
        return CoinbasePayRouter()
    }

    @Provides
    fun provideFetchTokensInteract(tokenRepository: TokenRepositoryType): FetchTokensInteract {
        return FetchTokensInteract(tokenRepository)
    }

    @Provides
    fun provideSignatureGenerateInteract(walletRepository: WalletRepositoryType): SignatureGenerateInteract {
        return SignatureGenerateInteract(walletRepository)
    }

    @Provides
    fun provideMemPoolInteract(tokenRepository: TokenRepositoryType): MemPoolInteract {
        return MemPoolInteract(tokenRepository)
    }

    @Provides
    fun provideTransferTicketRouter(): TransferTicketDetailRouter {
        return TransferTicketDetailRouter()
    }

    @Provides
    fun provideLocaleRepository(preferenceRepository: PreferenceRepositoryType): LocaleRepositoryType {
        return LocaleRepository(preferenceRepository)
    }

    @Provides
    fun provideCurrencyRepository(preferenceRepository: PreferenceRepositoryType): CurrencyRepositoryType {
        return CurrencyRepository(preferenceRepository)
    }

    @Provides
    fun provideErc20DetailRouterRouter(): TokenDetailRouter {
        return TokenDetailRouter()
    }

    @Provides
    fun provideGenericWalletInteract(walletRepository: WalletRepositoryType): GenericWalletInteract {
        return GenericWalletInteract(walletRepository)
    }

    @Provides
    fun provideChangeTokenEnableInteract(tokenRepository: TokenRepositoryType): ChangeTokenEnableInteract {
        return ChangeTokenEnableInteract(tokenRepository)
    }

    @Provides
    fun provideManageWalletsRouter(): ManageWalletsRouter {
        return ManageWalletsRouter()
    }

    @Provides
    fun provideSellDetailRouter(): SellDetailRouter {
        return SellDetailRouter()
    }

    @Provides
    fun provideDeleteAccountInteract(
        accountRepository: WalletRepositoryType
    ): DeleteWalletInteract {
        return DeleteWalletInteract(accountRepository)
    }

    @Provides
    fun provideExportWalletInteract(
        walletRepository: WalletRepositoryType
    ): ExportWalletInteract {
        return ExportWalletInteract(walletRepository)
    }

    @Provides
    fun provideImportTokenRouter(): ImportTokenRouter {
        return ImportTokenRouter()
    }

    @Provides
    fun provideRedeemSignatureDisplayRouter(): RedeemSignatureDisplayRouter {
        return RedeemSignatureDisplayRouter()
    }
}
