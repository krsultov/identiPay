package com.identipay.wallet.data.repository

import android.util.Base64
import android.util.Log
import com.identipay.wallet.crypto.Ed25519Ops
import com.identipay.wallet.crypto.StealthAddress
import com.identipay.wallet.data.db.dao.StealthAddressDao
import com.identipay.wallet.data.db.dao.TransactionDao
import com.identipay.wallet.data.db.entity.StealthAddressEntity
import com.identipay.wallet.data.db.entity.TransactionEntity
import com.identipay.wallet.data.preferences.UserPreferences
import com.identipay.wallet.data.preferences.WalletKeys
import com.identipay.wallet.network.BackendApi
import com.identipay.wallet.network.CreatePayRequest
import com.identipay.wallet.network.GasSponsorSendRequest
import com.identipay.wallet.network.PayRequestDetail
import com.identipay.wallet.network.PayRequestResponse
import com.identipay.wallet.network.SubmitTxRequest
import com.identipay.wallet.network.toHexString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentRepository @Inject constructor(
    private val backendApi: BackendApi,
    private val stealthAddress: StealthAddress,
    private val walletKeys: WalletKeys,
    private val stealthAddressDao: StealthAddressDao,
    private val transactionDao: TransactionDao,
    private val userPreferences: UserPreferences,
    private val balanceRepository: dagger.Lazy<BalanceRepository>,
) {
    /**
     * Send USDC to a resolved @name.idpay.
     *
     * Flow:
     * 1. Resolve name -> (spendPubkey, viewPubkey)
     * 2. Derive stealth address for recipient
     * 3. Select source coin from our stealth addresses (largest-first)
     * 4. Request gas-sponsored transaction from backend
     * 5. Sign with source stealth private key
     * 6. Submit via backend
     * 7. Record transaction locally
     */
    suspend fun sendToName(name: String, amountMicros: Long): SendResult {
        // 1. Resolve recipient
        val resolution = backendApi.resolveName(name)
            ?: return SendResult.Error("Name @$name.idpay not found")

        val recipientSpendPub = hexToBytes(resolution.spendPubkey)
        val recipientViewPub = hexToBytes(resolution.viewPubkey)

        // 2. Derive stealth address for recipient
        val stealth = stealthAddress.derive(recipientSpendPub, recipientViewPub)

        // 3. Find a source stealth address with sufficient balance
        val sources = stealthAddressDao.getWithBalance()
        val source = sources.firstOrNull { it.balanceUsdc >= amountMicros }
            ?: return SendResult.Error("Insufficient balance")

        // 4. Build and execute via gas sponsorship
        val sourcePrivKey = Base64.decode(source.stealthPrivKeyEnc, Base64.NO_WRAP)

        val txDigest = executeSendTransaction(
            senderAddress = source.stealthAddress,
            senderPrivKey = sourcePrivKey,
            recipientStealthAddress = stealth.stealthAddress,
            amountMicros = amountMicros,
            ephemeralPubkey = stealth.ephemeralPubkey,
            viewTag = stealth.viewTag,
        )

        // 5. Record transaction locally
        transactionDao.insert(
            TransactionEntity(
                txDigest = txDigest,
                type = "send",
                amount = amountMicros,
                counterpartyName = name,
                stealthAddress = source.stealthAddress,
            )
        )

        return SendResult.Success(txDigest)
    }

    companion object {
        private const val TAG = "PaymentRepository"
        private const val USDC_TYPE =
            "0xa1ec7fc00a6f40db9693ad1415d0c193ad3906494428cf252621037bd7117e29::usdc::USDC"
        /** Stop scanning after this many consecutive empty addresses (fresh device recovery). */
        private const val GAP_LIMIT = 20
    }

    /**
     * Self-derive a fresh stealth address for receiving.
     * Uses a counter-based ephemeral key for determinism.
     * Stores the address in the DB so balance refresh can track it.
     */
    suspend fun deriveReceiveAddress(counter: Int = 0): ReceiveAddress {
        val spendPub = walletKeys.getSpendPublicKey()
        val viewPub = walletKeys.getViewPublicKey()

        // Deterministic ephemeral key from counter
        val seed = walletKeys.getViewKeyPair().privateKey
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(seed, "HmacSHA256"))
        val ephPriv = mac.doFinal("receive-$counter".toByteArray(Charsets.UTF_8))
        // Apply X25519 clamping
        ephPriv[0] = (ephPriv[0].toInt() and 248).toByte()
        ephPriv[31] = (ephPriv[31].toInt() and 127).toByte()
        ephPriv[31] = (ephPriv[31].toInt() or 64).toByte()

        val stealth = stealthAddress.derive(spendPub, viewPub, ephPriv)

        // Recover stealth private key via receiver-side scan so we can spend later
        val viewPriv = walletKeys.getViewKeyPair().privateKey
        val spendPriv = walletKeys.getSpendKeyPair().privateKey
        val scanResult = stealthAddress.scan(
            viewPrivateKey = viewPriv,
            spendPrivateKey = spendPriv,
            spendPubkey = spendPub,
            ephemeralPubkey = stealth.ephemeralPubkey,
            announcedViewTag = stealth.viewTag,
            announcedStealthAddress = stealth.stealthAddress,
        )

        if (scanResult != null) {
            val privKeyEnc = Base64.encodeToString(scanResult.stealthPrivateKey, Base64.NO_WRAP)
            stealthAddressDao.insert(
                StealthAddressEntity(
                    stealthAddress = scanResult.stealthAddress,
                    stealthPrivKeyEnc = privKeyEnc,
                    stealthPubkey = scanResult.stealthPubkey.toHexString(),
                    ephemeralPubkey = stealth.ephemeralPubkey.toHexString(),
                    viewTag = stealth.viewTag,
                    createdAt = System.currentTimeMillis(),
                )
            )
            Log.d(TAG, "deriveReceiveAddress: stored ${scanResult.stealthAddress} in DB")
        } else {
            Log.e(TAG, "deriveReceiveAddress: self-scan failed for ${stealth.stealthAddress}")
        }

        // Persist high-water mark so we can re-derive on recovery
        val currentMax = userPreferences.getReceiveCounterOnce()
        if (counter >= currentMax) {
            userPreferences.setReceiveCounter(counter + 1)
        }

        return ReceiveAddress(
            stealthAddress = stealth.stealthAddress,
            ephemeralPubkey = stealth.ephemeralPubkey.toHexString(),
            viewTag = stealth.viewTag,
        )
    }

    /**
     * Re-derive receive stealth addresses and recover any missing from the local DB.
     *
     * Two modes:
     * - **Known counter** (same device / DataStore intact): replays 0..highWaterMark,
     *   re-inserts any that are missing from Room. Cheap — no RPC calls needed.
     * - **Fresh device** (counter == 0, new install with restored seed): uses a
     *   BIP44-style gap-limit scan. Derives addresses sequentially and queries
     *   on-chain balance; stops after [GAP_LIMIT] consecutive empty addresses.
     *
     * @return number of addresses recovered
     */
    suspend fun recoverReceiveAddresses(): Int {
        if (!walletKeys.hasKeys()) return 0

        val highWaterMark = userPreferences.getReceiveCounterOnce()

        val spendPub = walletKeys.getSpendPublicKey()
        val viewPub = walletKeys.getViewPublicKey()
        val viewPriv = walletKeys.getViewKeyPair().privateKey
        val spendPriv = walletKeys.getSpendKeyPair().privateKey
        val seed = viewPriv

        return if (highWaterMark > 0) {
            // Known counter — replay all indices, no RPC needed
            recoverRange(0, highWaterMark, spendPub, viewPub, viewPriv, spendPriv, seed)
        } else {
            // Fresh device — gap-limit scan with on-chain balance checks
            recoverWithGapLimit(spendPub, viewPub, viewPriv, spendPriv, seed)
        }
    }

    /**
     * Re-derive addresses for counter indices [from, until) and insert any missing.
     */
    private suspend fun recoverRange(
        from: Int,
        until: Int,
        spendPub: ByteArray,
        viewPub: ByteArray,
        viewPriv: ByteArray,
        spendPriv: ByteArray,
        seed: ByteArray,
    ): Int {
        var recovered = 0
        for (i in from until until) {
            if (recoverSingleAddress(i, spendPub, viewPub, viewPriv, spendPriv, seed)) {
                recovered++
            }
        }
        Log.d(TAG, "recoverRange: recovered $recovered of ${until - from} addresses")
        return recovered
    }

    /**
     * Gap-limit scan for fresh device recovery. Derives addresses sequentially,
     * checks on-chain balance, and stops after [GAP_LIMIT] consecutive addresses
     * with zero balance.
     */
    private suspend fun recoverWithGapLimit(
        spendPub: ByteArray,
        viewPub: ByteArray,
        viewPriv: ByteArray,
        spendPriv: ByteArray,
        seed: ByteArray,
    ): Int {
        Log.d(TAG, "recoverWithGapLimit: starting gap-limit scan (gap=$GAP_LIMIT)")
        var recovered = 0
        var consecutiveEmpty = 0
        var counter = 0

        while (consecutiveEmpty < GAP_LIMIT) {
            val stealth = deriveAtCounter(counter, spendPub, viewPub, seed)

            // Check if already in DB
            val existing = stealthAddressDao.getByAddress(stealth.stealthAddress)
            if (existing != null) {
                // Already known — reset gap counter
                consecutiveEmpty = 0
                counter++
                continue
            }

            // Check on-chain balance
            val balance = balanceRepository.get().queryBalance(stealth.stealthAddress)
            if (balance > 0L) {
                // Has funds — recover it
                if (recoverSingleAddress(counter, spendPub, viewPub, viewPriv, spendPriv, seed)) {
                    recovered++
                }
                consecutiveEmpty = 0
            } else {
                consecutiveEmpty++
            }
            counter++
        }

        // Update persisted counter if we discovered addresses beyond it
        if (counter - GAP_LIMIT > 0) {
            val discoveredMax = counter - GAP_LIMIT
            userPreferences.setReceiveCounter(discoveredMax)
            Log.d(TAG, "recoverWithGapLimit: updated counter to $discoveredMax")
        }

        Log.d(TAG, "recoverWithGapLimit: scanned $counter addresses, recovered $recovered")
        return recovered
    }

    /**
     * Derive stealth output at a specific counter index.
     */
    private fun deriveAtCounter(
        counter: Int,
        spendPub: ByteArray,
        viewPub: ByteArray,
        seed: ByteArray,
    ): com.identipay.wallet.crypto.StealthOutput {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(seed, "HmacSHA256"))
        val ephPriv = mac.doFinal("receive-$counter".toByteArray(Charsets.UTF_8))
        ephPriv[0] = (ephPriv[0].toInt() and 248).toByte()
        ephPriv[31] = (ephPriv[31].toInt() and 127).toByte()
        ephPriv[31] = (ephPriv[31].toInt() or 64).toByte()
        return stealthAddress.derive(spendPub, viewPub, ephPriv)
    }

    /**
     * Recover a single receive address at [counter] if missing from DB.
     * @return true if the address was inserted
     */
    private suspend fun recoverSingleAddress(
        counter: Int,
        spendPub: ByteArray,
        viewPub: ByteArray,
        viewPriv: ByteArray,
        spendPriv: ByteArray,
        seed: ByteArray,
    ): Boolean {
        val stealth = deriveAtCounter(counter, spendPub, viewPub, seed)

        if (stealthAddressDao.getByAddress(stealth.stealthAddress) != null) return false

        val scanResult = stealthAddress.scan(
            viewPrivateKey = viewPriv,
            spendPrivateKey = spendPriv,
            spendPubkey = spendPub,
            ephemeralPubkey = stealth.ephemeralPubkey,
            announcedViewTag = stealth.viewTag,
            announcedStealthAddress = stealth.stealthAddress,
        ) ?: return false

        val privKeyEnc = Base64.encodeToString(scanResult.stealthPrivateKey, Base64.NO_WRAP)
        stealthAddressDao.insert(
            StealthAddressEntity(
                stealthAddress = scanResult.stealthAddress,
                stealthPrivKeyEnc = privKeyEnc,
                stealthPubkey = scanResult.stealthPubkey.toHexString(),
                ephemeralPubkey = stealth.ephemeralPubkey.toHexString(),
                viewTag = stealth.viewTag,
                createdAt = System.currentTimeMillis(),
            )
        )
        Log.d(TAG, "recoverSingleAddress: recovered counter=$counter addr=${scanResult.stealthAddress}")
        return true
    }

    /**
     * Create a payment request via the backend.
     */
    suspend fun createPayRequest(
        amount: String,
        memo: String? = null,
        expiresInSeconds: Int = 600,
    ): PayRequestResponse {
        return backendApi.createPayRequest(
            CreatePayRequest(
                recipientName = "", // Will be set from UserPreferences in ViewModel
                amount = amount,
                memo = memo,
                expiresInSeconds = expiresInSeconds,
            )
        )
    }

    /**
     * Resolve a payment request.
     */
    suspend fun getPayRequest(requestId: String): PayRequestDetail {
        return backendApi.getPayRequest(requestId)
    }

    /**
     * Execute a P2P send via gas-sponsored transaction.
     *
     * 1. Request backend to build a gas-sponsored PTB (splitCoins + transferObjects + announce)
     * 2. Sign the returned tx bytes with the stealth private key
     * 3. Submit the signed tx via backend
     */
    private suspend fun executeSendTransaction(
        senderAddress: String,
        senderPrivKey: ByteArray,
        recipientStealthAddress: String,
        amountMicros: Long,
        ephemeralPubkey: ByteArray,
        viewTag: Int,
    ): String {
        // 1. Request gas-sponsored transaction from backend (backend finds coin)
        val sponsorResponse = backendApi.sponsorSend(
            GasSponsorSendRequest(
                senderAddress = senderAddress,
                amount = amountMicros.toString(),
                recipient = recipientStealthAddress,
                coinType = USDC_TYPE,
                ephemeralPubkey = ephemeralPubkey.map { it.toInt() and 0xFF },
                viewTag = viewTag,
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

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.startsWith("0x")) hex.substring(2) else hex
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

sealed class SendResult {
    data class Success(val txDigest: String) : SendResult()
    data class Error(val message: String) : SendResult()
}

data class ReceiveAddress(
    val stealthAddress: String,
    val ephemeralPubkey: String,
    val viewTag: Int,
)
