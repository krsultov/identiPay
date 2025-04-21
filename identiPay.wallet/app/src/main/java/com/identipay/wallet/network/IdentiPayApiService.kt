package com.identipay.wallet.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

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
}