package com.identipay.wallet.data.repository

import android.util.Base64
import android.util.Log
import com.identipay.wallet.crypto.ArtifactEncryption
import com.identipay.wallet.crypto.Ed25519Ops
import com.identipay.wallet.crypto.IntentHashComputer
import com.identipay.wallet.crypto.IntentSigner
import com.identipay.wallet.crypto.StealthAddress
import com.identipay.wallet.data.db.dao.StealthAddressDao
import com.identipay.wallet.data.db.dao.TransactionDao
import com.identipay.wallet.data.db.entity.StealthAddressEntity
import com.identipay.wallet.data.db.entity.TransactionEntity
import com.identipay.wallet.data.preferences.WalletKeys
import com.identipay.wallet.network.BackendApi
import com.identipay.wallet.network.CommerceProposal
import com.identipay.wallet.network.GasSponsorSettlementRequest
import com.identipay.wallet.network.SubmitTxRequest
import com.identipay.wallet.network.toHexString
import javax.inject.Inject
import javax.inject.Singleton

sealed class CommerceResult {
    data class Success(val txDigest: String) : CommerceResult()
    data class Error(val message: String) : CommerceResult()
}

/**
 * Orchestrates the full merchant checkout flow:
 * 1. Fetch & verify proposal
 * 2. Sign intent
 * 3. Optional ZK proof
 * 4. Encrypt receipt/warranty artifacts
 * 5. Request gas-sponsored settlement from backend
 * 6. Sign and submit
 * 7. Record transaction
 */
