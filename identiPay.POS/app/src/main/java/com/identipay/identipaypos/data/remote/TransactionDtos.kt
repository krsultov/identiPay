package com.identipay.identipaypos.data.remote

import com.google.gson.annotations.SerializedName
import java.util.UUID


enum class TransactionTypeDto { Payment, Transfer, Subscription, ValueAddedService }

data class CreateTransactionOfferRequestDto(
    @SerializedName("RecipientDid")
    val recipientDid: String,
    @SerializedName("TransactionType")
    val transactionType: TransactionTypeDto,
    @SerializedName("Amount")
    val amount: Double,
    @SerializedName("Currency")
    val currency: String,
    @SerializedName("MetadataJson")
    val metadataJson: String?
)

data class TransactionOfferResponseDto(
    @SerializedName("id")
    val id: UUID,
    @SerializedName("payloadId")
    val payloadId: UUID,
    @SerializedName("status")
    val status: String
)