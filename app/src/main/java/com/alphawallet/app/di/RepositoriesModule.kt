package com.alphawallet.app.di

import android.content.Context
import com.alphawallet.app.entity.AnalyticsProperties
import com.alphawallet.app.repository.CoinbasePayRepository
import com.alphawallet.app.repository.CoinbasePayRepositoryType
import com.alphawallet.app.repository.EthereumNetworkRepository
import com.alphawallet.app.repository.EthereumNetworkRepositoryType
import com.alphawallet.app.repository.OnRampRepository
import com.alphawallet.app.repository.OnRampRepositoryType
import com.alphawallet.app.repository.PreferenceRepositoryType
import com.alphawallet.app.repository.SharedPreferenceRepository
import com.alphawallet.app.repository.SwapRepository
import com.alphawallet.app.repository.SwapRepositoryType
import com.alphawallet.app.repository.TokenLocalSource
import com.alphawallet.app.repository.TokenRepository
import com.alphawallet.app.repository.TokenRepositoryType
import com.alphawallet.app.repository.TokensMappingRepository
import com.alphawallet.app.repository.TokensMappingRepositoryType
import com.alphawallet.app.repository.TokensRealmSource
import com.alphawallet.app.repository.TransactionLocalSource
import com.alphawallet.app.repository.TransactionRepository
import com.alphawallet.app.repository.TransactionRepositoryType
import com.alphawallet.app.repository.TransactionsRealmCache
import com.alphawallet.app.repository.WalletDataRealmSource
import com.alphawallet.app.repository.WalletRepository
import com.alphawallet.app.repository.WalletRepositoryType
import com.alphawallet.app.service.AccountKeystoreService
import com.alphawallet.app.service.AlphaWalletNotificationService
import com.alphawallet.app.service.AlphaWalletService
import com.alphawallet.app.service.AnalyticsService
import com.alphawallet.app.service.AnalyticsServiceType
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.service.GasService
import com.alphawallet.app.service.IPFSService
import com.alphawallet.app.service.IPFSServiceType
import com.alphawallet.app.service.KeyService
import com.alphawallet.app.service.KeystoreAccountService
import com.alphawallet.app.service.NotificationService
import com.alphawallet.app.service.OpenSeaService
import com.alphawallet.app.service.RealmManager
import com.alphawallet.app.service.SwapService
import com.alphawallet.app.service.TickerService
import com.alphawallet.app.service.TokensService
import com.alphawallet.app.service.TransactionNotificationService
import com.alphawallet.app.service.TransactionsNetworkClient
import com.alphawallet.app.service.TransactionsNetworkClientType
import com.alphawallet.app.service.TransactionsService
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class RepositoriesModule {
    @Singleton
    @Provides
    fun providePreferenceRepository(@ApplicationContext context: Context): PreferenceRepositoryType {
        return SharedPreferenceRepository(context)
    }

    @Singleton
    @Provides
    fun provideAccountKeyStoreService(
        @ApplicationContext context: Context,
        keyService: KeyService
    ): AccountKeystoreService {
        val file = File(context.filesDir, KeystoreAccountService.KEYSTORE_FOLDER)
        return KeystoreAccountService(file, context.filesDir, keyService)
    }

    @Singleton
    @Provides
    fun provideTickerService(
        httpClient: OkHttpClient,
        sharedPrefs: PreferenceRepositoryType,
        localSource: TokenLocalSource
    ): TickerService {
        return TickerService(httpClient, sharedPrefs, localSource)
    }

    @Singleton
    @Provides
    fun provideEthereumNetworkRepository(
        preferenceRepository: PreferenceRepositoryType,
        @ApplicationContext context: Context
    ): EthereumNetworkRepositoryType {
        return EthereumNetworkRepository(preferenceRepository, context)
    }

    @Singleton
    @Provides
    fun provideWalletRepository(
        preferenceRepositoryType: PreferenceRepositoryType,
        accountKeystoreService: AccountKeystoreService,
        networkRepository: EthereumNetworkRepositoryType,
        walletDataRealmSource: WalletDataRealmSource,
        keyService: KeyService
    ): WalletRepositoryType {
        return WalletRepository(
            preferenceRepositoryType,
            accountKeystoreService,
            networkRepository,
            walletDataRealmSource,
            keyService
        )
    }

    @Singleton
    @Provides
    fun provideTransactionRepository(
        networkRepository: EthereumNetworkRepositoryType,
        accountKeystoreService: AccountKeystoreService,
        inDiskCache: TransactionLocalSource,
        transactionsService: TransactionsService
    ): TransactionRepositoryType {
        return TransactionRepository(
            networkRepository,
            accountKeystoreService,
            inDiskCache,
            transactionsService
        )
    }

    @Singleton
    @Provides
    fun provideOnRampRepository(@ApplicationContext context: Context): OnRampRepositoryType {
        return OnRampRepository(context)
    }

    @Singleton
    @Provides
    fun provideSwapRepository(@ApplicationContext context: Context): SwapRepositoryType {
        return SwapRepository(context)
    }

    @Singleton
    @Provides
    fun provideCoinbasePayRepository(): CoinbasePayRepositoryType {
        return CoinbasePayRepository()
    }

    @Singleton
    @Provides
    fun provideTransactionInDiskCache(realmManager: RealmManager): TransactionLocalSource {
        return TransactionsRealmCache(realmManager)
    }

    @Singleton
    @Provides
    fun provideBlockExplorerClient(
        httpClient: OkHttpClient,
        gson: Gson,
        realmManager: RealmManager
    ): TransactionsNetworkClientType {
        return TransactionsNetworkClient(httpClient, gson, realmManager)
    }

    @Singleton
    @Provides
    fun provideTokenRepository(
        ethereumNetworkRepository: EthereumNetworkRepositoryType,
        tokenLocalSource: TokenLocalSource,
        @ApplicationContext context: Context,
        tickerService: TickerService
    ): TokenRepositoryType {
        return TokenRepository(
            ethereumNetworkRepository,
            tokenLocalSource,
            context,
            tickerService
        )
    }

    @Singleton
    @Provides
    fun provideRealmTokenSource(
        realmManager: RealmManager,
        ethereumNetworkRepository: EthereumNetworkRepositoryType,
        tokensMappingRepository: TokensMappingRepositoryType
    ): TokenLocalSource {
        return TokensRealmSource(realmManager, ethereumNetworkRepository, tokensMappingRepository)
    }

    @Singleton
    @Provides
    fun provideRealmWalletDataSource(realmManager: RealmManager): WalletDataRealmSource {
        return WalletDataRealmSource(realmManager)
    }

    @Singleton
    @Provides
    fun provideTokensServices(
        ethereumNetworkRepository: EthereumNetworkRepositoryType,
        tokenRepository: TokenRepositoryType,
        tickerService: TickerService,
        openseaService: OpenSeaService,
        analyticsService: AnalyticsServiceType<AnalyticsProperties?>,
        client: OkHttpClient
    ): TokensService {
        return TokensService(
            ethereumNetworkRepository,
            tokenRepository,
            tickerService,
            openseaService,
            analyticsService,
            client
        )
    }

    @Singleton
    @Provides
    fun provideIPFSService(client: OkHttpClient): IPFSServiceType {
        return IPFSService(client)
    }

    @Singleton
    @Provides
    fun provideTransactionsServices(
        tokensService: TokensService,
        ethereumNetworkRepositoryType: EthereumNetworkRepositoryType,
        transactionsNetworkClientType: TransactionsNetworkClientType,
        transactionLocalSource: TransactionLocalSource,
        transactionNotificationService: TransactionNotificationService
    ): TransactionsService {
        return TransactionsService(
            tokensService,
            ethereumNetworkRepositoryType,
            transactionsNetworkClientType,
            transactionLocalSource,
            transactionNotificationService
        )
    }

    @Singleton
    @Provides
    fun provideGasService(
        ethereumNetworkRepository: EthereumNetworkRepositoryType,
        client: OkHttpClient,
        realmManager: RealmManager
    ): GasService {
        return GasService(ethereumNetworkRepository, client, realmManager)
    }

    @Singleton
    @Provides
    fun provideOpenseaService(): OpenSeaService {
        return OpenSeaService()
    }

    @Singleton
    @Provides
    fun provideSwapService(): SwapService {
        return SwapService()
    }

    @Singleton
    @Provides
    fun provideFeemasterService(okHttpClient: OkHttpClient, gson: Gson): AlphaWalletService {
        return AlphaWalletService(okHttpClient, gson)
    }

    @Singleton
    @Provides
    fun provideNotificationService(@ApplicationContext ctx: Context): NotificationService {
        return NotificationService(ctx)
    }

    @Singleton
    @Provides
    fun providingAssetDefinitionServices(
        ipfsService: IPFSServiceType,
        @ApplicationContext ctx: Context,
        notificationService: NotificationService,
        realmManager: RealmManager,
        tokensService: TokensService,
        tls: TokenLocalSource,
        alphaService: AlphaWalletService
    ): AssetDefinitionService {
        return AssetDefinitionService(
            ipfsService,
            ctx,
            notificationService,
            realmManager,
            tokensService,
            tls,
            alphaService
        )
    }

    @Singleton
    @Provides
    fun provideKeyService(
        @ApplicationContext ctx: Context,
        analyticsService: AnalyticsServiceType<AnalyticsProperties?>
    ): KeyService {
        return KeyService(ctx, analyticsService)
    }

    @Singleton
    @Provides
    fun provideAnalyticsService(
        @ApplicationContext ctx: Context?,
        preferenceRepository: PreferenceRepositoryType?
    ): AnalyticsServiceType<AnalyticsProperties?> {
        return AnalyticsService<Any?>(ctx, preferenceRepository)
    }

    @Singleton
    @Provides
    fun provideTokensMappingRepository(@ApplicationContext ctx: Context): TokensMappingRepositoryType {
        return TokensMappingRepository(ctx)
    }

    @Singleton
    @Provides
    fun provideTransactionNotificationService(
        @ApplicationContext ctx: Context,
        preferenceRepositoryType: PreferenceRepositoryType
    ): TransactionNotificationService {
        return TransactionNotificationService(ctx, preferenceRepositoryType)
    }

    @Singleton
    @Provides
    fun provideAlphaWalletNotificationService(walletRepository: WalletRepositoryType): AlphaWalletNotificationService {
        return AlphaWalletNotificationService(walletRepository)
    }
}
