package com.identipay.wallet.crypto

import java.math.BigInteger
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identity commitment computation matching circuits/identity_registration.circom.
 *
 * commitment = Poseidon(issuerCertHash, docNumberHash, dobHash, userSalt)
 */
@Singleton
class IdentityCommitment @Inject constructor() {

    /**
     * Compute the identity commitment from hashed credential fields.
     * All inputs should already be BN254 field elements.
     */
    fun compute(
        issuerCertHash: BigInteger,
        docNumberHash: BigInteger,
        dobHash: BigInteger,
        userSalt: BigInteger,
    ): BigInteger {
        return PoseidonHash.hash4(issuerCertHash, docNumberHash, dobHash, userSalt)
    }

    /**
     * Hash a raw string credential field to a BN254 field element.
     * Uses SHA-256 and reduces mod the BN254 scalar field prime.
     */
    fun hashToField(value: String): BigInteger {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(value.toByteArray(Charsets.UTF_8))
        return BigInteger(1, hash).mod(PoseidonHash.FIELD_PRIME)
    }

    /**
     * Hash raw credential fields and compute the commitment.
     */
    fun computeFromRaw(
        issuerCert: ByteArray,
        docNumber: String,
        dateOfBirth: String,
        userSalt: BigInteger,
    ): BigInteger {
        val md = MessageDigest.getInstance("SHA-256")
        val issuerCertHash = BigInteger(1, md.digest(issuerCert)).mod(PoseidonHash.FIELD_PRIME)
        val docNumberHash = hashToField(docNumber)
        val dobHash = hashToField(dateOfBirth)
        return compute(issuerCertHash, docNumberHash, dobHash, userSalt)
    }

    /**
     * Generate a random user salt as a BN254 field element.
     */
    fun generateSalt(): BigInteger {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return BigInteger(1, bytes).mod(PoseidonHash.FIELD_PRIME)
    }
}
