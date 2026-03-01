package com.identipay.wallet.crypto

import org.bouncycastle.math.ec.rfc7748.X25519
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted artifact output for on-chain storage.
 */
data class EncryptedArtifact(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val ephemeralPubkey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedArtifact) return false
        return ciphertext.contentEquals(other.ciphertext) &&
                nonce.contentEquals(other.nonce) &&
                ephemeralPubkey.contentEquals(other.ephemeralPubkey)
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ephemeralPubkey.contentHashCode()
        return result
    }
}

/**
 * ECDH + AES-256-GCM artifact encryption per whitepaper section 7.1.
 *
 * encrypt(k_stealth, K_merchant, plaintext):
 *   1. e = SHA256(k_stealth || "identipay-artifact-eph-v1")
 *   2. E = X25519.publicKey(e)  [with clamping]
 *   3. shared = X25519.sharedSecret(e, K_merchant)
 *   4. K_enc = SHA256(shared || "identipay-artifact-enc-v1")
 *   5. nonce = random 12 bytes
 *   6. ciphertext = AES-256-GCM(K_enc, nonce, plaintext)
 *   -> EncryptedArtifact(ciphertext, nonce, E)
 */
@Singleton
class ArtifactEncryption @Inject constructor() {

    companion object {
        private val EPH_DOMAIN = "identipay-artifact-eph-v1".toByteArray(Charsets.UTF_8)
        private val ENC_DOMAIN = "identipay-artifact-enc-v1".toByteArray(Charsets.UTF_8)
        private const val GCM_TAG_BITS = 128
        private const val GCM_NONCE_SIZE = 12
    }

    /**
     * Encrypt a plaintext artifact for a merchant.
     *
     * @param stealthPrivKey The buyer's stealth private key (32 bytes)
     * @param merchantPubKey The merchant's X25519 public key (32 bytes)
     * @param plaintext The data to encrypt
     */
    fun encrypt(
        stealthPrivKey: ByteArray,
        merchantPubKey: ByteArray,
        plaintext: ByteArray,
    ): EncryptedArtifact {
        // 1. Derive deterministic ephemeral private key
        val sha256 = MessageDigest.getInstance("SHA-256")
        sha256.update(stealthPrivKey)
        sha256.update(EPH_DOMAIN)
        val ephPriv = sha256.digest()

        // Apply X25519 clamping
        ephPriv[0] = (ephPriv[0].toInt() and 248).toByte()
        ephPriv[31] = (ephPriv[31].toInt() and 127).toByte()
        ephPriv[31] = (ephPriv[31].toInt() or 64).toByte()

        // 2. Compute ephemeral public key
        val ephPub = ByteArray(32)
        X25519.scalarMultBase(ephPriv, 0, ephPub, 0)

        // 3. ECDH shared secret
        val shared = ByteArray(32)
        X25519.scalarMult(ephPriv, 0, merchantPubKey, 0, shared, 0)

        // 4. Derive encryption key
        val sha256Enc = MessageDigest.getInstance("SHA-256")
        sha256Enc.update(shared)
        sha256Enc.update(ENC_DOMAIN)
        val encKey = sha256Enc.digest()

        // 5. Random nonce
        val nonce = ByteArray(GCM_NONCE_SIZE)
        SecureRandom().nextBytes(nonce)

        // 6. AES-256-GCM encrypt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(encKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        val ciphertext = cipher.doFinal(plaintext)

        return EncryptedArtifact(
            ciphertext = ciphertext,
            nonce = nonce,
            ephemeralPubkey = ephPub,
        )
    }

    /**
     * Decrypt an artifact as the buyer (stealth key holder).
     *
     * Recomputes the same ephemeral key deterministically, then ECDH with merchant pubkey.
     *
     * @param stealthPrivKey The buyer's stealth private key (32 bytes)
     * @param merchantPubKey The merchant's X25519 public key (32 bytes)
     * @param ciphertext The encrypted data (including GCM tag)
     * @param nonce The 12-byte GCM nonce
     */
    fun decrypt(
        stealthPrivKey: ByteArray,
        merchantPubKey: ByteArray,
        ciphertext: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        // 1. Re-derive deterministic ephemeral private key (same as encrypt)
        val sha256 = MessageDigest.getInstance("SHA-256")
        sha256.update(stealthPrivKey)
        sha256.update(EPH_DOMAIN)
        val ephPriv = sha256.digest()

        // Apply X25519 clamping
        ephPriv[0] = (ephPriv[0].toInt() and 248).toByte()
        ephPriv[31] = (ephPriv[31].toInt() and 127).toByte()
        ephPriv[31] = (ephPriv[31].toInt() or 64).toByte()

        // 2. ECDH shared secret: e * K_merchant
        val shared = ByteArray(32)
        X25519.scalarMult(ephPriv, 0, merchantPubKey, 0, shared, 0)

        // 3. Derive encryption key
        val sha256Enc = MessageDigest.getInstance("SHA-256")
        sha256Enc.update(shared)
        sha256Enc.update(ENC_DOMAIN)
        val encKey = sha256Enc.digest()

        // 4. AES-256-GCM decrypt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(encKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        return cipher.doFinal(ciphertext)
    }

    /**
     * Decrypt an artifact as the merchant.
     *
     * Uses ECDH: k_merchant * E (ephemeral pubkey from the buyer).
     *
     * @param merchantPrivKey The merchant's X25519 private key (32 bytes)
     * @param ephemeralPubkey The buyer's ephemeral public key from EncryptedArtifact
     * @param ciphertext The encrypted data (including GCM tag)
     * @param nonce The 12-byte GCM nonce
     */
    fun decryptAsMerchant(
        merchantPrivKey: ByteArray,
        ephemeralPubkey: ByteArray,
        ciphertext: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        // 1. ECDH shared secret: k_merchant * E
        val shared = ByteArray(32)
        X25519.scalarMult(merchantPrivKey, 0, ephemeralPubkey, 0, shared, 0)

        // 2. Derive encryption key
        val sha256Enc = MessageDigest.getInstance("SHA-256")
        sha256Enc.update(shared)
        sha256Enc.update(ENC_DOMAIN)
        val encKey = sha256Enc.digest()

        // 3. AES-256-GCM decrypt
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(encKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        return cipher.doFinal(ciphertext)
    }
}
