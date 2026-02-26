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
    val timestamp: Long,
)

@Serializable
data class AnnouncementPage(
    val announcements: List<Announcement>,
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
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
}
