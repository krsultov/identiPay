package com.identipay.wallet.crypto

import com.identipay.wallet.network.CommerceProposal
import org.bouncycastle.jcajce.provider.digest.SHA3
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes the intent hash of a CommerceProposal using JSON-LD URDNA2015
 * canonicalization followed by SHA3-256 hashing.
 *
 * Matches the backend logic in proposal.service.ts exactly.
 *
 * Implements URDNA2015 (W3C RDF Dataset Canonicalization) directly
 * to avoid titanium-json-ld / jsonld-java Android incompatibilities.
 * Safe because the JSON-LD context is fixed and embedded.
 */
@Singleton
class IntentHashComputer @Inject constructor() {

    companion object {
        private const val NS = "https://schema.identipay.net/v1#"
        private const val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        private const val XSD_INTEGER = "http://www.w3.org/2001/XMLSchema#integer"
        private const val XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean"
    }

    /**
     * An RDF quad (triple in default graph).
     * [subject] is a blank node identifier (e.g. "_:b0").
     * [predicate] is a full IRI.
     * [objectNQuad] is the N-Quads serialized object term (literal or IRI in angle brackets).
     * [objectBlankId] is non-null when the object is a blank node reference.
     */
    private data class Quad(
        val subject: String,
        val predicate: String,
        val objectNQuad: String,
        val objectBlankId: String? = null,
    )

