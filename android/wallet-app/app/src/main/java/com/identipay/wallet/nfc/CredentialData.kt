package com.identipay.wallet.nfc

import java.math.BigInteger

/**
 * Credential data extracted from an NFC passport reading.
 * Field elements are ready for use in ZK circuit inputs.
 */
data class CredentialData(
    val issuerCertHash: BigInteger,
    val personalNumberHash: BigInteger,
    val dobHash: BigInteger,
    val rawPersonalNumber: String,
    val rawDateOfBirth: String,
    val rawNationality: String,
    val rawIssuer: String,
    val issuerCertBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CredentialData) return false
        return issuerCertHash == other.issuerCertHash &&
                personalNumberHash == other.personalNumberHash &&
                dobHash == other.dobHash &&
                rawPersonalNumber == other.rawPersonalNumber
    }

    override fun hashCode(): Int {
        var result = issuerCertHash.hashCode()
        result = 31 * result + personalNumberHash.hashCode()
        result = 31 * result + dobHash.hashCode()
        result = 31 * result + rawPersonalNumber.hashCode()
        return result
    }
}
