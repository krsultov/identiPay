package com.identipay.wallet.zk

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
    val docNumberHash: String,
    val dobHash: String,
    val userSalt: String,
) {
    companion object {
        fun create(
            issuerCertHash: BigInteger,
            docNumberHash: BigInteger,
            dobHash: BigInteger,
            userSalt: BigInteger,
        ): IdentityRegistrationInput {
            return IdentityRegistrationInput(
                issuerCertHash = issuerCertHash.toString(),
                docNumberHash = docNumberHash.toString(),
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
