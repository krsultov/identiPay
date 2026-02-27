package com.identipay.wallet.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val txDigest: String,
    val type: String,                  // "send", "receive", "deposit"
    val amount: Long,                  // In micro-USDC (6 decimals)
    val counterpartyName: String? = null,
    val stealthAddress: String,
    val timestamp: Long = System.currentTimeMillis(),
    val encryptedPayload: String? = null,
)
