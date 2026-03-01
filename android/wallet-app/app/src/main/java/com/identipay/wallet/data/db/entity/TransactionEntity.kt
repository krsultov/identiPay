package com.identipay.wallet.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val txDigest: String,
    val type: String,                  // "send", "receive", "commerce", "deposit"
    val amount: Long,                  // In micro-USDC (6 decimals)
    val counterpartyName: String? = null,
    val stealthAddress: String,
    val timestamp: Long = System.currentTimeMillis(),
    val encryptedPayload: String? = null,
    val buyerStealthAddress: String? = null,   // Buyer's one-time address where receipt/warranty are delivered
    val merchantPublicKey: String? = null,     // Merchant X25519 public key (hex) for artifact decryption
)
