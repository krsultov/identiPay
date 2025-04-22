package com.identipay.identipaypos.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface IdentiPayPosApiService {

    @POST("api/transactions/offer")
    suspend fun createTransactionOffer(
        @Body request: CreateTransactionOfferRequestDto
    ): Response<TransactionOfferResponseDto>
}