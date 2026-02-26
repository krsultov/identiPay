package com.identipay.wallet.data.repository

import com.identipay.wallet.crypto.IdentityCommitment
import com.identipay.wallet.crypto.SeedManager
import com.identipay.wallet.data.preferences.UserPreferences
import com.identipay.wallet.data.preferences.WalletKeys
import com.identipay.wallet.network.BackendApi
import com.identipay.wallet.network.RegistrationRequest
import com.identipay.wallet.network.toHexString
import com.identipay.wallet.nfc.CredentialData
import com.identipay.wallet.zk.IdentityRegistrationInput
import com.identipay.wallet.zk.ProofGenerator
import kotlinx.coroutines.flow.first
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full identity registration flow:
 * 1. Generate/load seed -> derive keypairs
 * 2. Accept NFC credential data
 * 3. Compute identity commitment
 * 4. Generate ZK proof
 * 5. Submit registration to backend (gas-sponsored)
 * 6. Store registered name locally
 */
@Singleton
class IdentityRepository @Inject constructor(
    private val seedManager: SeedManager,
    private val walletKeys: WalletKeys,
    private val identityCommitment: IdentityCommitment,
    private val proofGenerator: ProofGenerator,
    private val backendApi: BackendApi,
    private val userPreferences: UserPreferences,
) {

    /**
     * Initialize wallet keys if not already present.
     * @return The generated mnemonic words (only shown once to user)
     */
    suspend fun initializeKeys(): List<String> {
        if (seedManager.hasSeed()) {
            throw IllegalStateException("Wallet already initialized")
        }
        return seedManager.generateMnemonic()
    }

    /**
     * Check if wallet has been initialized with keys.
     */
    fun hasKeys(): Boolean = walletKeys.hasKeys()

    /**
     * Register a name on-chain with ZK proof of identity.
     */
    suspend fun registerName(
        name: String,
        credentialData: CredentialData,
    ): String {
        // 1. Get or generate user salt
        var saltStr = userPreferences.userSalt.first()
        val userSalt: BigInteger
        if (saltStr == null) {
            userSalt = identityCommitment.generateSalt()
            saltStr = userSalt.toString()
            userPreferences.setUserSalt(saltStr)
        } else {
            userSalt = BigInteger(saltStr)
        }

        // 2. Compute identity commitment
        val commitment = identityCommitment.compute(
            credentialData.issuerCertHash,
            credentialData.docNumberHash,
            credentialData.dobHash,
            userSalt,
        )

        // 3. Generate ZK proof
        val circuitInput = IdentityRegistrationInput.create(
            issuerCertHash = credentialData.issuerCertHash,
            docNumberHash = credentialData.docNumberHash,
            dobHash = credentialData.dobHash,
            userSalt = userSalt,
        )
        val proofResult = proofGenerator.generateIdentityProof(circuitInput)

        // 4. Get public keys
        val spendPubkey = walletKeys.getSpendKeyPair().publicKey
        val viewPubkey = walletKeys.getViewPublicKey()

        // 5. Serialize commitment to bytes
        val commitmentBytes = commitment.toByteArray().let { raw ->
            // Ensure 32 bytes, big-endian, no sign byte
            if (raw.size <= 32) {
                val padded = ByteArray(32)
                raw.copyInto(padded, 32 - raw.size)
                padded
            } else {
                // Strip leading zero sign byte
                raw.copyOfRange(raw.size - 32, raw.size)
            }
        }

        // 6. Submit to backend (backend builds and submits the Sui PTB)
        val request = RegistrationRequest(
            name = name,
            spendPubkey = spendPubkey.toHexString(),
            viewPubkey = viewPubkey.toHexString(),
            identityCommitment = commitmentBytes.toHexString(),
            zkProof = proofResult.proofBytes.toHexString(),
            zkPublicInputs = proofResult.publicInputsBytes.toHexString(),
        )
        val response = backendApi.registerName(request)

        // 8. Store locally
        userPreferences.setRegisteredName(name)
        userPreferences.setOnboarded(true)

        return response.txDigest
    }

    /**
     * Check if a name is available for registration.
     */
    suspend fun isNameAvailable(name: String): Boolean {
        return backendApi.isNameAvailable(name)
    }
}
