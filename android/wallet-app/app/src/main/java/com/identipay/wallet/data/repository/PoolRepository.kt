package com.identipay.wallet.data.repository

import android.util.Base64
import android.util.Log
import com.identipay.wallet.crypto.Ed25519Ops
import com.identipay.wallet.crypto.PoseidonHash
import com.identipay.wallet.data.db.dao.NoteDao
import com.identipay.wallet.data.db.dao.StealthAddressDao
import com.identipay.wallet.data.db.entity.NoteEntity
import com.identipay.wallet.data.preferences.WalletKeys
import com.identipay.wallet.network.BackendApi
import com.identipay.wallet.network.GasSponsorPoolWithdrawRequest
import com.identipay.wallet.network.GasSponsorSendRequest
import com.identipay.wallet.network.SubmitTxRequest
import com.identipay.wallet.network.SuiClientProvider
import com.identipay.wallet.network.toHexString
import com.identipay.wallet.zk.PoolSpendInput
import com.identipay.wallet.zk.ProofGenerator
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.delay
import java.math.BigInteger
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

sealed class PoolResult {
    data class Success(val txDigest: String, val noteId: Long = 0) : PoolResult()
    data class Error(val message: String) : PoolResult()
}

/**
 * Manages shielded pool deposits and withdrawals.
 *
 * The pool is used internally when a payment requires more USDC than any
 * single stealth address holds. Multiple stealth addresses deposit into
 * the pool, then a single withdrawal goes to a fresh stealth address,
 * breaking on-chain linkage between the source addresses.
 */
