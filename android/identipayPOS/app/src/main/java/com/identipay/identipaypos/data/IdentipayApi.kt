package com.identipay.identipaypos.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object IdentipayApi {

    const val BACKEND_URL = "https://identipay.me"
    const val API_KEY = "REPLACE_WITH_YOUR_API_KEY"

    val backendHost: String
        get() = BACKEND_URL.removePrefix("http://").removePrefix("https://")

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    suspend fun createProposal(
        items: List<CartItem>,
        total: Double,
        ageGate: Int?,
    ): ProposalResponse = withContext(Dispatchers.IO) {
        val requestBody = ProposalRequest(
            items = items.map { item ->
                ProposalItem(
                    name = item.product.name,
                    quantity = item.quantity,
                    unitPrice = "%.2f".format(item.product.price),
                    currency = item.product.currency,
                )
            },
            amount = ProposalAmount(
                value = "%.2f".format(total),
                currency = "USDC",
            ),
            deliverables = ProposalDeliverables(receipt = true),
            constraints = if (ageGate != null && ageGate > 0) ProposalConstraints(ageGate = ageGate) else null,
            expiresInSeconds = 900,
        )

        val body = gson.toJson(requestBody).toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url("$BACKEND_URL/api/identipay/v1/proposals")
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        if (!response.isSuccessful) {
            throw Exception("Proposal creation failed (${response.code}): $responseBody")
        }

        gson.fromJson(responseBody, ProposalResponse::class.java)
    }
}

// ── Request models ──

data class ProposalRequest(
    val items: List<ProposalItem>,
    val amount: ProposalAmount,
    val deliverables: ProposalDeliverables,
    val constraints: ProposalConstraints? = null,
    val expiresInSeconds: Int,
)

data class ProposalItem(
    val name: String,
    val quantity: Int,
    val unitPrice: String,
    val currency: String,
)

data class ProposalAmount(
    val value: String,
    val currency: String,
)

data class ProposalDeliverables(
    val receipt: Boolean,
)

data class ProposalConstraints(
    val ageGate: Int,
)

// ── Response models ──

data class ProposalResponse(
    val transactionId: String,
    val intentHash: String,
    val qrDataUrl: String? = null,
    val uri: String,
    val expiresAt: String,
)