    /**
     * Compute the intent hash of a proposal (excluding the intentHash field itself).
     * Returns lowercase hex string matching the backend's computeIntentHash().
     */
    fun compute(proposal: CommerceProposal): String {
        val quads = proposalToQuads(proposal)
        val canonicalized = urdna2015(quads)

        val digest = SHA3.Digest256()
        val hashBytes = digest.digest(canonicalized.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify that a proposal's intentHash matches the computed value.
     */
    fun verify(proposal: CommerceProposal): Boolean {
        return compute(proposal) == proposal.intentHash
    }

    // ── JSON-LD → RDF (manual expansion with known fixed context) ───────

    private fun proposalToQuads(p: CommerceProposal): List<Quad> {
        var counter = 0
        fun bnode() = "_:b${counter++}"

        val quads = mutableListOf<Quad>()
        val root = bnode()

        // @type → rdf:type
        quads += Quad(root, RDF_TYPE, "<${NS}CommerceProposal>")

        // simple string properties on root
        quads += literal(root, "${NS}transactionId", p.transactionId)
        quads += literal(root, "${NS}expiresAt", p.expiresAt)
        quads += literal(root, "${NS}settlementChain", p.settlementChain)
        quads += literal(root, "${NS}settlementModule", p.settlementModule)

        // merchant (nested object)
        val merchant = bnode()
        quads += Quad(root, "${NS}merchant", merchant, merchant)
        quads += literal(merchant, "${NS}did", p.merchant.did)
        quads += literal(merchant, "${NS}name", p.merchant.name)
        quads += literal(merchant, "${NS}suiAddress", p.merchant.suiAddress)
        quads += literal(merchant, "${NS}publicKey", p.merchant.publicKey)

        // items (array → multiple triples with same predicate)
        for (item in p.items) {
            val itemId = bnode()
            quads += Quad(root, "${NS}items", itemId, itemId)
            quads += literal(itemId, "${NS}name", item.name)
            quads += integer(itemId, "${NS}quantity", item.quantity)
            quads += literal(itemId, "${NS}unitPrice", item.unitPrice)
            item.currency?.let { quads += literal(itemId, "${NS}currency", it) }
        }

        // amount
        val amount = bnode()
        quads += Quad(root, "${NS}amount", amount, amount)
        quads += literal(amount, "${NS}value", p.amount.value)
        quads += literal(amount, "${NS}currency", p.amount.currency)

        // deliverables
        val del = bnode()
        quads += Quad(root, "${NS}deliverables", del, del)
        quads += boolean(del, "${NS}receipt", p.deliverables.receipt)
        p.deliverables.warranty?.let { w ->
            val war = bnode()
            quads += Quad(del, "${NS}warranty", war, war)
            quads += integer(war, "${NS}durationDays", w.durationDays)
            quads += boolean(war, "${NS}transferable", w.transferable)
        }

        // constraints (optional)
        p.constraints?.let { c ->
            val hasFields = c.ageGate != null || !c.regionRestriction.isNullOrEmpty()
            if (hasFields) {
                val con = bnode()
                quads += Quad(root, "${NS}constraints", con, con)
                c.ageGate?.let { quads += integer(con, "${NS}ageGate", it) }
                c.regionRestriction?.forEach { quads += literal(con, "${NS}regionRestriction", it) }
            }
        }

        return quads
    }

    private fun literal(s: String, p: String, v: String) =
        Quad(s, p, "\"${escapeNQuads(v)}\"")

    private fun integer(s: String, p: String, v: Int) =
        Quad(s, p, "\"$v\"^^<$XSD_INTEGER>")

    private fun boolean(s: String, p: String, v: Boolean) =
        Quad(s, p, "\"$v\"^^<$XSD_BOOLEAN>")

    private fun escapeNQuads(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    // ── URDNA2015 canonicalization ──────────────────────────────────────

    /**
     * Implements W3C RDF Dataset Canonicalization (URDNA2015).
     *
     * Steps:
     * 1. Hash first-degree quads for every blank node (SHA-256).
     * 2. Sort blank nodes by hash; assign canonical names _:c14n0, _:c14n1, …
     * 3. For hash collisions use N-degree hashing to disambiguate.
     * 4. Serialize all quads with canonical names, sort lines, join.
     */
    private fun urdna2015(quads: List<Quad>): String {
        // Collect every blank-node identifier
        val blankNodes = mutableSetOf<String>()
        for (q in quads) {
            blankNodes += q.subject                        // subjects are always blank nodes here
            q.objectBlankId?.let { blankNodes += it }
        }

        // Hash first-degree quads for each blank node
        val hashGroups = mutableMapOf<String, MutableList<String>>()
        for (bn in blankNodes) {
            val h = hashFirstDegree(bn, quads)
            hashGroups.getOrPut(h) { mutableListOf() } += bn
        }

        // Assign canonical identifiers in hash order
        val canonical = mutableMapOf<String, String>()
        var n = 0
        for (hash in hashGroups.keys.sorted()) {
            val group = hashGroups[hash]!!
            if (group.size == 1) {
                canonical[group[0]] = "_:c14n${n++}"
            } else {
                // Collision: disambiguate with N-degree hash, then sort
                val sorted = group.sortedBy { hashNDegree(it, quads) }
                for (bn in sorted) {
                    canonical[bn] = "_:c14n${n++}"
                }
            }
        }

        // Serialize with canonical names, sort, join
        return quads.map { q ->
            val s = canonical[q.subject]!!
            val p = "<${q.predicate}>"
            val o = if (q.objectBlankId != null) canonical[q.objectBlankId]!! else q.objectNQuad
            "$s $p $o .\n"
        }.sorted().joinToString("")
    }

    /**
     * URDNA2015 §4.6.3 — Hash First Degree Quads.
     *
     * For each quad mentioning [blankNodeId], produce an N-Quad string
     * replacing [blankNodeId] with `_:a` and any other blank node with `_:z`.
     * Sort these strings, concatenate, SHA-256 hash.
     */
    private fun hashFirstDegree(blankNodeId: String, quads: List<Quad>): String {
        val nquadStrings = mutableListOf<String>()
        for (q in quads) {
            val mentions = q.subject == blankNodeId || q.objectBlankId == blankNodeId
            if (!mentions) continue

            val s = when (q.subject) {
                blankNodeId -> "_:a"
                else -> "_:z"      // all subjects are blank nodes in our dataset
            }
            val p = "<${q.predicate}>"
            val o = when {
                q.objectBlankId == blankNodeId -> "_:a"
                q.objectBlankId != null -> "_:z"
                else -> q.objectNQuad
            }
            nquadStrings += "$s $p $o .\n"
        }
        nquadStrings.sort()
        return sha256Hex(nquadStrings.joinToString(""))
    }

    /**
     * Simplified N-degree hashing for collision resolution.
     *
     * Extends first-degree hashing by incorporating the first-degree hashes
     * of neighbouring blank nodes, giving a deeper structural fingerprint.
     */
    private fun hashNDegree(blankNodeId: String, quads: List<Quad>): String {
        val parts = mutableListOf<String>()
        for (q in quads) {
            val mentions = q.subject == blankNodeId || q.objectBlankId == blankNodeId
            if (!mentions) continue

            val s = when (q.subject) {
                blankNodeId -> "_:a"
                else -> hashFirstDegree(q.subject, quads)
            }
            val p = "<${q.predicate}>"
            val o = when {
                q.objectBlankId == blankNodeId -> "_:a"
                q.objectBlankId != null -> hashFirstDegree(q.objectBlankId, quads)
                else -> q.objectNQuad
            }
            parts += "$s $p $o .\n"
        }
        parts.sort()
        return sha256Hex(parts.joinToString(""))
    }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
