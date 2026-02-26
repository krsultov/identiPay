package com.identipay.wallet.network

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds Sui Programmable Transaction Blocks (PTBs) for identiPay operations.
 *
 * Target function:
 * <PACKAGE_ID>::meta_address_registry::register_name(
 *     registry: &mut MetaAddressRegistry,
 *     vk: &VerificationKey,
 *     name: String,
 *     spend_pubkey: vector<u8>,
 *     view_pubkey: vector<u8>,
 *     identity_commitment: vector<u8>,
 *     zk_proof: vector<u8>,
 *     zk_public_inputs: vector<u8>,
 *     ctx: &mut TxContext,
 * )
 */
@Singleton
class TransactionBuilder @Inject constructor() {

    /**
     * Build a registration PTB as a JSON-serializable transaction.
     * The actual PTB is constructed and signed on the backend (gas-sponsored).
     * This method prepares the parameter data.
     */
    fun buildRegistrationParams(
        name: String,
        spendPubkey: ByteArray,
        viewPubkey: ByteArray,
        identityCommitment: ByteArray,
        zkProof: ByteArray,
        zkPublicInputs: ByteArray,
    ): RegistrationParams {
        require(spendPubkey.size == 32) { "Spend pubkey must be 32 bytes" }
        require(viewPubkey.size == 32) { "View pubkey must be 32 bytes" }
        require(name.length in 3..20) { "Name must be 3-20 characters" }
        require(name.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }) {
            "Name must be lowercase alphanumeric with hyphens"
        }
        require(!name.startsWith('-') && !name.endsWith('-')) {
            "Name cannot start or end with a hyphen"
        }

        return RegistrationParams(
            packageId = SuiClientProvider.PACKAGE_ID,
            registryId = SuiClientProvider.META_REGISTRY_ID,
            name = name,
            spendPubkey = spendPubkey.toHexString(),
            viewPubkey = viewPubkey.toHexString(),
            identityCommitment = identityCommitment.toHexString(),
            zkProof = zkProof.toHexString(),
            zkPublicInputs = zkPublicInputs.toHexString(),
        )
    }

    /**
     * Validate a name for registration.
     */
    fun isValidName(name: String): Boolean {
        if (name.length !in 3..20) return false
        if (!name.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }) return false
        if (name.startsWith('-') || name.endsWith('-')) return false
        return true
    }
}

data class RegistrationParams(
    val packageId: String,
    val registryId: String,
    val name: String,
    val spendPubkey: String,
    val viewPubkey: String,
    val identityCommitment: String,
    val zkProof: String,
    val zkPublicInputs: String,
)

fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
