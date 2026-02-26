package com.identipay.wallet.crypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.math.BigInteger

object Ed25519Ops {

    fun keyPairFromSeed(seed: ByteArray): Ed25519KeyPair {
        require(seed.size == 32) { "Seed must be 32 bytes" }
        val privateKey = Ed25519PrivateKeyParameters(seed, 0)
        val publicKey = privateKey.generatePublicKey()
        return Ed25519KeyPair(
            privateKey = seed,
            publicKey = publicKey.encoded,
        )
    }

    fun sign(message: ByteArray, privateKeySeed: ByteArray): ByteArray {
        val privateKey = Ed25519PrivateKeyParameters(privateKeySeed, 0)
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        val pubKeyParams = Ed25519PublicKeyParameters(publicKey, 0)
        val verifier = Ed25519Signer()
        verifier.init(false, pubKeyParams)
        verifier.update(message, 0, message.size)
        return verifier.verifySignature(signature)
    }

    /**
     * Compute K_spend + scalar*G on Ed25519 using pure BigInteger field arithmetic.
     * Returns the compressed 32-byte encoding.
     */
    fun pointAddScalarBase(spendPubkey: ByteArray, scalar: ByteArray): ByteArray {
        require(spendPubkey.size == 32) { "Public key must be 32 bytes" }
        require(scalar.size == 32) { "Scalar must be 32 bytes" }

        // Compute s*G by creating a keypair from the scalar
        val sPriv = Ed25519PrivateKeyParameters(scalar, 0)
        val sG = sPriv.generatePublicKey().encoded

        // Add the two points using extended coordinate arithmetic
        val result = ByteArray(32)
        pointAddExtended(spendPubkey, sG, result)
        return result
    }

    /**
     * Ed25519 point addition in extended twisted Edwards coordinates.
     * Uses the unified addition formula.
     */
    private fun pointAddExtended(p1Bytes: ByteArray, p2Bytes: ByteArray, result: ByteArray) {
        val field = Ed25519Field

        val (x1, y1) = field.decodePoint(p1Bytes)
        val (x2, y2) = field.decodePoint(p2Bytes)

        // Extended coordinates with Z=1, T=X*Y
        val t1 = field.mul(x1, y1)
        val t2 = field.mul(x2, y2)

        // Unified addition: a=-1, d=d_ed25519
        val a = field.mul(field.sub(y1, x1), field.sub(y2, x2))
        val b = field.mul(field.add(y1, x1), field.add(y2, x2))
        val c = field.mul(field.mul(t1, t2), field.mul(field.TWO, field.D))
        val e = field.sub(b, a)
        val f = field.sub(field.TWO, c) // Z1*Z2=1, so 2*Z1*Z2=2
        val g = field.add(field.TWO, c)
        val h = field.add(b, a)

        val x3 = field.mul(e, f)
        val y3 = field.mul(g, h)
        val z3 = field.mul(f, g)

        val zInv = field.inv(z3)
        val xAffine = field.mul(x3, zInv)
        val yAffine = field.mul(y3, zInv)

        field.encodePoint(xAffine, yAffine, result)
    }
}

/**
 * Field arithmetic for Ed25519 (mod p = 2^255 - 19).
 */
internal object Ed25519Field {
    val P = BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
    val D = BigInteger("52036cee2b6ffe738cc740797779e89800700a4d4141d8ab75eb4dca135978a3", 16)
    val ONE: BigInteger = BigInteger.ONE
    val TWO: BigInteger = BigInteger.TWO
    private val SQRT_M1 = BigInteger("2b8324804fc1df0b2b4d00993dfbd7a72f431806ad2fe478c4ee1b274a0ea0b0", 16)

    fun add(a: BigInteger, b: BigInteger): BigInteger = a.add(b).mod(P)
    fun sub(a: BigInteger, b: BigInteger): BigInteger = a.subtract(b).mod(P)
    fun mul(a: BigInteger, b: BigInteger): BigInteger = a.multiply(b).mod(P)
    fun inv(a: BigInteger): BigInteger = a.modPow(P.subtract(TWO), P)

    fun decodePoint(bytes: ByteArray): Pair<BigInteger, BigInteger> {
        val copy = bytes.copyOf()
        val xSign = (copy[31].toInt() shr 7) and 1
        copy[31] = (copy[31].toInt() and 0x7F).toByte()

        val yBytes = copy.reversedArray()
        val y = BigInteger(1, yBytes)

        val y2 = mul(y, y)
        val u = sub(y2, ONE)
        val v = add(mul(D, y2), ONE)
        var x = recoverX(u, v)

        if (x.testBit(0) != (xSign == 1)) {
            x = P.subtract(x)
        }

        return Pair(x, y)
    }

    private fun recoverX(u: BigInteger, v: BigInteger): BigInteger {
        val vInv = inv(v)
        val x2 = mul(u, vInv)

        val exp = P.add(BigInteger.valueOf(3)).shiftRight(3)
        var x = x2.modPow(exp, P)

        if (mul(x, x) != x2.mod(P)) {
            x = mul(x, SQRT_M1)
        }

        if (mul(x, x) != x2.mod(P)) {
            throw IllegalArgumentException("Invalid Ed25519 point encoding")
        }

        return x
    }

    fun encodePoint(x: BigInteger, y: BigInteger, result: ByteArray) {
        val yBytes = y.toByteArray()
        for (i in 0 until 32) {
            val srcIdx = yBytes.size - 1 - i
            result[i] = if (srcIdx >= 0) yBytes[srcIdx] else 0
        }
        if (x.testBit(0)) {
            result[31] = (result[31].toInt() or 0x80).toByte()
        }
    }
}
