package com.identipay.wallet.crypto

import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Identity commitment computation matching circuits/identity_registration.circom.
 *
 * commitment = Poseidon(issuerCertHash, personalNumberHash, dobHash, userSalt)
 */
@Singleton
class IdentityCommitment @Inject constructor() {

    /**
     * Compute the identity commitment from hashed credential fields.
     * All inputs should already be BN254 field elements.
     */
    fun compute(
        issuerCertHash: BigInteger,
        personalNumberHash: BigInteger,
        dobHash: BigInteger,
        userSalt: BigInteger,
    ): BigInteger {
        return PoseidonHash.hash4(issuerCertHash, personalNumberHash, dobHash, userSalt)
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
        personalNumber: String,
        dateOfBirth: String,
        userSalt: BigInteger,
    ): BigInteger {
        val md = MessageDigest.getInstance("SHA-256")
        val issuerCertHash = BigInteger(1, md.digest(issuerCert)).mod(PoseidonHash.FIELD_PRIME)
        val personalNumberHash = hashToField(personalNumber)
        val dobHash = hashToField(dateOfBirth)
        return compute(issuerCertHash, personalNumberHash, dobHash, userSalt)
    }

    /**
     * Derive the user salt deterministically from the master seed.
     * Same seed always produces the same salt, enabling reproducible
     * identity commitments without storing the salt separately.
     */
    fun deriveSaltFromSeed(seed: ByteArray): BigInteger {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(seed, "HmacSHA512"))
        val derived = mac.doFinal("identity-salt".toByteArray(Charsets.UTF_8))
        return BigInteger(1, derived.copyOfRange(0, 32)).mod(PoseidonHash.FIELD_PRIME)
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
