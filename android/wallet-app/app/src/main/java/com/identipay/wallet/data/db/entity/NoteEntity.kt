package com.identipay.wallet.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a shielded pool note commitment stored locally.
 * Each note is created during a pool deposit and spent during a pool withdrawal.
 */
@Entity(tableName = "pool_notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteCommitment: String,       // Hex-encoded Poseidon commitment
    val amount: Long,                 // Amount in micro-USDC
    val ownerKey: String,             // Hex-encoded owner public key
    val salt: String,                 // Hex-encoded random salt used in commitment
    val leafIndex: Long,              // Position in the on-chain Merkle tree
    val isSpent: Boolean = false,
    val nullifier: String? = null,    // Hex-encoded nullifier (set when spent)
    val depositTxDigest: String,      // Sui tx digest of the deposit
    val withdrawTxDigest: String? = null, // Sui tx digest of the withdrawal
    val createdAt: Long = System.currentTimeMillis(),
)
