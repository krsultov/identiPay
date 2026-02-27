package com.identipay.wallet.data.repository

import android.util.Log
import com.identipay.wallet.crypto.IdentityCommitment
import com.identipay.wallet.crypto.SeedManager
import com.identipay.wallet.data.preferences.UserPreferences
import com.identipay.wallet.data.preferences.WalletKeys
import com.identipay.wallet.network.BackendApi
import com.identipay.wallet.network.RegistrationRequest
import com.identipay.wallet.network.SuiClientProvider
import com.identipay.wallet.network.toHexString
import com.identipay.wallet.nfc.CredentialData
import com.identipay.wallet.zk.IdentityRegistrationInput
import com.identipay.wallet.zk.ProofGenerator
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigInteger
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

private const val TAG = "IdentityRepository"

/**
 * Result of initializing keys from passport + PIN.
 */
sealed class InitKeysResult {
    /** Fresh registration — no existing name found on-chain. */
    object NewRegistration : InitKeysResult()
    /** Reactivation — an existing name was recovered from the chain. */
    data class Reactivated(val name: String) : InitKeysResult()
}

/**
 * Orchestrates the full identity registration flow:
 * 1. Scan passport -> enter PIN -> derive seed -> derive keypairs
 * 2. Check on-chain for existing registration (reactivation)
 * 3. If new: compute identity commitment, generate ZK proof, register
 * 4. Store registered name locally
 */
