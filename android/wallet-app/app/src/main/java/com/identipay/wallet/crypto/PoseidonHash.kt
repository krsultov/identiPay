package com.identipay.wallet.crypto

import java.math.BigInteger

/**
 * BN254 Poseidon hash matching circomlib and sui::poseidon::poseidon_bn254.
 * Uses hardcoded round constants and MDS matrices extracted from circomlib.
 *
 * Field: BN254 scalar field
 * p = 21888242871839275222246405745257275088548364400416034343698204186575808495617
 *
 * Parameters (from circomlib reference implementation):
 *   N_ROUNDS_F = 8 (full rounds)
 *   N_ROUNDS_P = [56, 57, 56, 60, ...] (partial rounds, indexed by t-2)
 *   S-box: x^5
 */
object PoseidonHash {

    val FIELD_PRIME = BigInteger(
        "21888242871839275222246405745257275088548364400416034343698204186575808495617"
    )

    private const val N_ROUNDS_F = 8
    // Partial rounds indexed by (t - 2), matching circomlib
    private val N_ROUNDS_P = intArrayOf(56, 57, 56, 60, 60, 63, 64, 63, 60, 66, 60, 65, 70, 60, 64, 68)

    private val FIVE = BigInteger.valueOf(5)

    fun hash2(a: BigInteger, b: BigInteger): BigInteger = poseidon(listOf(a, b))

    fun hash3(a: BigInteger, b: BigInteger, c: BigInteger): BigInteger = poseidon(listOf(a, b, c))

    fun hash4(a: BigInteger, b: BigInteger, c: BigInteger, d: BigInteger): BigInteger = poseidon(listOf(a, b, c, d))

    /**
     * Core Poseidon permutation matching circomlib's reference implementation exactly.
     */
    private fun poseidon(inputs: List<BigInteger>): BigInteger {
        val t = inputs.size + 1
        val nRoundsP = N_ROUNDS_P[t - 2]
        val nRounds = N_ROUNDS_F + nRoundsP

        val C = when (t) {
            3 -> PoseidonConstants.C3
            4 -> PoseidonConstants.C4
            5 -> PoseidonConstants.C5
            else -> throw IllegalArgumentException("Unsupported t=$t (${inputs.size} inputs)")
        }
        val M = when (t) {
            3 -> PoseidonConstants.M3
            4 -> PoseidonConstants.M4
            5 -> PoseidonConstants.M5
            else -> throw IllegalArgumentException("Unsupported t=$t (${inputs.size} inputs)")
        }

        // Initialize state: [0, inputs[0], inputs[1], ...]
        val state = Array(t) { i ->
            if (i == 0) BigInteger.ZERO else inputs[i - 1].mod(FIELD_PRIME)
        }

        for (r in 0 until nRounds) {
            // Add round constants
            for (i in 0 until t) {
                state[i] = state[i].add(C[r * t + i]).mod(FIELD_PRIME)
            }

            // S-box layer
            if (r < N_ROUNDS_F / 2 || r >= N_ROUNDS_F / 2 + nRoundsP) {
                // Full round: apply x^5 to all elements
                for (i in 0 until t) {
                    state[i] = state[i].modPow(FIVE, FIELD_PRIME)
                }
            } else {
                // Partial round: apply x^5 only to first element
                state[0] = state[0].modPow(FIVE, FIELD_PRIME)
            }

            // MDS mixing
            val newState = Array(t) { BigInteger.ZERO }
            for (i in 0 until t) {
                for (j in 0 until t) {
                    newState[i] = newState[i].add(
                        M[i][j].multiply(state[j])
                    ).mod(FIELD_PRIME)
                }
            }
            for (i in 0 until t) {
                state[i] = newState[i]
            }
        }

        return state[0]
    }
}