@Singleton
class CommerceRepository @Inject constructor(
    private val backendApi: BackendApi,
    private val intentHashComputer: IntentHashComputer,
    private val intentSigner: IntentSigner,
    private val artifactEncryption: ArtifactEncryption,
    private val stealthAddress: StealthAddress,
    private val walletKeys: WalletKeys,
    private val stealthAddressDao: StealthAddressDao,
    private val transactionDao: TransactionDao,
) {
    companion object {
        private const val TAG = "CommerceRepository"
        private const val USDC_TYPE =
            "0xa1ec7fc00a6f40db9693ad1415d0c193ad3906494428cf252621037bd7117e29::usdc::USDC"
    }

    /**
     * Fetch a proposal from the backend and independently verify its intent hash.
     * Rejects if the computed hash doesn't match the provided one.
     */
    suspend fun fetchProposal(txId: String): Result<CommerceProposal> {
        return try {
            val proposal = backendApi.getIntent(txId)

            // Independently verify the intent hash
            if (!intentHashComputer.verify(proposal)) {
                return Result.failure(
                    IllegalStateException("Intent hash verification failed — proposal may be tampered")
                )
            }

            Result.success(proposal)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Execute the full checkout flow for a verified proposal.
     *
     * @param proposal The verified CommerceProposal
     * @param zkProof Optional ZK proof bytes (if age-gated)
     * @param zkPublicInputs Optional ZK public inputs (if age-gated)
     */
    suspend fun executeCheckout(
        proposal: CommerceProposal,
        zkProof: ByteArray? = null,
        zkPublicInputs: ByteArray? = null,
    ): CommerceResult {
        try {
            // 1. Derive a one-time stealth address for the buyer (receipt delivery)
            val spendPub = walletKeys.getSpendPublicKey()
            val viewPub = walletKeys.getViewPublicKey()
            val buyerStealth = stealthAddress.derive(spendPub, viewPub)

            // Recover stealth private key for signing
            val viewPriv = walletKeys.getViewKeyPair().privateKey
            val spendPriv = walletKeys.getSpendKeyPair().privateKey
            val scanResult = stealthAddress.scan(
                viewPrivateKey = viewPriv,
                spendPrivateKey = spendPriv,
                spendPubkey = spendPub,
                ephemeralPubkey = buyerStealth.ephemeralPubkey,
                announcedViewTag = buyerStealth.viewTag,
                announcedStealthAddress = buyerStealth.stealthAddress,
            ) ?: return CommerceResult.Error("Failed to derive buyer stealth key")

            // Store the buyer's stealth address so we can track the receipt
            val privKeyEnc = Base64.encodeToString(scanResult.stealthPrivateKey, Base64.NO_WRAP)
            stealthAddressDao.insert(
                StealthAddressEntity(
                    stealthAddress = scanResult.stealthAddress,
                    stealthPrivKeyEnc = privKeyEnc,
                    stealthPubkey = scanResult.stealthPubkey.toHexString(),
                    ephemeralPubkey = buyerStealth.ephemeralPubkey.toHexString(),
                    viewTag = buyerStealth.viewTag,
                    createdAt = System.currentTimeMillis(),
                )
            )

            // 2. Sign the intent hash with buyer's stealth private key (raw scalar)
            val intentSig = intentSigner.sign(proposal.intentHash, scanResult.stealthPrivateKey)
            val buyerPubkey = Ed25519Ops.publicKeyFromScalar(scanResult.stealthPrivateKey)

            // 3. Build receipt JSON and encrypt
            val merchantPubKeyBytes = hexToBytes(proposal.merchant.publicKey)
            val receiptJson = buildReceiptJson(proposal)
            val encryptedReceipt = artifactEncryption.encrypt(
                stealthPrivKey = scanResult.stealthPrivateKey,
                merchantPubKey = merchantPubKeyBytes,
                plaintext = receiptJson.toByteArray(Charsets.UTF_8),
            )

            // 4. Encrypt warranty if present
            val hasWarranty = proposal.deliverables.warranty != null
            val encryptedWarranty = if (hasWarranty) {
                val warrantyJson = buildWarrantyJson(proposal)
                artifactEncryption.encrypt(
                    stealthPrivKey = scanResult.stealthPrivateKey,
                    merchantPubKey = merchantPubKeyBytes,
                    plaintext = warrantyJson.toByteArray(Charsets.UTF_8),
                )
            } else null

            // 5. Find USDC source with sufficient balance
            val amountMicros = parseAmountMicros(proposal.amount.value)
            val sources = stealthAddressDao.getWithBalance()
            val source = sources.firstOrNull { it.balanceUsdc >= amountMicros }
                ?: return CommerceResult.Error("Insufficient USDC balance")

            val sourcePrivKey = Base64.decode(source.stealthPrivKeyEnc, Base64.NO_WRAP)

            // 6. Parse proposal expiry to epoch millis
            val proposalExpiry = java.time.Instant.parse(proposal.expiresAt).toEpochMilli()

            // 7. Warranty parameters
            val warrantyExpiry = if (hasWarranty) {
                val days = proposal.deliverables.warranty!!.durationDays.toLong()
                System.currentTimeMillis() + days * 86_400_000L
            } else 0L
            val warrantyTransferable = proposal.deliverables.warranty?.transferable ?: false

            // 8. Build and execute the settlement via gas sponsorship
            val isAgeGated = proposal.constraints?.ageGate != null && zkProof != null
            val intentHashBytes = hexToBytes(proposal.intentHash)

            val txDigest = executeSettlement(
                senderAddress = source.stealthAddress,
                senderPrivKey = sourcePrivKey,
                amountMicros = amountMicros,
                merchantAddress = proposal.merchant.suiAddress,
                buyerStealthAddr = scanResult.stealthAddress,
                intentSig = intentSig,
                intentHashBytes = intentHashBytes,
                buyerPubkey = buyerPubkey,
                proposalExpiry = proposalExpiry,
                encryptedPayload = encryptedReceipt.ciphertext,
                payloadNonce = encryptedReceipt.nonce,
                ephemeralPubkey = encryptedReceipt.ephemeralPubkey,
                encryptedWarrantyTerms = encryptedWarranty?.ciphertext ?: ByteArray(0),
                warrantyTermsNonce = encryptedWarranty?.nonce ?: ByteArray(0),
                warrantyExpiry = warrantyExpiry,
                warrantyTransferable = warrantyTransferable,
                stealthEphemeralPubkey = buyerStealth.ephemeralPubkey,
                stealthViewTag = buyerStealth.viewTag,
                isAgeGated = isAgeGated,
                zkProof = zkProof,
                zkPublicInputs = zkPublicInputs,
            )

            // 9. Record in local DB
            transactionDao.insert(
                TransactionEntity(
                    txDigest = txDigest,
                    type = "commerce",
                    amount = amountMicros,
                    counterpartyName = proposal.merchant.name,
                    stealthAddress = source.stealthAddress,
                )
            )

            Log.d(TAG, "Commerce settlement succeeded: $txDigest")
            return CommerceResult.Success(txDigest)
        } catch (e: Exception) {
            Log.e(TAG, "Checkout failed", e)
            return CommerceResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Execute a settlement via gas-sponsored transaction.
     *
     * 1. Send settlement parameters to backend for PTB construction + gas sponsorship
     * 2. Sign the returned tx bytes with the stealth private key
     * 3. Submit the signed tx via backend
     */
    private suspend fun executeSettlement(
        senderAddress: String,
        senderPrivKey: ByteArray,
        amountMicros: Long,
        merchantAddress: String,
        buyerStealthAddr: String,
        intentSig: ByteArray,
        intentHashBytes: ByteArray,
        buyerPubkey: ByteArray,
        proposalExpiry: Long,
        encryptedPayload: ByteArray,
        payloadNonce: ByteArray,
        ephemeralPubkey: ByteArray,
        encryptedWarrantyTerms: ByteArray,
        warrantyTermsNonce: ByteArray,
        warrantyExpiry: Long,
        warrantyTransferable: Boolean,
        stealthEphemeralPubkey: ByteArray,
        stealthViewTag: Int,
        isAgeGated: Boolean,
        zkProof: ByteArray?,
        zkPublicInputs: ByteArray?,
    ): String {
        val type = if (isAgeGated) "settlement" else "settlement_no_zk"

        // 1. Request gas-sponsored settlement from backend (backend finds coin + builds PTB)
        val sponsorResponse = backendApi.sponsorSettlement(
            GasSponsorSettlementRequest(
                type = type,
                senderAddress = senderAddress,
                coinType = USDC_TYPE,
                amount = amountMicros.toString(),
                merchantAddress = merchantAddress,
                buyerStealthAddr = buyerStealthAddr,
                intentSig = intentSig.map { it.toInt() and 0xFF },
                intentHash = intentHashBytes.map { it.toInt() and 0xFF },
                buyerPubkey = buyerPubkey.map { it.toInt() and 0xFF },
                proposalExpiry = proposalExpiry.toString(),
                encryptedPayload = encryptedPayload.map { it.toInt() and 0xFF },
                payloadNonce = payloadNonce.map { it.toInt() and 0xFF },
                ephemeralPubkey = ephemeralPubkey.map { it.toInt() and 0xFF },
                encryptedWarrantyTerms = encryptedWarrantyTerms.map { it.toInt() and 0xFF },
                warrantyTermsNonce = warrantyTermsNonce.map { it.toInt() and 0xFF },
                warrantyExpiry = warrantyExpiry.toString(),
                warrantyTransferable = warrantyTransferable,
                stealthEphemeralPubkey = stealthEphemeralPubkey.map { it.toInt() and 0xFF },
                stealthViewTag = stealthViewTag,
                zkProof = zkProof?.map { it.toInt() and 0xFF },
                zkPublicInputs = zkPublicInputs?.map { it.toInt() and 0xFF },
            )
        )

        // 2. Sign the tx bytes with stealth private key
        val signature = signTransaction(sponsorResponse.txBytes, senderPrivKey)

        // 3. Submit via backend (backend co-signs as gas owner)
        val submitResponse = backendApi.submitSponsoredTx(
            SubmitTxRequest(
                txBytes = sponsorResponse.txBytes,
                senderSignature = signature,
            )
        )

        return submitResponse.txDigest
    }

    /**
     * Sign a Sui transaction with a raw Ed25519 scalar (stealth private key).
     * Produces the standard Sui signature format: flag(1) + sig(64) + pubkey(32).
     */
    private fun signTransaction(txBytes: String, stealthScalar: ByteArray): String {
        val txData = Base64.decode(txBytes, Base64.NO_WRAP)

        // Sui signs BLAKE2b-256(IntentPrefix || TransactionData)
        // IntentPrefix for TransactionData = [0x00, 0x00, 0x00]
        val intentMessage = ByteArray(3 + txData.size)
        intentMessage[0] = 0x00
        intentMessage[1] = 0x00
        intentMessage[2] = 0x00
        System.arraycopy(txData, 0, intentMessage, 3, txData.size)

        val digest = org.bouncycastle.crypto.digests.Blake2bDigest(256)
        digest.update(intentMessage, 0, intentMessage.size)
        val txDigestBytes = ByteArray(32)
        digest.doFinal(txDigestBytes, 0)

        // stealthScalar is a raw Ed25519 scalar (not a seed) — use raw scalar signing
        val pubkey = Ed25519Ops.publicKeyFromScalar(stealthScalar)
        val signature = Ed25519Ops.signWithScalar(txDigestBytes, stealthScalar, pubkey)

        // Self-verify before submitting
        check(Ed25519Ops.verify(txDigestBytes, signature, pubkey)) {
            "Ed25519 self-verification failed — signWithScalar or rawScalarMultBase is broken"
        }

        // Sui signature format: flag(1) + signature(64) + pubkey(32)
        val suiSig = ByteArray(97)
        suiSig[0] = 0x00 // Ed25519 flag
        System.arraycopy(signature, 0, suiSig, 1, 64)
        System.arraycopy(pubkey, 0, suiSig, 65, 32)

        return Base64.encodeToString(suiSig, Base64.NO_WRAP)
    }

    private fun buildReceiptJson(proposal: CommerceProposal): String {
        val items = proposal.items.joinToString(",") { item ->
            """{"name":"${item.name}","quantity":${item.quantity},"unitPrice":"${item.unitPrice}"}"""
        }
        return """{"transactionId":"${proposal.transactionId}","merchant":"${proposal.merchant.name}","items":[$items],"amount":"${proposal.amount.value}","currency":"${proposal.amount.currency}","timestamp":${System.currentTimeMillis()}}"""
    }

    private fun buildWarrantyJson(proposal: CommerceProposal): String {
        val w = proposal.deliverables.warranty!!
        return """{"transactionId":"${proposal.transactionId}","merchant":"${proposal.merchant.name}","durationDays":${w.durationDays},"transferable":${w.transferable},"issuedAt":${System.currentTimeMillis()}}"""
    }

    private fun parseAmountMicros(value: String): Long {
        val decimal = value.toDouble()
        return (decimal * 1_000_000).toLong()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.startsWith("0x")) hex.substring(2) else hex
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