@Singleton
class IdentityRepository @Inject constructor(
    private val seedManager: SeedManager,
    private val walletKeys: WalletKeys,
    private val identityCommitment: IdentityCommitment,
    private val proofGenerator: ProofGenerator,
    private val backendApi: BackendApi,
    private val userPreferences: UserPreferences,
    @param:Named("suiRpc") private val suiRpcClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Initialize wallet keys from passport data + PIN.
     * Derives a deterministic seed, then checks the chain for an existing
     * registration. Returns [InitKeysResult.Reactivated] with the recovered
     * name if found, or [InitKeysResult.NewRegistration] if this is a fresh
     * wallet.
     */
    suspend fun initializeKeys(credentialData: CredentialData, pin: String): InitKeysResult {
        if (seedManager.hasSeed()) {
            throw IllegalStateException("Wallet already initialized")
        }

        val issuerCertHash = MessageDigest.getInstance("SHA-256")
            .digest(credentialData.issuerCertBytes)

        seedManager.deriveFromPassportAndPin(
            personalNumber = credentialData.rawPersonalNumber,
            dateOfBirth = credentialData.rawDateOfBirth,
            nationality = credentialData.rawNationality,
            issuerCertHash = issuerCertHash,
            pin = pin,
        )

        // Compute the identity commitment to look up on-chain
        val seed = seedManager.getSeed()
        val userSalt = identityCommitment.deriveSaltFromSeed(seed)
        val commitment = identityCommitment.compute(
            credentialData.issuerCertHash,
            credentialData.personalNumberHash,
            credentialData.dobHash,
            userSalt,
        )

        // Check if this commitment is already registered on-chain
        val existingName = lookupNameByCommitment(commitment)
        if (existingName != null) {
            // Reactivation: store recovered state locally
            userPreferences.setUserSalt(userSalt.toString())
            userPreferences.setRegisteredName(existingName)
            userPreferences.setOnboarded(true)
            return InitKeysResult.Reactivated(existingName)
        }

        return InitKeysResult.NewRegistration
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
        // 1. Derive deterministic user salt from seed
        val seed = seedManager.getSeed()
        val userSalt = identityCommitment.deriveSaltFromSeed(seed)
        // Store in preferences for quick access (reproducible from seed)
        userPreferences.setUserSalt(userSalt.toString())

        // 2. Compute identity commitment
        val commitment = identityCommitment.compute(
            credentialData.issuerCertHash,
            credentialData.personalNumberHash,
            credentialData.dobHash,
            userSalt,
        )

        // 3. Generate ZK proof
        val circuitInput = IdentityRegistrationInput.create(
            issuerCertHash = credentialData.issuerCertHash,
            personalNumberHash = credentialData.personalNumberHash,
            dobHash = credentialData.dobHash,
            userSalt = userSalt,
        )
        val proofResult = proofGenerator.generateIdentityProof(circuitInput)

        // 4. Get public keys
        val spendPubkey = walletKeys.getSpendKeyPair().publicKey
        val viewPubkey = walletKeys.getViewPublicKey()

        // 5. Serialize commitment to bytes
        val commitmentBytes = commitmentToBytes(commitment)

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

        // 7. Store locally
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

    // ── On-chain lookup ──

    /**
     * Look up a registered name by identity commitment directly from the Sui chain.
     * Queries the MetaAddressRegistry's `commitments` table (Table<vector<u8>, String>).
     *
     * Returns the name if found, or null if the commitment is not registered.
     */
    private suspend fun lookupNameByCommitment(commitment: BigInteger): String? {
        return try {
            // 1. Get the commitments table ID from the registry object
            val tableId = getCommitmentsTableId() ?: return null

            // 2. Query the dynamic field for this commitment
            val commitmentBytes = commitmentToBytes(commitment)
            val commitmentArray = commitmentBytes.map { it.toInt() and 0xFF }

            val dynamicFieldBody = """
                {
                    "jsonrpc": "2.0",
                    "id": 2,
                    "method": "suix_getDynamicFieldObject",
                    "params": [
                        "$tableId",
                        {"type": "vector<u8>", "value": $commitmentArray}
                    ]
                }
            """.trimIndent()

            val response = suiRpcClient.post(SuiClientProvider.SUI_TESTNET_URL) {
                setBody(TextContent(dynamicFieldBody, ContentType.Application.Json))
            }.body<String>()

            val parsed = json.parseToJsonElement(response).jsonObject
            val result = parsed["result"]?.jsonObject ?: return null
            val data = result["data"]?.jsonObject ?: return null
            val content = data["content"]?.jsonObject ?: return null
            val fields = content["fields"]?.jsonObject ?: return null
            val value = fields["value"]?.jsonPrimitive?.content
            value
        } catch (e: Exception) {
            Log.w(TAG, "Failed to look up commitment on-chain", e)
            null
        }
    }

    /**
     * Fetch the MetaAddressRegistry object and extract the commitments table UID.
     */
    private suspend fun getCommitmentsTableId(): String? {
        val body = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "sui_getObject",
                "params": [
                    "${SuiClientProvider.META_REGISTRY_ID}",
                    {"showContent": true}
                ]
            }
        """.trimIndent()

        val response = suiRpcClient.post(SuiClientProvider.SUI_TESTNET_URL) {
            setBody(TextContent(body, ContentType.Application.Json))
        }.body<String>()

        val parsed = json.parseToJsonElement(response).jsonObject
        val result = parsed["result"]?.jsonObject ?: return null
        val data = result["data"]?.jsonObject ?: return null
        val content = data["content"]?.jsonObject ?: return null
        val fields = content["fields"]?.jsonObject ?: return null
        val commitments = fields["commitments"]?.jsonObject ?: return null
        val commitmentsFields = commitments["fields"]?.jsonObject ?: return null
        val id = commitmentsFields["id"]?.jsonObject ?: return null
        return id["id"]?.jsonPrimitive?.content
    }

    // ── Helpers ──

    private fun commitmentToBytes(commitment: BigInteger): ByteArray {
        return commitment.toByteArray().let { raw ->
            if (raw.size <= 32) {
                val padded = ByteArray(32)
                raw.copyInto(padded, 32 - raw.size)
                padded
            } else {
                raw.copyOfRange(raw.size - 32, raw.size)
            }
        }
    }
}
