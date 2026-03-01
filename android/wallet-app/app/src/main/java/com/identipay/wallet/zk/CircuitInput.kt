package com.identipay.wallet.zk

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigInteger

/**
 * Input data classes for ZK circuit proof generation.
 */

@Serializable
data class IdentityRegistrationInput(
    val issuerCertHash: String,
    @SerialName("docNumberHash")
    val personalNumberHash: String,
    val dobHash: String,
    val userSalt: String,
) {
    companion object {
        fun create(
            issuerCertHash: BigInteger,
            personalNumberHash: BigInteger,
            dobHash: BigInteger,
            userSalt: BigInteger,
        ): IdentityRegistrationInput {
            return IdentityRegistrationInput(
                issuerCertHash = issuerCertHash.toString(),
                personalNumberHash = personalNumberHash.toString(),
                dobHash = dobHash.toString(),
                userSalt = userSalt.toString(),
            )
        }
    }

    fun toJson(): String = Json.encodeToString(this)
}

@Serializable
data class AgeCheckInput(
    // Private signals
    val birthYear: String,
    val birthMonth: String,
    val birthDay: String,
    val dobHash: String,
    val userSalt: String,
    // Public signals
    val ageThreshold: String,
    val referenceDate: String,
    val identityCommitment: String,
    val intentHash: String,
) {
    companion object {
        fun create(
            birthYear: Int,
            birthMonth: Int,
            birthDay: Int,
            dobHash: BigInteger,
            userSalt: BigInteger,
            ageThreshold: Int,
            referenceDate: Int,
            identityCommitment: BigInteger,
            intentHash: BigInteger,
        ): AgeCheckInput {
            return AgeCheckInput(
                birthYear = birthYear.toString(),
                birthMonth = birthMonth.toString(),
                birthDay = birthDay.toString(),
                dobHash = dobHash.toString(),
                userSalt = userSalt.toString(),
                ageThreshold = ageThreshold.toString(),
                referenceDate = referenceDate.toString(),
                identityCommitment = identityCommitment.toString(),
                intentHash = intentHash.toString(),
            )
        }
    }

    fun toJson(): String = Json.encodeToString(this)
}

@Serializable
data class PoolSpendInput(
    // Private signals — names must match circom signal names exactly
    val noteAmount: String,
    val ownerKey: String,
    val salt: String,
    val pathElements: List<String>,
    val pathIndices: List<String>,
    // Public signals
    val merkleRoot: String,
    val nullifier: String,
    val withdrawAmount: String,
    val recipient: String,
    val changeCommitment: String,
) {
    fun toJson(): String = Json.encodeToString(this)
}
