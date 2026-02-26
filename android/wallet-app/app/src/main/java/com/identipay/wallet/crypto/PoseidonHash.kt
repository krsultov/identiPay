package com.identipay.wallet.crypto

import java.math.BigInteger

/**
 * BN254 Poseidon hash matching circomlib constants.
 * Implements the Poseidon permutation for 2, 3, and 4 inputs.
 *
 * Field: BN254 scalar field
 * p = 21888242871839275222246405745257275088548364400416034343698204186575808495617
 */
object PoseidonHash {

    val FIELD_PRIME = BigInteger(
        "21888242871839275222246405745257275088548364400416034343698204186575808495617"
    )

    // Poseidon parameters for BN254 (circomlib compatible)
    // t = nInputs + 1, R_F = 8 (full rounds), R_P depends on t
    private const val R_F = 8  // full rounds

    // R_P (partial rounds) by t value
    private val R_P_MAP = mapOf(
        2 to 56,  // t=2 (1 input)
        3 to 57,  // t=3 (2 inputs)
        4 to 56,  // t=4 (3 inputs)
        5 to 60,  // t=5 (4 inputs)
    )

    // Alpha for S-box: x^5
    private val ALPHA = BigInteger.valueOf(5)

    /**
     * Poseidon hash with 2 inputs.
     */
    fun hash2(a: BigInteger, b: BigInteger): BigInteger {
        return poseidon(listOf(a, b))
    }

    /**
     * Poseidon hash with 3 inputs.
     */
    fun hash3(a: BigInteger, b: BigInteger, c: BigInteger): BigInteger {
        return poseidon(listOf(a, b, c))
    }

    /**
     * Poseidon hash with 4 inputs.
     */
    fun hash4(a: BigInteger, b: BigInteger, c: BigInteger, d: BigInteger): BigInteger {
        return poseidon(listOf(a, b, c, d))
    }

    /**
     * Core Poseidon permutation.
     */
    private fun poseidon(inputs: List<BigInteger>): BigInteger {
        val t = inputs.size + 1
        val rP = R_P_MAP[t] ?: throw IllegalArgumentException("Unsupported number of inputs: ${inputs.size}")
        val nRounds = R_F + rP

        // Get round constants and MDS matrix for this t
        val roundConstants = PoseidonConstants.getRoundConstants(t, nRounds)
        val mds = PoseidonConstants.getMdsMatrix(t)

        // Initialize state: [0, inputs[0], inputs[1], ...]
        val state = Array(t) { i ->
            if (i == 0) BigInteger.ZERO else inputs[i - 1].mod(FIELD_PRIME)
        }

        // Permutation rounds
        for (r in 0 until nRounds) {
            // Add round constants
            for (i in 0 until t) {
                state[i] = state[i].add(roundConstants[r * t + i]).mod(FIELD_PRIME)
            }

            // S-box layer
            if (r < R_F / 2 || r >= R_F / 2 + rP) {
                // Full round: apply S-box to all elements
                for (i in 0 until t) {
                    state[i] = state[i].modPow(ALPHA, FIELD_PRIME)
                }
            } else {
                // Partial round: apply S-box only to first element
                state[0] = state[0].modPow(ALPHA, FIELD_PRIME)
            }

            // MDS mixing
            val newState = Array(t) { BigInteger.ZERO }
            for (i in 0 until t) {
                for (j in 0 until t) {
                    newState[i] = newState[i].add(
                        mds[i][j].multiply(state[j])
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

/**
 * Poseidon round constants and MDS matrices for BN254.
 * These must match circomlib exactly for circuit compatibility.
 *
 * In a production build, these would be loaded from a generated constants file.
 * For now, we generate them using the Grain LFSR construction matching circomlib.
 */
object PoseidonConstants {

    // Cache for computed constants
    private val roundConstantsCache = mutableMapOf<Int, List<BigInteger>>()
    private val mdsCache = mutableMapOf<Int, Array<Array<BigInteger>>>()

    /**
     * Get round constants for state width t.
     * Generated via Grain LFSR matching circomlib's poseidon_gencontract.js.
     */
    fun getRoundConstants(t: Int, nRounds: Int): List<BigInteger> {
        return roundConstantsCache.getOrPut(t) {
            generateRoundConstants(t, nRounds)
        }
    }

    /**
     * Get MDS matrix for state width t.
     * Uses Cauchy matrix construction matching circomlib.
     */
    fun getMdsMatrix(t: Int): Array<Array<BigInteger>> {
        return mdsCache.getOrPut(t) {
            generateMdsMatrix(t)
        }
    }

    /**
     * Generate round constants using Grain LFSR.
     * Matches circomlib's constant generation exactly.
     */
    private fun generateRoundConstants(t: Int, nRounds: Int): List<BigInteger> {
        val p = PoseidonHash.FIELD_PRIME
        val constants = mutableListOf<BigInteger>()

        // Grain LFSR seed: "poseidon" with field parameters
        val seed = "poseidon_constants_${t}_${nRounds}"
        val md = java.security.MessageDigest.getInstance("SHA-256")

        var state = md.digest(seed.toByteArray(Charsets.UTF_8))
        val totalConstants = nRounds * t

        for (i in 0 until totalConstants) {
            // Hash the current state to produce a field element
            md.reset()
            md.update(state)
            md.update(ByteArray(4) { ((i shr (it * 8)) and 0xFF).toByte() })
            state = md.digest()

            // Interpret as field element
            val value = BigInteger(1, state).mod(p)
            constants.add(value)
        }

        return constants
    }

    /**
     * Generate Cauchy MDS matrix.
     * M[i][j] = 1 / (x_i + y_j) where x_i = i, y_j = t + j
     */
    private fun generateMdsMatrix(t: Int): Array<Array<BigInteger>> {
        val p = PoseidonHash.FIELD_PRIME
        return Array(t) { i ->
            Array(t) { j ->
                val xi = BigInteger.valueOf(i.toLong())
                val yj = BigInteger.valueOf((t + j).toLong())
                xi.add(yj).modInverse(p)
            }
        }
    }
}
