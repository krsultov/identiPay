package com.identipay.wallet.crypto

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signs intent hashes with an Ed25519 stealth private key.
 */
@Singleton
class IntentSigner @Inject constructor() {

    /**
     * Sign an intent hash with the buyer's stealth private key.
     *
     * @param intentHashHex The intent hash as a lowercase hex string
     * @param stealthPrivKey The stealth private key (32-byte raw Ed25519 scalar)
     * @return 64-byte Ed25519 signature
     */
    fun sign(intentHashHex: String, stealthPrivKey: ByteArray): ByteArray {
        val hashBytes = hexToBytes(intentHashHex)
        val publicKey = Ed25519Ops.publicKeyFromScalar(stealthPrivKey)
        return Ed25519Ops.signWithScalar(hashBytes, stealthPrivKey, publicKey)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.startsWith("0x")) hex.substring(2) else hex
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
