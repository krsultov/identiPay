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
    val dobHash: String,
    val currentDate: String,
    val minAge: String,
    val identityCommitment: String,
) {
    fun toJson(): String = Json.encodeToString(this)
}
