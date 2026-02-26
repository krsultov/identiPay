package com.identipay.wallet

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

@HiltAndroidApp
class IdentiPayApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Replace Android's stripped-down BouncyCastle with the full version.
        // This is required for PACE authentication which uses brainpool EC curves
        // (brainpoolP256r1, etc.) not supported by Android's Conscrypt/BoringSSL.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
