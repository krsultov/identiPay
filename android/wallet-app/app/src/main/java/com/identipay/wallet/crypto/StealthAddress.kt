package com.identipay.wallet.crypto

import java.math.BigInteger
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class StealthOutput(
    val ephemeralPubkey: ByteArray,  // R = r*G (32 bytes, X25519 public key)
    val stealthAddress: String,      // 0x-prefixed Sui address
    val viewTag: Int,                // 0-255
    val stealthPubkey: ByteArray,    // K_stealth (32 bytes, Ed25519 compressed)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StealthOutput) return false
        return ephemeralPubkey.contentEquals(other.ephemeralPubkey) &&
                stealthAddress == other.stealthAddress &&
                viewTag == other.viewTag &&
                stealthPubkey.contentEquals(other.stealthPubkey)
    }
    override fun hashCode(): Int {
        var result = ephemeralPubkey.contentHashCode()
        result = 31 * result + stealthAddress.hashCode()
        result = 31 * result + viewTag
        result = 31 * result + stealthPubkey.contentHashCode()
        return result
    }
}

data class ScanResult(
    val stealthAddress: String,
    val stealthPubkey: ByteArray,
    val stealthPrivateKey: ByteArray,  // k_spend + s mod L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScanResult) return false
        return stealthAddress == other.stealthAddress &&
                stealthPubkey.contentEquals(other.stealthPubkey) &&
                stealthPrivateKey.contentEquals(other.stealthPrivateKey)
    }
    override fun hashCode(): Int {
        var result = stealthAddress.hashCode()
        result = 31 * result + stealthPubkey.contentHashCode()
        result = 31 * result + stealthPrivateKey.contentHashCode()
        return result
    }
}

/**
 * Stealth address derivation matching backend/src/services/stealth.service.ts exactly.
 *
 * Protocol:
 * 1. Generate ephemeral X25519 keypair (r, R = r*G)
 * 2. ECDH: shared = x25519(r, K_view)
 * 3. Scalar: s = SHA-256(shared || "identipay-stealth-v1")
 * 4. Stealth pubkey: K_stealth = K_spend + s*G (Ed25519 point addition)
 * 5. Sui address: BLAKE2b-256(0x00 || K_stealth)
 * 6. View tag: first byte of shared secret
 */
@Singleton
class StealthAddress @Inject constructor(
    private val x25519Ops: X25519Ops,
) {
    companion object {
        private val DOMAIN_SEPARATOR = "identipay-stealth-v1".toByteArray(Charsets.UTF_8)

        // Ed25519 curve order
        private val L = BigInteger.valueOf(2).pow(252).add(
            BigInteger("27742317777372353535851937790883648493")
        )
    }

    /**
     * Full stealth address derivation from sender side.
     */
    fun derive(
        spendPubkey: ByteArray,
        viewPubkey: ByteArray,
        ephemeralPrivateKey: ByteArray? = null,
    ): StealthOutput {
        val ephPriv = ephemeralPrivateKey ?: run {
            val key = ByteArray(32)
            java.security.SecureRandom().nextBytes(key)
            // Apply X25519 clamping
            key[0] = (key[0].toInt() and 248).toByte()
            key[31] = (key[31].toInt() and 127).toByte()
            key[31] = (key[31].toInt() or 64).toByte()
            key
        }

        // Compute ephemeral public key
        val ephPub = ByteArray(32)
        org.bouncycastle.math.ec.rfc7748.X25519.scalarMultBase(ephPriv, 0, ephPub, 0)

        // ECDH shared secret
        val shared = x25519Ops.sharedSecret(ephPriv, viewPubkey)

        // View tag: first byte of shared secret
        val viewTag = shared[0].toInt() and 0xFF

        // Stealth scalar: SHA-256(shared || domain_separator)
        val scalar = deriveStealthScalar(shared)

        // Reduce scalar mod L (Ed25519 curve order) - little-endian
        val scalarReduced = reduceScalarModL(scalar)

        // Stealth pubkey: K_spend + s*G
        val stealthPubkey = Ed25519Ops.pointAddScalarBase(spendPubkey, scalarReduced)

        // Sui address: BLAKE2b-256(0x00 || K_stealth)
        val stealthAddress = SuiAddress.fromPubkey(stealthPubkey)

        return StealthOutput(
            ephemeralPubkey = ephPub,
            stealthAddress = stealthAddress,
            viewTag = viewTag,
            stealthPubkey = stealthPubkey,
        )
    }

    /**
     * Receiver-side: check if an announcement is addressed to us.
     * Returns null if not ours, or a ScanResult with the stealth private key if matched.
     */
    fun scan(
        viewPrivateKey: ByteArray,
        spendPrivateKey: ByteArray,
        spendPubkey: ByteArray,
        ephemeralPubkey: ByteArray,
        announcedViewTag: Int,
        announcedStealthAddress: String,
    ): ScanResult? {
        val shared = x25519Ops.sharedSecret(viewPrivateKey, ephemeralPubkey)
        val viewTag = shared[0].toInt() and 0xFF

        // Fast filter: check view tag first (256x speedup)
        if (viewTag != announcedViewTag) return null

        // Full derivation
        val scalar = deriveStealthScalar(shared)
        val scalarReduced = reduceScalarModL(scalar)
        val stealthPubkey = Ed25519Ops.pointAddScalarBase(spendPubkey, scalarReduced)
        val stealthAddress = SuiAddress.fromPubkey(stealthPubkey)

        if (stealthAddress != announcedStealthAddress) return null

        // Derive stealth private key: k_stealth = k_spend_scalar + s (mod L)
        // spendPrivateKey is a seed — extract the actual Ed25519 scalar via SHA-512 + clamp
        val (actualSpendScalar, _) = Ed25519Ops.expandSeed(spendPrivateKey)
        val spendScalar = BigInteger(1, actualSpendScalar.reversedArray())
        val sScalar = BigInteger(1, scalarReduced.reversedArray())
        val stealthScalar = spendScalar.add(sScalar).mod(L)
        val stealthPrivKey = ByteArray(32)
        val scalarBytes = stealthScalar.toByteArray()
        for (i in scalarBytes.indices) {
            val destIdx = scalarBytes.size - 1 - i
            if (destIdx < 32) {
                stealthPrivKey[scalarBytes.size - 1 - i] = scalarBytes[i]
            }
        }

        return ScanResult(
            stealthAddress = stealthAddress,
            stealthPubkey = stealthPubkey,
            stealthPrivateKey = stealthPrivKey,
        )
    }

    private fun deriveStealthScalar(sharedSecret: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(sharedSecret)
        md.update(DOMAIN_SEPARATOR)
        return md.digest()
    }

    /**
     * Reduce a 32-byte scalar mod L (Ed25519 curve order).
     * Input is treated as little-endian (matching TypeScript bytesToBigInt).
     */
    private fun reduceScalarModL(scalar: ByteArray): ByteArray {
        // Convert from little-endian bytes to BigInteger
        val reversed = scalar.reversedArray()
        val value = BigInteger(1, reversed)
        val reduced = value.mod(L)

        // Convert back to 32-byte little-endian
        val result = ByteArray(32)
        val reducedBytes = reduced.toByteArray()
        // BigInteger is big-endian, we need little-endian
        for (i in reducedBytes.indices) {
            val destIdx = reducedBytes.size - 1 - i
            if (destIdx < 32) {
                result[reducedBytes.size - 1 - i] = reducedBytes[i]
            }
        }
        return result
    }
}
