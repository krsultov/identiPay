package com.identipay.wallet.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class X25519Ops @Inject constructor(
    private val lazySodium: LazySodiumAndroid,
) {
    /**
     * Compute X25519 ECDH shared secret.
     */
    fun sharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        require(publicKey.size == 32) { "Public key must be 32 bytes" }
        val shared = ByteArray(32)
        lazySodium.getSodium().crypto_scalarmult(shared, privateKey, publicKey)
        return shared
    }

    companion object {
        /**
         * Generate an X25519 keypair from a 32-byte seed.
         */
        fun keyPairFromSeed(seed: ByteArray): X25519KeyPair {
            require(seed.size == 32) { "Seed must be 32 bytes" }
            val privateKey = seed.copyOf()
            privateKey[0] = (privateKey[0].toInt() and 248).toByte()
            privateKey[31] = (privateKey[31].toInt() and 127).toByte()
            privateKey[31] = (privateKey[31].toInt() or 64).toByte()

            val publicKey = ByteArray(32)
            org.bouncycastle.math.ec.rfc7748.X25519.scalarMultBase(privateKey, 0, publicKey, 0)

            return X25519KeyPair(
                privateKey = privateKey,
                publicKey = publicKey,
            )
        }
    }
}
