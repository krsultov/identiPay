package com.identipay.wallet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideBouncyCastleProvider(): BouncyCastleProvider {
        // Provider is registered at position 1 in IdentiPayApp.onCreate()
        return Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) as BouncyCastleProvider
    }
}
