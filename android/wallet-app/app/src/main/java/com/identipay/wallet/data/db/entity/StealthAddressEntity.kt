package com.identipay.wallet.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stealth_addresses")
data class StealthAddressEntity(
    @PrimaryKey
    val stealthAddress: String,
    val stealthPrivKeyEnc: String,    // Base64-encoded encrypted stealth private key
    val stealthPubkey: String,        // Hex-encoded stealth public key
    val ephemeralPubkey: String,      // Hex-encoded ephemeral public key
    val viewTag: Int,
    val balanceUsdc: Long = 0,        // Balance in micro-USDC (6 decimals)
    val createdAt: Long = System.currentTimeMillis(),
)
