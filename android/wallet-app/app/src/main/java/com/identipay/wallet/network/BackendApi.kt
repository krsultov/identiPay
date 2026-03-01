package com.identipay.wallet.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class NameResolution(
    val spendPubkey: String,
    val viewPubkey: String,
)

@Serializable
data class RegistrationRequest(
    val name: String,
    val spendPubkey: String,
    val viewPubkey: String,
    val identityCommitment: String,
    val zkProof: String,
    val zkPublicInputs: String,
)

@Serializable
data class RegistrationResponse(
    val txDigest: String,
)

@Serializable
data class Announcement(
    val ephemeralPubkey: String,
    val stealthAddress: String,
    val viewTag: Int,
    val txDigest: String,
    val timestamp: String,
)

@Serializable
data class AnnouncementPage(
    val announcements: List<Announcement>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class CreatePayRequest(
    val recipientName: String,
    val amount: String,
    val currency: String = "USDC",
    val memo: String? = null,
    val expiresInSeconds: Int = 600,
)

@Serializable
data class PayRequestResponse(
    val requestId: String,
    val recipientName: String,
    val amount: String,
    val currency: String,
    val memo: String? = null,
    val expiresAt: String,
    val status: String,
    val qrDataUrl: String? = null,
    val uri: String? = null,
)

@Serializable
data class PayRequestDetail(
    val requestId: String,
    val recipientName: String,
    val amount: String,
    val currency: String,
    val memo: String? = null,
    val expiresAt: String,
    val status: String,
    val recipient: NameResolution? = null,
)

// ── Gas sponsorship types ──

@Serializable
data class GasSponsorSendRequest(
    val type: String = "send",
    val senderAddress: String,
    val amount: String,
    val recipient: String,
    val coinType: String,
    val ephemeralPubkey: List<Int>,
    val viewTag: Int,
)

@Serializable
data class GasSponsorSettlementRequest(
    val type: String,    // "settlement" or "settlement_no_zk"
    val senderAddress: String,
    val coinType: String,
    val amount: String,
    val merchantAddress: String,
    val buyerStealthAddr: String,
    val intentSig: List<Int>,
    val intentHash: List<Int>,
    val buyerPubkey: List<Int>,
    val proposalExpiry: String,
    val encryptedPayload: List<Int>,
    val payloadNonce: List<Int>,
    val ephemeralPubkey: List<Int>,
    val encryptedWarrantyTerms: List<Int>,
    val warrantyTermsNonce: List<Int>,
    val warrantyExpiry: String,
    val warrantyTransferable: Boolean,
    val stealthEphemeralPubkey: List<Int>,
    val stealthViewTag: Int,
    val zkProof: List<Int>? = null,
    val zkPublicInputs: List<Int>? = null,
)

@Serializable
data class GasSponsorPoolWithdrawRequest(
    val type: String = "pool_withdraw",
    val senderAddress: String,
    val coinType: String,
    val amount: String,
    val recipient: String,
    val nullifier: List<Int>,
    val changeCommitment: List<Int>,
    val zkProof: List<Int>,
    val zkPublicInputs: List<Int>,
)

@Serializable
data class GasSponsorResponse(val txBytes: String)

@Serializable
data class SubmitTxRequest(val txBytes: String, val senderSignature: String)

@Serializable
data class SubmitTxResponse(val txDigest: String)

// ── Merchant lookup types ──

@Serializable
data class MerchantLookup(
    val name: String,
    val publicKey: String,
    val suiAddress: String,
    val did: String,
)

// ── Commerce proposal types (matching backend types/proposal.ts) ──

@Serializable
data class MerchantInfo(
    val did: String,
    val name: String,
    val suiAddress: String,
    val publicKey: String,
)

@Serializable
data class LineItem(
    val name: String,
    val quantity: Int,
    val unitPrice: String,
    val currency: String? = null,
)

@Serializable
data class AmountInfo(
    val value: String,
    val currency: String,
)

@Serializable
data class Warranty(
    val durationDays: Int,
    val transferable: Boolean,
)

@Serializable
data class Deliverables(
    val receipt: Boolean,
    val warranty: Warranty? = null,
)

@Serializable
data class Constraints(
    val ageGate: Int? = null,
    val regionRestriction: List<String>? = null,
)

@Serializable
data class CommerceProposal(
    @kotlinx.serialization.SerialName("@context")
    val context: String,
    @kotlinx.serialization.SerialName("@type")
    val type: String,
    val transactionId: String,
    val merchant: MerchantInfo,
    val items: List<LineItem>,
    val amount: AmountInfo,
    val deliverables: Deliverables,
    val constraints: Constraints? = null,
    val expiresAt: String,
    val intentHash: String,
    val settlementChain: String,
    val settlementModule: String,
)

/**
 * Ktor HTTP client wrapping all backend API endpoints.
 */
@Singleton
class BackendApi @Inject constructor(
    private val httpClient: HttpClient,
) {

    /**
     * Check name availability and resolve if registered.
     * GET /names/:name
     */
    suspend fun resolveName(name: String): NameResolution? {
        return try {
            httpClient.get("names/$name").body<NameResolution>()
        } catch (e: Exception) {
            null // Name not found = available
        }
    }

    /**
     * Check if a name is available for registration.
     */
    suspend fun isNameAvailable(name: String): Boolean {
        return resolveName(name) == null
    }

    /**
     * Submit a gas-sponsored registration transaction.
     * POST /names/register
     */
    suspend fun registerName(request: RegistrationRequest): RegistrationResponse {
        return httpClient.post("names/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /**
     * Get stealth address announcements.
     * GET /announcements
     */
    suspend fun getAnnouncements(
        since: Long? = null,
        limit: Int = 50,
        cursor: String? = null,
    ): AnnouncementPage {
        return httpClient.get("announcements") {
            since?.let { parameter("since", it) }
            parameter("limit", limit)
            cursor?.let { parameter("cursor", it) }
        }.body()
    }

    /**
     * Create a payment request.
     * POST /pay-requests
     */
    suspend fun createPayRequest(request: CreatePayRequest): PayRequestResponse {
        return httpClient.post("pay-requests") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /**
     * Get a payment request by ID.
     * GET /pay-requests/:requestId
     */
    suspend fun getPayRequest(requestId: String): PayRequestDetail {
        return httpClient.get("pay-requests/$requestId").body()
    }

    /**
     * Look up a merchant by their Sui address.
     * GET /merchants/by-address/:suiAddress
     */
    suspend fun lookupMerchantByAddress(suiAddress: String): MerchantLookup? {
        return try {
            httpClient.get("merchants/by-address/$suiAddress").body<MerchantLookup>()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get a commerce proposal (intent) by transaction ID.
     * GET /intents/:txId
     */
    suspend fun getIntent(transactionId: String): CommerceProposal {
        return httpClient.get("intents/$transactionId").body()
    }

    /**
     * Request gas-sponsored P2P send transaction.
     * POST /transactions/gas-sponsor
     */
    suspend fun sponsorSend(request: GasSponsorSendRequest): GasSponsorResponse {
        return httpClient.post("transactions/gas-sponsor") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /**
     * Request gas-sponsored settlement transaction.
     * POST /transactions/gas-sponsor
     */
    suspend fun sponsorSettlement(request: GasSponsorSettlementRequest): GasSponsorResponse {
        return httpClient.post("transactions/gas-sponsor") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /**
     * Request gas-sponsored pool withdraw transaction.
     * POST /transactions/gas-sponsor
     */
    suspend fun sponsorPoolWithdraw(request: GasSponsorPoolWithdrawRequest): GasSponsorResponse {
        return httpClient.post("transactions/gas-sponsor") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /**
     * Submit a sponsored transaction with sender signature.
     * POST /transactions/submit
     */
    suspend fun submitSponsoredTx(request: SubmitTxRequest): SubmitTxResponse {
        return httpClient.post("transactions/submit") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /**
     * Submit a pool withdraw transaction (backend signs as both sender and gas owner).
     * POST /transactions/submit-pool
     */
    suspend fun submitPoolWithdraw(request: SubmitTxRequest): SubmitTxResponse {
        return httpClient.post("transactions/submit-pool") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}