@Singleton
class PoolRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val stealthAddressDao: StealthAddressDao,
    private val walletKeys: WalletKeys,
    private val backendApi: BackendApi,
    private val proofGenerator: ProofGenerator,
    @param:Named("suiRpc") private val suiRpcClient: HttpClient,
) {
    companion object {
        private const val TAG = "PoolRepository"
        private const val TREE_DEPTH = 20
        private const val USDC_TYPE =
            "0xa1ec7fc00a6f40db9693ad1415d0c193ad3906494428cf252621037bd7117e29::usdc::USDC"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Deposit USDC from a stealth address into the shielded pool.
     *
     * 1. Compute note commitment: Poseidon(amount, ownerKey, salt)
     * 2. Build deposit PTB via gas sponsorship
     * 3. Sign and submit
     * 4. Store NoteEntity locally
     */
    suspend fun deposit(
        amount: Long,
        stealthAddress: String,
        stealthPrivKey: ByteArray,
    ): PoolResult {
        try {
            Log.d(TAG, "── DEPOSIT START ── amount=$amount, sender=$stealthAddress")

            // 1. Generate random salt for this note
            val saltBytes = ByteArray(31) // <32 bytes to stay within BN254 field
            SecureRandom().nextBytes(saltBytes)
            val salt = BigInteger(1, saltBytes).mod(PoseidonHash.FIELD_PRIME)

            // 2. Compute owner key (public key from stealth private key)
            val ownerPubkey = Ed25519Ops.publicKeyFromScalar(stealthPrivKey)
            val ownerKeyField = BigInteger(1, ownerPubkey).mod(PoseidonHash.FIELD_PRIME)

            // 3. Compute note commitment = Poseidon(amount, ownerKey, salt)
            val amountField = BigInteger.valueOf(amount)
            val noteCommitment = PoseidonHash.hash3(amountField, ownerKeyField, salt)

            Log.d(TAG, "  deposit inputs: noteAmount=$amount")
            Log.d(TAG, "  deposit inputs: ownerKeyField=${ownerKeyField.toString(10).take(20)}…")
            Log.d(TAG, "  deposit inputs: salt=${salt.toString(16).take(20)}…")
            Log.d(TAG, "  deposit commitment (local)=${noteCommitment.toString(10).take(30)}…")

            // 4. Commitment bytes sent to backend
            val commitBytes = noteCommitment.toByteArray()
            Log.d(TAG, "  commitment toByteArray().size=${commitBytes.size}, take(32).size=${commitBytes.take(32).size}")

            // 5. Build deposit transaction via gas sponsorship
            val txDigest = executePoolDeposit(
                senderAddress = stealthAddress,
                senderPrivKey = stealthPrivKey,
                amount = amount,
                noteCommitment = noteCommitment,
            )

            // 6. Get leaf index from deposit event (retry — event indexing may lag)
            var leafIndex: Long? = null
            for (attempt in 0 until 10) {
                leafIndex = fetchDepositLeafIndex(txDigest)
                if (leafIndex != null) break
                Log.d(TAG, "  deposit event not yet indexed, retry ${attempt + 1}/10")
                delay(1500L)
            }
            if (leafIndex == null) {
                return PoolResult.Error("Deposit succeeded but leaf index unavailable after retries")
            }

            // 7. Fetch on-chain commitment from the event to compare with local
            val onChainCommitment = fetchDepositCommitmentFromTx(txDigest)
            Log.d(TAG, "  deposit commitment (on-chain)=${onChainCommitment?.toString(10)?.take(30)}…")
            if (onChainCommitment != null && onChainCommitment != noteCommitment) {
                Log.e(TAG, "  ‼️ COMMITMENT MISMATCH: local≠onChain!")
                Log.e(TAG, "    local =${noteCommitment.toString(10)}")
                Log.e(TAG, "    onChain=${onChainCommitment.toString(10)}")
            }

            // 8. Compute nullifier = Poseidon(noteCommitment, ownerKeyField)
            val nullifier = PoseidonHash.hash2(noteCommitment, ownerKeyField)

            // 9. Store note locally
            val noteId = noteDao.insert(
                NoteEntity(
                    noteCommitment = noteCommitment.toString(16),
                    amount = amount,
                    ownerKey = ownerPubkey.toHexString(),
                    salt = salt.toString(16),
                    leafIndex = leafIndex,
                    depositTxDigest = txDigest,
                )
            )

            Log.d(TAG, "── DEPOSIT OK ── tx=$txDigest, leafIndex=$leafIndex")
            return PoolResult.Success(txDigest, noteId = noteId)
        } catch (e: Exception) {
            Log.e(TAG, "Deposit failed", e)
            return PoolResult.Error(e.message ?: "Deposit failed")
        }
    }

    /**
     * Withdraw USDC from the shielded pool to a recipient address.
     *
     * 1. Fetch Merkle proof for the note's leaf index
     * 2. Compute nullifier
     * 3. Generate ZK proof
     * 4. Build withdraw PTB via gas sponsorship
     * 5. Mark note as spent
     */
    suspend fun withdraw(
        noteId: Long,
        recipientAddress: String,
        amount: Long,
    ): PoolResult {
        try {
            Log.d(TAG, "── WITHDRAW START ── noteId=$noteId, amount=$amount, recipient=$recipientAddress")

            val notes = noteDao.getUnspent()
            val note = notes.find { it.id == noteId }
                ?: return PoolResult.Error("Note not found or already spent")

            Log.d(TAG, "  note from DB: id=${note.id}, amount=${note.amount}, leafIndex=${note.leafIndex}")
            Log.d(TAG, "  note commitment (hex)=${note.noteCommitment.take(20)}…")
            Log.d(TAG, "  note ownerKey (hex)=${note.ownerKey.take(20)}…")
            Log.d(TAG, "  note salt (hex)=${note.salt.take(20)}…")

            // 1. Reconstruct note fields
            val noteCommitment = BigInteger(note.noteCommitment, 16)
            val ownerKeyBytes = hexToBytes(note.ownerKey)
            val ownerKeyField = BigInteger(1, ownerKeyBytes).mod(PoseidonHash.FIELD_PRIME)
            val salt = BigInteger(note.salt, 16)

            // Recompute commitment to verify consistency
            val recomputedCommitment = PoseidonHash.hash3(
                BigInteger.valueOf(note.amount), ownerKeyField, salt
            )
            Log.d(TAG, "  noteCommitment (from DB hex)  =${noteCommitment.toString(10).take(30)}…")
            Log.d(TAG, "  recomputed Poseidon(amt,key,s) =${recomputedCommitment.toString(10).take(30)}…")
            if (noteCommitment != recomputedCommitment) {
                Log.e(TAG, "  ‼️ COMMITMENT RECOMPUTE MISMATCH — stored≠Poseidon(inputs)")
                Log.e(TAG, "    stored    =${noteCommitment.toString(10)}")
                Log.e(TAG, "    recomputed=${recomputedCommitment.toString(10)}")
                return PoolResult.Error(
                    "Note $noteId has corrupted data: commitment doesn't match stored inputs"
                )
            }

            // 2. Compute nullifier
            val nullifier = PoseidonHash.hash2(noteCommitment, ownerKeyField)
            Log.d(TAG, "  nullifier=${nullifier.toString(10).take(30)}…")

            // 3. Get Merkle proof + root, retrying until the rebuilt tree
            //    matches the on-chain root (event indexing may lag after deposits)
            val (merklePath, merkleRoot) = run {
                for (attempt in 0 until 10) {
                    val onChainRoot = fetchMerkleRoot()
                    val (path, computedRoot) = getMerkleProofAndRoot(note.leafIndex)
                    if (computedRoot == onChainRoot) {
                        Log.d(TAG, "  merkle roots match on attempt ${attempt + 1}")
                        return@run Pair(path, onChainRoot)
                    }
                    Log.d(TAG, "  merkle root mismatch (attempt ${attempt + 1}/10):")
                    Log.d(TAG, "    computed =${computedRoot.toString(10).take(30)}…")
                    Log.d(TAG, "    onChain  =${onChainRoot.toString(10).take(30)}…")
                    delay(2000L)
                }
                return PoolResult.Error("Merkle tree not synced after retries")
            }

            Log.d(TAG, "  merkleRoot=${merkleRoot.toString(10).take(30)}…")
            Log.d(TAG, "  merklePath[0]=${merklePath.getOrNull(0)?.toString(10)?.take(30)}…")
            Log.d(TAG, "  merklePath[1]=${merklePath.getOrNull(1)?.toString(10)?.take(30)}…")

            // 5. Compute change commitment (if withdrawing less than full note)
            val changeAmount = note.amount - amount
            var changeCommitment = BigInteger.ZERO
            if (changeAmount > 0) {
                val changeSaltBytes = ByteArray(31)
                SecureRandom().nextBytes(changeSaltBytes)
                val changeSalt = BigInteger(1, changeSaltBytes).mod(PoseidonHash.FIELD_PRIME)
                changeCommitment = PoseidonHash.hash3(
                    BigInteger.valueOf(changeAmount),
                    ownerKeyField,
                    changeSalt,
                )
            }
            Log.d(TAG, "  changeAmount=$changeAmount, changeCommitment=${changeCommitment.toString(10).take(20)}…")

            // 6. Build circuit input (field names must match circom signals)
            val pathIndices = computePathIndices(note.leafIndex, TREE_DEPTH)
            Log.d(TAG, "  pathIndices[0..4]=${pathIndices.take(5)}")

            val poolSpendInput = PoolSpendInput(
                noteAmount = note.amount.toString(),
                ownerKey = ownerKeyField.toString(),
                salt = salt.toString(),
                pathElements = merklePath.map { it.toString() },
                pathIndices = pathIndices.map { it.toString() },
                merkleRoot = merkleRoot.toString(),
                nullifier = nullifier.toString(),
                withdrawAmount = amount.toString(),
                recipient = addressToField(recipientAddress).toString(),
                changeCommitment = changeCommitment.toString(),
            )

            Log.d(TAG, "  circuit input JSON (first 500 chars):")
            Log.d(TAG, "  ${poolSpendInput.toJson().take(500)}")

            // 7. Generate ZK proof
            Log.d(TAG, "  generating ZK proof…")
            val proofResult = proofGenerator.generatePoolProof(poolSpendInput)

            // 8. Build and submit withdraw transaction
            val txDigest = executePoolWithdraw(
                nullifier = nullifier,
                recipient = recipientAddress,
                amount = amount,
                changeCommitment = changeCommitment,
                proof = proofResult.proofBytes,
                publicInputs = proofResult.publicInputsBytes,
            )

            // 9. Mark note as spent
            noteDao.markSpent(noteId, nullifier.toString(16), txDigest)

            Log.d(TAG, "Withdraw succeeded: $txDigest")
            return PoolResult.Success(txDigest)
        } catch (e: Exception) {
            Log.e(TAG, "Withdraw failed", e)
            return PoolResult.Error(e.message ?: "Withdraw failed")
        }
    }

    /**
     * Withdraw all unclaimed notes from the pool to a single recipient address.
     *
     * Iterates over every unspent note and withdraws its full amount.
     * Returns the total amount withdrawn, or an error on the first failure.
     */
    suspend fun withdrawAll(recipientAddress: String): PoolResult {
        val notes = noteDao.getUnspent()
        if (notes.isEmpty()) {
            return PoolResult.Error("No unspent notes in pool")
        }

        var totalWithdrawn = 0L
        for (note in notes) {
            val result = withdraw(
                noteId = note.id,
                recipientAddress = recipientAddress,
                amount = note.amount,
            )
            when (result) {
                is PoolResult.Success -> totalWithdrawn += note.amount
                is PoolResult.Error -> return PoolResult.Error(
                    "Withdrew $totalWithdrawn before failure on note ${note.id}: ${result.message}"
                )
            }
        }

        Log.d(TAG, "withdrawAll: withdrew $totalWithdrawn from ${notes.size} notes to $recipientAddress")
        return PoolResult.Success("withdrew_all:$totalWithdrawn")
    }

    /**
     * Get total unspent balance in the pool.
     */
    suspend fun getTotalUnspent(): Long = noteDao.getTotalUnspent() ?: 0L

    /**
     * Get all unspent note entities (for iterating withdrawals).
     */
    suspend fun getUnspentNotes(): List<NoteEntity> = noteDao.getUnspent()

    /**
     * Get specific unspent notes by their IDs.
     */
    suspend fun getNotesByIds(ids: List<Long>): List<NoteEntity> = noteDao.getByIds(ids)

    /**
     * Reconstruct Merkle proof by replaying all deposit events into an
     * incremental Merkle tree (mirroring the on-chain insert_leaf), then
     * extracting the sibling path for [leafIndex] from the final tree state.
     *
     * Returns both the sibling path AND the computed root so the caller can
     * verify consistency with the on-chain root before generating a ZK proof.
     */
    suspend fun getMerkleProofAndRoot(leafIndex: Long): Pair<List<BigInteger>, BigInteger> {
        val commitments = fetchAllDepositCommitments()
        Log.d(TAG, "  getMerkleProofAndRoot: ${commitments.size} commitments from events, leafIndex=$leafIndex")
        for ((i, c) in commitments.withIndex()) {
            Log.d(TAG, "    event commitment[$i]=${c.toString(10).take(30)}…")
        }

        // Build zero hashes for each level
        val zeroHashes = mutableListOf<BigInteger>()
        var zh = BigInteger.ZERO
        for (i in 0 until TREE_DEPTH) {
            zeroHashes.add(zh)
            zh = PoseidonHash.hash2(zh, zh)
        }
        Log.d(TAG, "    zeroHash[0]=${zeroHashes[0]} (should be 0)")
        Log.d(TAG, "    zeroHash[1]=Poseidon(0,0)=${zeroHashes[1].toString(10).take(30)}…")

        // Store every node hash at every level so we can look up siblings.
        // nodes[level][position] = hash
        // Level 0 = leaves, Level 1 = first internal level, etc.
        val nodes = Array(TREE_DEPTH + 1) { mutableMapOf<Long, BigInteger>() }

        // Insert each leaf and propagate up
        for ((insertIdx, commitment) in commitments.withIndex()) {
            var currentIndex = insertIdx.toLong()
            var currentHash = commitment
            nodes[0][currentIndex] = currentHash

            for (level in 0 until TREE_DEPTH) {
                val parentIndex = currentIndex / 2
                val isLeft = currentIndex % 2 == 0L
                val siblingIndex = if (isLeft) currentIndex + 1 else currentIndex - 1

                val sibling = nodes[level][siblingIndex] ?: zeroHashes[level]
                currentHash = if (isLeft) {
                    PoseidonHash.hash2(currentHash, sibling)
                } else {
                    PoseidonHash.hash2(sibling, currentHash)
                }

                currentIndex = parentIndex
                nodes[level + 1][currentIndex] = currentHash
            }
        }

        // Computed root is at the top of the tree
        val computedRoot = nodes[TREE_DEPTH][0] ?: zh // zh is the all-zero root if tree is empty

        // Extract siblings for leafIndex from the final tree state
        val siblings = mutableListOf<BigInteger>()
        var idx = leafIndex
        for (level in 0 until TREE_DEPTH) {
            val siblingIndex = if (idx % 2 == 0L) idx + 1 else idx - 1
            val sibling = nodes[level][siblingIndex] ?: zeroHashes[level]
            siblings.add(sibling)
            idx /= 2
        }

        return Pair(siblings, computedRoot)
    }

    @Deprecated("Use getMerkleProofAndRoot instead", ReplaceWith("getMerkleProofAndRoot(leafIndex).first"))
    suspend fun getMerkleProof(leafIndex: Long): List<BigInteger> = getMerkleProofAndRoot(leafIndex).first

    /**
     * Fetch all DepositEvent note_commitments from the shielded pool, ordered by leaf_index.
     */
    private suspend fun fetchAllDepositCommitments(): List<BigInteger> {
        val commitments = mutableListOf<BigInteger>()
        var cursorJson = "null"
        val eventType = "${SuiClientProvider.PACKAGE_ID}::shielded_pool::DepositEvent"

        while (true) {
            val body = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "suix_queryEvents",
                    "params": [{"MoveEventType": "$eventType"}, $cursorJson, 50, false]
                }
            """.trimIndent()

            val response = suiRpcClient.post(SuiClientProvider.SUI_TESTNET_URL) {
                setBody(TextContent(body, ContentType.Application.Json))
            }.body<String>()

            val parsed = json.parseToJsonElement(response).jsonObject
            val result = parsed["result"]?.jsonObject ?: break
            val data = result["data"]?.jsonArray ?: break

            for (event in data) {
                val parsedJson = event.jsonObject["parsedJson"]?.jsonObject ?: continue
                val commitStr = parsedJson["note_commitment"]?.jsonPrimitive?.content ?: continue
                commitments.add(BigInteger(commitStr))
            }

            val hasNext = result["hasNextPage"]?.jsonPrimitive?.content?.toBoolean() ?: false
            if (!hasNext) break

            val nextCursor = result["nextCursor"]?.jsonObject ?: break
            val txDigest = nextCursor["txDigest"]?.jsonPrimitive?.content ?: break
            val eventSeq = nextCursor["eventSeq"]?.jsonPrimitive?.content ?: break
            cursorJson = """{"txDigest":"$txDigest","eventSeq":"$eventSeq"}"""
        }

        return commitments
    }

    private fun computePathIndices(leafIndex: Long, depth: Int): List<Int> {
        val indices = mutableListOf<Int>()
        var idx = leafIndex
        for (i in 0 until depth) {
            indices.add((idx % 2).toInt())
            idx /= 2
        }
        return indices
    }

    // ── Transaction execution ──

    private suspend fun executePoolDeposit(
        senderAddress: String,
        senderPrivKey: ByteArray,
        amount: Long,
        noteCommitment: BigInteger,
    ): String {
        // Request gas-sponsored deposit transaction from backend
        val sponsorResponse = backendApi.sponsorSend(
            GasSponsorSendRequest(
                type = "pool_deposit",
                senderAddress = senderAddress,
                amount = amount.toString(),
                recipient = "", // Pool address resolved by backend
                coinType = USDC_TYPE,
                ephemeralPubkey = noteCommitment.toByteArray()
                    .take(32)
                    .map { it.toInt() and 0xFF },
                viewTag = 0,
            )
        )

        val signature = signTransaction(sponsorResponse.txBytes, senderPrivKey)
        val submitResponse = backendApi.submitSponsoredTx(
            SubmitTxRequest(txBytes = sponsorResponse.txBytes, senderSignature = signature)
        )
        return submitResponse.txDigest
    }

    private suspend fun executePoolWithdraw(
        nullifier: BigInteger,
        recipient: String,
        amount: Long,
        changeCommitment: BigInteger,
        proof: ByteArray,
        publicInputs: ByteArray,
    ): String {
        // Convert BigIntegers to byte arrays for the request
        val nullifierBytes = bigIntToBytes(nullifier, 32)
        val changeBytes = bigIntToBytes(changeCommitment, 32)

        // Pool withdrawals are fully backend-signed (admin is sender + gas owner)
        val sponsorResponse = backendApi.sponsorPoolWithdraw(
            GasSponsorPoolWithdrawRequest(
                senderAddress = recipient,
                coinType = USDC_TYPE,
                amount = amount.toString(),
                recipient = recipient,
                nullifier = nullifierBytes.map { it.toInt() and 0xFF },
                changeCommitment = changeBytes.map { it.toInt() and 0xFF },
                zkProof = proof.map { it.toInt() and 0xFF },
                zkPublicInputs = publicInputs.map { it.toInt() and 0xFF },
            )
        )

        // Backend signs as both sender and gas owner for pool withdrawals
        val submitResponse = backendApi.submitPoolWithdraw(
            SubmitTxRequest(
                txBytes = sponsorResponse.txBytes,
                senderSignature = "",
            )
        )
        return submitResponse.txDigest
    }

    private fun bigIntToBytes(value: BigInteger, size: Int): ByteArray {
        val result = ByteArray(size)
        val raw = value.toByteArray()
        // BigInteger is big-endian with possible leading sign byte
        val start = if (raw.size > size) raw.size - size else 0
        val destStart = if (raw.size < size) size - raw.size else 0
        System.arraycopy(raw, start, result, destStart, minOf(raw.size, size))
        return result
    }

    private suspend fun fetchDepositCommitmentFromTx(txDigest: String): BigInteger? {
        return try {
            val body = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "sui_getTransactionBlock",
                    "params": ["$txDigest", {"showEvents": true}]
                }
            """.trimIndent()
            val response = suiRpcClient.post(SuiClientProvider.SUI_TESTNET_URL) {
                setBody(TextContent(body, ContentType.Application.Json))
            }.body<String>()
            val parsed = json.parseToJsonElement(response).jsonObject
            val events = parsed["result"]?.jsonObject?.get("events")?.jsonArray ?: return null
            for (event in events) {
                val eventType = event.jsonObject["type"]?.jsonPrimitive?.content ?: continue
                if (eventType.contains("DepositEvent")) {
                    val parsedJson = event.jsonObject["parsedJson"]?.jsonObject ?: continue
                    val commitStr = parsedJson["note_commitment"]?.jsonPrimitive?.content ?: continue
                    return BigInteger(commitStr)
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch deposit commitment from tx", e)
            null
        }
    }

    private suspend fun fetchDepositLeafIndex(txDigest: String): Long? {
        return try {
            val body = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "sui_getTransactionBlock",
                    "params": ["$txDigest", {"showEvents": true}]
                }
            """.trimIndent()

            val response = suiRpcClient.post(SuiClientProvider.SUI_TESTNET_URL) {
                setBody(TextContent(body, ContentType.Application.Json))
            }.body<String>()

            val parsed = json.parseToJsonElement(response).jsonObject
            val events = parsed["result"]?.jsonObject?.get("events")?.jsonArray ?: return null
            for (event in events) {
                val eventType = event.jsonObject["type"]?.jsonPrimitive?.content ?: continue
                if (eventType.contains("DepositEvent")) {
                    val parsedJson = event.jsonObject["parsedJson"]?.jsonObject ?: continue
                    return parsedJson["leaf_index"]?.jsonPrimitive?.content?.toLong()
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch deposit leaf index", e)
            null
        }
    }

    private suspend fun fetchMerkleRoot(): BigInteger {
        return try {
            val body = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "sui_getObject",
                    "params": ["${SuiClientProvider.SHIELDED_POOL_ID}", {"showContent": true}]
                }
            """.trimIndent()

            val response = suiRpcClient.post(SuiClientProvider.SUI_TESTNET_URL) {
                setBody(TextContent(body, ContentType.Application.Json))
            }.body<String>()

            val parsed = json.parseToJsonElement(response).jsonObject
            val fields = parsed["result"]?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("fields")?.jsonObject
                ?: return BigInteger.ZERO

            val rootStr = fields["merkle_root"]?.jsonPrimitive?.content
                ?: return BigInteger.ZERO
            BigInteger(rootStr)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Merkle root", e)
            BigInteger.ZERO
        }
    }

    private fun signTransaction(txBytes: String, stealthScalar: ByteArray): String {
        val txData = Base64.decode(txBytes, Base64.NO_WRAP)

        val intentMessage = ByteArray(3 + txData.size)
        intentMessage[0] = 0x00
        intentMessage[1] = 0x00
        intentMessage[2] = 0x00
        System.arraycopy(txData, 0, intentMessage, 3, txData.size)

        val digest = org.bouncycastle.crypto.digests.Blake2bDigest(256)
        digest.update(intentMessage, 0, intentMessage.size)
        val txDigestBytes = ByteArray(32)
        digest.doFinal(txDigestBytes, 0)

        val pubkey = Ed25519Ops.publicKeyFromScalar(stealthScalar)
        val signature = Ed25519Ops.signWithScalar(txDigestBytes, stealthScalar, pubkey)

        val suiSig = ByteArray(97)
        suiSig[0] = 0x00
        System.arraycopy(signature, 0, suiSig, 1, 64)
        System.arraycopy(pubkey, 0, suiSig, 65, 32)

        return Base64.encodeToString(suiSig, Base64.NO_WRAP)
    }

    private fun addressToField(address: String): BigInteger {
        val clean = if (address.startsWith("0x")) address.substring(2) else address
        return BigInteger(clean, 16).mod(PoseidonHash.FIELD_PRIME)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.startsWith("0x")) hex.substring(2) else hex
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
