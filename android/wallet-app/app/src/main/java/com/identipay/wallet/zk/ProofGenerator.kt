package com.identipay.wallet.zk

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates ZK proofs using snarkjs running inside an invisible WebView.
 *
 * Circuit WASM and zkey files must be placed in assets:
 * - assets/circuits/identity_registration.wasm
 * - assets/circuits/identity_registration_final.zkey
 */
@Singleton
class ProofGenerator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /**
     * Generate an identity registration proof.
     */
    suspend fun generateIdentityProof(
        input: IdentityRegistrationInput,
    ): ProofResult = withContext(Dispatchers.Main) {
        generateProof("identity_registration", input.toJson())
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun generateProof(circuitName: String, inputJson: String): ProofResult {
        val deferred = CompletableDeferred<ProofResult>()

        // Read circuit files from assets and base64-encode them on IO thread
        val (wasmBase64, zkeyBase64) = withContext(Dispatchers.IO) {
            val wasmBytes = context.assets.open("circuits/${circuitName}.wasm")
                .use { it.readBytes() }
            val zkeyBytes = context.assets.open("circuits/${circuitName}_final.zkey")
                .use { it.readBytes() }
            Pair(
                Base64.encodeToString(wasmBytes, Base64.NO_WRAP),
                Base64.encodeToString(zkeyBytes, Base64.NO_WRAP),
            )
        }

        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true

        val bridge = ProofBridge(deferred)
        webView.addJavascriptInterface(bridge, "ProofBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Inject circuit data as base64, then generate the proof.
                // Split into two evaluateJavascript calls to avoid URL length limits.
                val setDataJs = "setCircuitData('$wasmBase64','$zkeyBase64');"
                webView.evaluateJavascript(setDataJs) {
                    val generateJs = "generateProof($inputJson);"
                    webView.evaluateJavascript(generateJs, null)
                }
            }
        }

        webView.loadUrl("file:///android_asset/proof_bridge.html")

        return try {
            deferred.await()
        } finally {
            webView.destroy()
        }
    }

    private class ProofBridge(
        private val deferred: CompletableDeferred<ProofResult>,
    ) {
        @JavascriptInterface
        fun onProofResult(proofHex: String, publicInputsHex: String) {
            try {
                val proofBytes = hexToBytes(proofHex)
                val publicInputsBytes = hexToBytes(publicInputsHex)
                deferred.complete(ProofResult(proofBytes, publicInputsBytes))
            } catch (e: Exception) {
                deferred.completeExceptionally(e)
            }
        }

        @JavascriptInterface
        fun onProofError(error: String) {
            deferred.completeExceptionally(RuntimeException("Proof generation failed: $error"))
        }

        private fun hexToBytes(hex: String): ByteArray {
            val cleanHex = hex.removePrefix("0x")
            return ByteArray(cleanHex.length / 2) { i ->
                cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }
    }

    companion object {
        /**
         * Serialize a Groth16 proof to the binary format expected by Sui.
         * Sui's groth16 module uses arkworks which expects LITTLE-ENDIAN field elements.
         *
         * G1 point: 64 bytes (32 x LE, 32 y LE)
         * G2 point: 128 bytes (32 x.c0 LE, 32 x.c1 LE, 32 y.c0 LE, 32 y.c1 LE)
         *
         * Proof layout: A (G1, 64) + B (G2, 128) + C (G1, 64) = 256 bytes
         */
        fun serializeProofForSui(
            piA: List<String>,  // [x, y, "1"] affine G1 point
            piB: List<List<String>>,  // [[c1, c0], [c1, c0], ["1","0"]] G2
            piC: List<String>,  // [x, y, "1"] affine G1 point
        ): ByteArray {
            val result = ByteArray(256)
            var offset = 0

            // A (G1): x, y in little-endian
            decimalToBytesLE(piA[0], 32).copyInto(result, offset); offset += 32
            decimalToBytesLE(piA[1], 32).copyInto(result, offset); offset += 32

            // B (G2): snarkjs stores as [c1, c0]; arkworks Fq2 = c0 || c1
            decimalToBytesLE(piB[0][1], 32).copyInto(result, offset); offset += 32
            decimalToBytesLE(piB[0][0], 32).copyInto(result, offset); offset += 32
            decimalToBytesLE(piB[1][1], 32).copyInto(result, offset); offset += 32
            decimalToBytesLE(piB[1][0], 32).copyInto(result, offset); offset += 32

            // C (G1): x, y in little-endian
            decimalToBytesLE(piC[0], 32).copyInto(result, offset); offset += 32
            decimalToBytesLE(piC[1], 32).copyInto(result, offset)

            return result
        }

        /**
         * Serialize public inputs for Sui.
         * Each field element is 32 bytes little-endian (arkworks format).
         */
        fun serializePublicInputsForSui(inputs: List<String>): ByteArray {
            val result = ByteArray(inputs.size * 32)
            for ((i, input) in inputs.withIndex()) {
                decimalToBytesLE(input, 32).copyInto(result, i * 32)
            }
            return result
        }

        private fun decimalToBytesLE(decStr: String, numBytes: Int): ByteArray {
            val n = BigInteger(decStr)
            val bytes = ByteArray(numBytes)
            val raw = n.toByteArray() // big-endian, may have leading sign byte
            // Convert to little-endian: reverse and fit into numBytes
            for (i in 0 until minOf(raw.size, numBytes)) {
                bytes[i] = raw[raw.size - 1 - i]
            }
            return bytes
        }
    }
}
