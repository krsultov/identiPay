package com.identipay.wallet.crypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.math.BigInteger
import java.security.MessageDigest

object Ed25519Ops {

    // Ed25519 curve order
    private val L = BigInteger.valueOf(2).pow(252).add(
        BigInteger("27742317777372353535851937790883648493")
    )

    // Ed25519 base point (compressed encoding from RFC 8032)
    private val BASE_POINT_ENCODED: ByteArray by lazy {
        val b = ByteArray(32)
        // y = 4/5 mod p, little-endian encoding
        b[0] = 0x58
        for (i in 1 until 32) b[i] = 0x66
        b
    }

    // Decoded base point coordinates (cached)
    private val BASE_POINT: Pair<BigInteger, BigInteger> by lazy {
        Ed25519Field.decodePoint(BASE_POINT_ENCODED)
    }

    fun keyPairFromSeed(seed: ByteArray): Ed25519KeyPair {
        require(seed.size == 32) { "Seed must be 32 bytes" }
        val privateKey = Ed25519PrivateKeyParameters(seed, 0)
        val publicKey = privateKey.generatePublicKey()
        return Ed25519KeyPair(
            privateKey = seed,
            publicKey = publicKey.encoded,
        )
    }

    /**
     * Expand an Ed25519 seed into the actual signing scalar and nonce prefix.
     * scalar = clamp(SHA-512(seed)[0..32])
     * prefix = SHA-512(seed)[32..64]
     */
    fun expandSeed(seed: ByteArray): Pair<ByteArray, ByteArray> {
        require(seed.size == 32) { "Seed must be 32 bytes" }
        val h = MessageDigest.getInstance("SHA-512").digest(seed)
        val scalar = h.copyOfRange(0, 32)
        // Clamp per RFC 8032
        scalar[0] = (scalar[0].toInt() and 248).toByte()
        scalar[31] = (scalar[31].toInt() and 127).toByte()
        scalar[31] = (scalar[31].toInt() or 64).toByte()
        val prefix = h.copyOfRange(32, 64)
        return Pair(scalar, prefix)
    }

    /**
     * Compute scalar * G using raw Ed25519 scalar multiplication (NOT seed-based).
     * The scalar must be a 32-byte little-endian value.
     * Uses double-and-add with extended twisted Edwards coordinates.
     */
    fun rawScalarMultBase(scalar: ByteArray): ByteArray {
        require(scalar.size == 32) { "Scalar must be 32 bytes" }
        val field = Ed25519Field
        val (bx, by) = BASE_POINT

        // Convert scalar to BigInteger (little-endian)
        val k = BigInteger(1, scalar.reversedArray())

        // Double-and-add in extended coordinates (X:Y:Z:T)
        // Start with identity point (0, 1, 1, 0)
        var rX = BigInteger.ZERO
        var rY = BigInteger.ONE
        var rZ = BigInteger.ONE
        var rT = BigInteger.ZERO

        var pX = bx
        var pY = by
        var pZ = BigInteger.ONE
        var pT = field.mul(bx, by)

        // Process each bit
        for (i in 0 until 253) {
            if (k.testBit(i)) {
                // R = R + P
                val added = extAdd(rX, rY, rZ, rT, pX, pY, pZ, pT)
                rX = added[0]; rY = added[1]; rZ = added[2]; rT = added[3]
            }
            // P = 2 * P
            val doubled = extDouble(pX, pY, pZ, pT)
            pX = doubled[0]; pY = doubled[1]; pZ = doubled[2]; pT = doubled[3]
        }

        // Convert to affine and encode
        val zInv = field.inv(rZ)
        val xAffine = field.mul(rX, zInv)
        val yAffine = field.mul(rY, zInv)

        val result = ByteArray(32)
        field.encodePoint(xAffine, yAffine, result)
        return result
    }

    /**
     * Derive the public key from a raw Ed25519 scalar (not a seed).
     */
    fun publicKeyFromScalar(scalar: ByteArray): ByteArray = rawScalarMultBase(scalar)

