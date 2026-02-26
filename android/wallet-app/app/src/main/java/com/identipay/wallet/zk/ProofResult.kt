package com.identipay.wallet.zk

/**
 * Parsed ZK proof result ready for Sui submission.
 *
 * Binary formats match what groth16::proof_points_from_bytes and
 * groth16::public_proof_inputs_from_bytes expect on Sui.
 */
data class ProofResult(
    val proofBytes: ByteArray,
    val publicInputsBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProofResult) return false
        return proofBytes.contentEquals(other.proofBytes) &&
                publicInputsBytes.contentEquals(other.publicInputsBytes)
    }

    override fun hashCode(): Int {
        return proofBytes.contentHashCode() * 31 + publicInputsBytes.contentHashCode()
    }
}
