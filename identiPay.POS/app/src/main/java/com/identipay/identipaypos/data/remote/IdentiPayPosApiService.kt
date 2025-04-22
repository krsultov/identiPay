package com.identipay.identipaypos.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.UUID

data class TransactionDto(
    val id: UUID,
    val senderDid: String?,
    val status: String,
    val createdAt: String,
    val payload: TransactionPayloadDto?
)

data class TransactionPayloadDto(
    val id: UUID,
    val type: String,
    val recipientDid: String,
    val currency: String,
    val amount: Double,
    val metadataJson: String?
)

interface IdentiPayPosApiService {

    @POST("api/transactions/offer")
    suspend fun createTransactionOffer(
        @Body request: CreateTransactionOfferRequestDto
    ): Response<TransactionOfferResponseDto>

    @GET("api/transactions/{transactionId}")
    suspend fun getTransactionById(
        @Path("transactionId") transactionId: UUID
    ): Response<TransactionDto>
}