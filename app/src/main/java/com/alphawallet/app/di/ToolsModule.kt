package com.alphawallet.app.di

import android.content.Context
import com.alphawallet.app.C
import com.alphawallet.app.interact.WalletConnectInteract
import com.alphawallet.app.repository.PreferenceRepositoryType
import com.alphawallet.app.service.GasService
import com.alphawallet.app.service.RealmManager
import com.alphawallet.app.walletconnect.AWWalletConnectClient
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class ToolsModule {
    @Singleton
    @Provides
    fun provideGson(): Gson {
        return Gson()
    }

    @Singleton
    @Provides
    fun okHttpClient(): OkHttpClient {
        return OkHttpClient.Builder() //.addInterceptor(new LogInterceptor())
            .connectTimeout(C.CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(C.READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(C.WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    @Singleton
    @Provides
    fun provideRealmManager(): RealmManager {
        return RealmManager()
    }

    @Singleton
    @Provides
    fun provideAWWalletConnectClient(
        @ApplicationContext context: Context,
        walletConnectInteract: WalletConnectInteract,
        preferenceRepositoryType: PreferenceRepositoryType,
        gasService: GasService
    ): AWWalletConnectClient {
        return AWWalletConnectClient(
            context,
            walletConnectInteract,
            preferenceRepositoryType,
            gasService
        )
    }
}
