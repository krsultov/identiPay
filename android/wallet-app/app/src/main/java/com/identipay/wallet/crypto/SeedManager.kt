package com.identipay.wallet.crypto

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sharedPreferences: SharedPreferences,
) {
    companion object {
        private const val KEYSTORE_ALIAS = "identipay_seed_key"
        private const val PREF_ENCRYPTED_SEED = "encrypted_seed"
        private const val PREF_SEED_IV = "seed_iv"
        private const val PREF_MNEMONIC = "encrypted_mnemonic"
        private const val PREF_MNEMONIC_IV = "mnemonic_iv"
        private const val GCM_TAG_LENGTH = 128
    }

    fun hasSeed(): Boolean {
        return sharedPreferences.contains(PREF_ENCRYPTED_SEED)
    }

    fun generateMnemonic(): List<String> {
        val entropy = ByteArray(32)
        SecureRandom().nextBytes(entropy)
        val words = entropyToMnemonic(entropy)
        val seed = mnemonicToSeed(words)
        storeSeed(seed)
        storeMnemonic(words.joinToString(" "))
        return words
    }

    fun restoreFromMnemonic(words: List<String>): Boolean {
        if (words.size != 24) return false
        val seed = mnemonicToSeed(words)
        storeSeed(seed)
        storeMnemonic(words.joinToString(" "))
        return true
    }

    fun getSeed(): ByteArray {
        val encrypted = Base64.decode(
            sharedPreferences.getString(PREF_ENCRYPTED_SEED, null)
                ?: throw IllegalStateException("No seed stored"),
            Base64.DEFAULT
        )
        val iv = Base64.decode(
            sharedPreferences.getString(PREF_SEED_IV, null)
                ?: throw IllegalStateException("No seed IV stored"),
            Base64.DEFAULT
        )
        return decrypt(encrypted, iv)
    }

    fun deriveSpendKeyPair(): Ed25519KeyPair {
        val seed = getSeed()
        val derived = deriveSubkey(seed, "spend")
        return Ed25519Ops.keyPairFromSeed(derived)
    }

    fun deriveViewKeyPair(): X25519KeyPair {
        val seed = getSeed()
        val derived = deriveSubkey(seed, "view")
        return X25519Ops.keyPairFromSeed(derived)
    }

    private fun deriveSubkey(masterSeed: ByteArray, purpose: String): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA512")
        val keySpec = javax.crypto.spec.SecretKeySpec(masterSeed, "HmacSHA512")
        mac.init(keySpec)
        val derived = mac.doFinal(purpose.toByteArray(Charsets.UTF_8))
        return derived.copyOfRange(0, 32)
    }

    private fun storeSeed(seed: ByteArray) {
        val key = getOrCreateKeyStoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(seed)
        sharedPreferences.edit()
            .putString(PREF_ENCRYPTED_SEED, Base64.encodeToString(encrypted, Base64.DEFAULT))
            .putString(PREF_SEED_IV, Base64.encodeToString(cipher.iv, Base64.DEFAULT))
            .apply()
    }

    private fun storeMnemonic(mnemonic: String) {
        val key = getOrCreateKeyStoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(mnemonic.toByteArray(Charsets.UTF_8))
        sharedPreferences.edit()
            .putString(PREF_MNEMONIC, Base64.encodeToString(encrypted, Base64.DEFAULT))
            .putString(PREF_MNEMONIC_IV, Base64.encodeToString(cipher.iv, Base64.DEFAULT))
            .apply()
    }

    private fun decrypt(encrypted: ByteArray, iv: ByteArray): ByteArray {
        val key = getOrCreateKeyStoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateKeyStoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            return (keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }

    private fun entropyToMnemonic(entropy: ByteArray): List<String> {
        val bits = ByteArray(entropy.size * 8 + entropy.size * 8 / 32)
        for (i in entropy.indices) {
            for (j in 0 until 8) {
                bits[i * 8 + j] = ((entropy[i].toInt() shr (7 - j)) and 1).toByte()
            }
        }
        // SHA-256 checksum
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(entropy)
        val checksumBits = entropy.size * 8 / 32
        for (j in 0 until checksumBits) {
            bits[entropy.size * 8 + j] = ((hash[0].toInt() shr (7 - j)) and 1).toByte()
        }

        val words = mutableListOf<String>()
        val totalBits = entropy.size * 8 + checksumBits
        for (i in 0 until totalBits / 11) {
            var index = 0
            for (j in 0 until 11) {
                index = (index shl 1) or bits[i * 11 + j].toInt()
            }
            words.add(Bip39.getWordlist(context)[index])
        }
        return words
    }

    private fun mnemonicToSeed(words: List<String>): ByteArray {
        val mnemonic = words.joinToString(" ")
        val salt = "mnemonic".toByteArray(Charsets.UTF_8)
        val spec = javax.crypto.spec.PBEKeySpec(
            mnemonic.toCharArray(),
            salt,
            2048,
            512
        )
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        return factory.generateSecret(spec).encoded.copyOfRange(0, 32)
    }
}

data class Ed25519KeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Ed25519KeyPair) return false
        return privateKey.contentEquals(other.privateKey) && publicKey.contentEquals(other.publicKey)
    }
    override fun hashCode(): Int = privateKey.contentHashCode() * 31 + publicKey.contentHashCode()
}

data class X25519KeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is X25519KeyPair) return false
        return privateKey.contentEquals(other.privateKey) && publicKey.contentEquals(other.publicKey)
    }
    override fun hashCode(): Int = privateKey.contentHashCode() * 31 + publicKey.contentHashCode()
}
