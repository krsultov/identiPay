package com.identipay.wallet.crypto

import org.bouncycastle.crypto.digests.Blake2bDigest

/**
 * Sui address derivation from Ed25519 public key.
 * Address = BLAKE2b-256(0x00 || pubkey)
 */
object SuiAddress {

    /**
     * Derive a Sui address from an Ed25519 public key.
     * @param pubkey 32-byte Ed25519 public key
     * @return 0x-prefixed hex string (66 chars)
     */
    fun fromPubkey(pubkey: ByteArray): String {
        require(pubkey.size == 32) { "Public key must be 32 bytes" }

        // Prepend 0x00 flag byte (Ed25519 scheme)
        val flagged = ByteArray(33)
        flagged[0] = 0x00
        System.arraycopy(pubkey, 0, flagged, 1, 32)

        // BLAKE2b-256
        val digest = Blake2bDigest(256)
        digest.update(flagged, 0, flagged.size)
        val hash = ByteArray(32)
        digest.doFinal(hash, 0)

        return "0x" + hash.joinToString("") { "%02x".format(it) }
    }
}
