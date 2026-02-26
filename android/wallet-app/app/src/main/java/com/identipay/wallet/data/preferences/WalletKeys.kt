package com.identipay.wallet.data.preferences

import com.identipay.wallet.crypto.Ed25519KeyPair
import com.identipay.wallet.crypto.SeedManager
import com.identipay.wallet.crypto.X25519KeyPair
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages wallet key references and derivation.
 * Keys are derived on-demand from the encrypted seed.
 */
@Singleton
class WalletKeys @Inject constructor(
    private val seedManager: SeedManager,
) {
    fun hasKeys(): Boolean = seedManager.hasSeed()

    fun getSpendKeyPair(): Ed25519KeyPair = seedManager.deriveSpendKeyPair()

    fun getViewKeyPair(): X25519KeyPair = seedManager.deriveViewKeyPair()

    fun getSpendPublicKey(): ByteArray = getSpendKeyPair().publicKey

    fun getViewPublicKey(): ByteArray = getViewKeyPair().publicKey
}
