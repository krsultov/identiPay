package com.identipay.wallet.network

import com.identipay.wallet.viewmodel.SignTransactionRequestDto
import com.identipay.wallet.viewmodel.TransactionDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.UUID

data class CreateUserRequestDto(
    val PrimaryKey: String
)

data class CreateUserResponseDto(
    val userId: String,
    val userDid: String,
)

interface IdentiPayApiService {
    @POST("api/users")
    suspend fun createUser(@Body request: CreateUserRequestDto): Response<CreateUserResponseDto>

    @GET("api/transactions/{transactionId}")
    suspend fun getTransactionById(
        @Path("transactionId") transactionId: UUID
    ): Response<TransactionDto>

    @POST("api/transactions/{transactionId}/sign")
    suspend fun signAndCompleteTransaction(
        @Path("transactionId") transactionId: UUID,
        @Body request: SignTransactionRequestDto
    ): Response<TransactionDto>
}