    fun sign(message: ByteArray, privateKeySeed: ByteArray): ByteArray {
        val privateKey = Ed25519PrivateKeyParameters(privateKeySeed, 0)
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    /**
     * Sign a message using a raw Ed25519 scalar (not a seed).
     * Implements RFC 8032 Ed25519 signing with a deterministic nonce derived
     * from the scalar itself.
     *
     * @param message The message to sign
     * @param scalar The raw 32-byte Ed25519 scalar (little-endian)
     * @param publicKey The corresponding 32-byte public key
     */
    fun signWithScalar(message: ByteArray, scalar: ByteArray, publicKey: ByteArray): ByteArray {
        require(scalar.size == 32) { "Scalar must be 32 bytes" }
        require(publicKey.size == 32) { "Public key must be 32 bytes" }

        // Derive a deterministic nonce prefix from the scalar.
        // This replaces SHA-512(seed)[32..64] used in standard Ed25519.
        val noncePrefix = MessageDigest.getInstance("SHA-512").let { md ->
            md.update(scalar)
            md.update("identipay-stealth-nonce-v1".toByteArray(Charsets.UTF_8))
            md.digest()
        }

        // r = SHA-512(noncePrefix || message) mod L
        val rHash = MessageDigest.getInstance("SHA-512").let { md ->
            md.update(noncePrefix)
            md.update(message)
            md.digest()
        }
        val rBigInt = bytesToBigIntLE(rHash).mod(L)
        val rBytes = bigIntToBytesLE(rBigInt, 32)

        // R = r * G
        val R = rawScalarMultBase(rBytes)

        // k = SHA-512(R || publicKey || message) mod L
        val kHash = MessageDigest.getInstance("SHA-512").let { md ->
            md.update(R)
            md.update(publicKey)
            md.update(message)
            md.digest()
        }
        val kBigInt = bytesToBigIntLE(kHash).mod(L)

        // S = (r + k * scalar) mod L
        val scalarBigInt = bytesToBigIntLE(scalar)
        val sBigInt = rBigInt.add(kBigInt.multiply(scalarBigInt)).mod(L)
        val sBytes = bigIntToBytesLE(sBigInt, 32)

        // Signature = R || S (64 bytes)
        val sig = ByteArray(64)
        System.arraycopy(R, 0, sig, 0, 32)
        System.arraycopy(sBytes, 0, sig, 32, 32)
        return sig
    }

    fun verify(message: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        val pubKeyParams = Ed25519PublicKeyParameters(publicKey, 0)
        val verifier = Ed25519Signer()
        verifier.init(false, pubKeyParams)
        verifier.update(message, 0, message.size)
        return verifier.verifySignature(signature)
    }

    /**
     * Compute K_spend + scalar*G on Ed25519.
     * Uses raw scalar multiplication (NOT seed-based key derivation).
     * Returns the compressed 32-byte encoding.
     */
    fun pointAddScalarBase(spendPubkey: ByteArray, scalar: ByteArray): ByteArray {
        require(spendPubkey.size == 32) { "Public key must be 32 bytes" }
        require(scalar.size == 32) { "Scalar must be 32 bytes" }

        // Compute s*G using raw scalar multiplication
        val sG = rawScalarMultBase(scalar)

        // Add the two points using extended coordinate arithmetic
        val result = ByteArray(32)
        pointAddEncoded(spendPubkey, sG, result)
        return result
    }

    private fun bytesToBigIntLE(bytes: ByteArray): BigInteger {
        return BigInteger(1, bytes.reversedArray())
    }

    private fun bigIntToBytesLE(value: BigInteger, size: Int): ByteArray {
        val result = ByteArray(size)
        val bigEndian = value.toByteArray()
        for (i in bigEndian.indices) {
            val destIdx = bigEndian.size - 1 - i
            if (destIdx < size) {
                result[bigEndian.size - 1 - i] = bigEndian[i]
            }
        }
        return result
    }

    /**
     * Extended twisted Edwards point addition.
     * Input/output as (X, Y, Z, T) where x=X/Z, y=Y/Z, T=X*Y/Z.
     * Uses the unified addition formula for a=-1.
     */
    private fun extAdd(
        x1: BigInteger, y1: BigInteger, z1: BigInteger, t1: BigInteger,
        x2: BigInteger, y2: BigInteger, z2: BigInteger, t2: BigInteger,
    ): Array<BigInteger> {
        val field = Ed25519Field
        val a = field.mul(field.sub(y1, x1), field.sub(y2, x2))
        val b = field.mul(field.add(y1, x1), field.add(y2, x2))
        val c = field.mul(field.mul(t1, t2), field.mul(field.TWO, field.D))
        val d = field.mul(field.mul(z1, z2), field.TWO)
        val e = field.sub(b, a)
        val f = field.sub(d, c)
        val g = field.add(d, c)
        val h = field.add(b, a)

        val x3 = field.mul(e, f)
        val y3 = field.mul(g, h)
        val z3 = field.mul(f, g)
        val t3 = field.mul(e, h)
        return arrayOf(x3, y3, z3, t3)
    }

    /**
     * Extended twisted Edwards point doubling.
     * Uses the doubling formula for a=-1.
     */
    private fun extDouble(
        x1: BigInteger, y1: BigInteger, z1: BigInteger, @Suppress("UNUSED_PARAMETER") t1: BigInteger,
    ): Array<BigInteger> {
        val field = Ed25519Field
        val a = field.mul(x1, x1)       // A = X1^2
        val b = field.mul(y1, y1)       // B = Y1^2
        val c = field.mul(field.mul(z1, z1), field.TWO) // C = 2*Z1^2
        val dd = field.sub(field.P, a)  // D = -A (since a=-1 on Ed25519)
        val e = field.sub(field.sub(field.mul(field.add(x1, y1), field.add(x1, y1)), a), b) // E = (X1+Y1)^2 - A - B
        val g = field.add(dd, b)        // G = D + B
        val f = field.sub(g, c)         // F = G - C
        val h = field.sub(dd, b)        // H = D - B

        val x3 = field.mul(e, f)
        val y3 = field.mul(g, h)
        val z3 = field.mul(f, g)
        val t3 = field.mul(e, h)
        return arrayOf(x3, y3, z3, t3)
    }

    /**
     * Point addition from two encoded (compressed) points.
     * Both inputs are affine (Z=1).
     */
    private fun pointAddEncoded(p1Bytes: ByteArray, p2Bytes: ByteArray, result: ByteArray) {
        val field = Ed25519Field

        val (x1, y1) = field.decodePoint(p1Bytes)
        val (x2, y2) = field.decodePoint(p2Bytes)

        val t1 = field.mul(x1, y1)
        val t2 = field.mul(x2, y2)

        val added = extAdd(
            x1, y1, BigInteger.ONE, t1,
            x2, y2, BigInteger.ONE, t2,
        )

        val zInv = field.inv(added[2])
        val xAffine = field.mul(added[0], zInv)
        val yAffine = field.mul(added[1], zInv)

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
    val TWO: BigInteger = BigInteger.valueOf(2)
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
