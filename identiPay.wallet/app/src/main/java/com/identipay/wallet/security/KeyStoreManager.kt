package com.identipay.wallet.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Log
import androidx.annotation.RequiresApi
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * Manages ECDSA cryptographic keys within the Android Keystore, ensuring compatibility
 * with the IdentiPay backend's verification process.
 *
 * Assumes:
 * - ECDSA with NIST P-256 curve (secp256r1) and SHA256 hashing.
 * - Backend expects Public Key as standard Base64 encoded SPKI/DER.
 * - Backend expects Signature as Base64Url encoded ASN.1/DER sequence.
 */
class KeyStoreManager {

    private val keyStore: KeyStore

    init {
        keyStore = loadKeyStore()
    }

    companion object {
        private const val TAG = "KeyStoreManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_EC // Elliptic Curve
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA" // Matches backend HashAlgorithmName.SHA256
        private const val CURVE_SPEC = "secp256r1" // NIST P-256 curve
    }

    private fun loadKeyStore(): KeyStore {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            return ks
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Android Keystore", e)
            throw RuntimeException("Android Keystore is unavailable.", e)
        }
    }

    /**
     * Checks if a key pair exists.
     */
    fun keyExists(alias: String): Boolean {
        return try {
            keyStore.containsAlias(alias)
        } catch (e: KeyStoreException) {
            Log.e(TAG, "Error checking alias '$alias'", e)
            false
        }
    }

    /**
     * Generates a new ECDSA key pair (P-256) if one doesn't exist.
     * @return True if a key was newly generated, false if it already existed or failed.
     */
    fun generateKeyPairIfNotExists(alias: String): Boolean {
        if (keyExists(alias)) {
            Log.i(TAG, "Key pair '$alias' already exists.")
            return false
        }

        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEYSTORE)

            val specBuilder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE_SPEC))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                specBuilder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
            } else {
                specBuilder.setUserAuthenticationValidityDurationSeconds(10)
            }


            keyPairGenerator.initialize(specBuilder.build())
            keyPairGenerator.generateKeyPair()
            Log.i(TAG, "Generated key pair for alias '$alias'.")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate key pair for alias '$alias'", e)
            return false
        }
    }

    /**
     * Get the Public Key associated with the alias.
     */
    private fun getPublicKeyObject(alias: String): PublicKey? {
        if (!keyExists(alias)) {
            Log.w(TAG, "Public key object not found for alias '$alias'.")
            return null
        }
        return try {
            keyStore.getCertificate(alias)?.publicKey
        } catch (e: KeyStoreException) {
            Log.e(TAG, "Error retrieving public key object for alias '$alias'", e)
            null
        }
    }

    /**
     * Retrieves the public key and encodes it as a standard Base64 string
     * using the SPKI/DER format.
     *
     * @return Standard Base64 encoded public key string, or null on error/not found.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun getPublicKeyBase64(alias: String): String? {
        return try {
            val publicKey = getPublicKeyObject(alias)
            publicKey?.encoded?.let { encodedBytes ->
                // Use standard Base64 encoder for SPKI/DER format
                Base64.getEncoder().encodeToString(encodedBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding public key for alias '$alias'", e)
            null
        }
    }

    /**
     * Signs the data using the private key for the alias. Triggers user authentication.
     *
     * @param alias The alias of the private key.
     * @param dataToSign The data to sign (e.g., UTF-8 bytes of canonical payload JSON).
     * @return Base64Url encoded signature string (ASN.1/DER format), or null on error/cancellation.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun signData(alias: String, dataToSign: ByteArray): String? {
        if (!keyExists(alias)) {
            Log.w(TAG, "Cannot sign: Private key not found for alias '$alias'.")
            return null
        }
        try {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                ?: throw KeyStoreException("Failed to retrieve private key entry for alias '$alias'.")

            val privateKey = entry.privateKey

            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(privateKey)

            signature.update(dataToSign)
            val signatureBytes = signature.sign() // Returns ASN.1 DER encoded signature

            // Encode using Base64Url for transmission
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)

        } catch (e: UserNotAuthenticatedException) {
            Log.e(TAG, "User authentication required but failed or cancelled for alias '$alias'", e)
            return null
        } catch (e: KeyStoreException) {
            Log.e(TAG, "Keystore error during signing for alias '$alias'", e)
            return null
        } catch (e: UnrecoverableKeyException) {
            Log.e(TAG, "Key is unrecoverable for alias '$alias'", e)
            return null
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "Signing algorithm not found for alias '$alias'", e)
            return null
        } catch (e: InvalidKeyException) {
            Log.e(TAG, "Invalid key for signing for alias '$alias'", e)
            return null
        } catch (e: SignatureException) {
            Log.e(TAG, "Signature generation failed for alias '$alias'", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during signing for alias '$alias'", e)
            return null
        }
    }

    fun getPrivateKeyEntry(alias: String): KeyStore.PrivateKeyEntry? {
        if (!keyExists(alias)) {
            Log.w(TAG, "Private key entry not found for alias '$alias'.")
            return null
        }
        return try {
            keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving private key entry for alias '$alias'", e)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun encodeSignatureBase64Url(signatureBytes: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)
    }
}