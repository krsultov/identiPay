import { assertEquals, assertNotEquals } from "@std/assert";
import {
  buildProposal,
  computeIntentHash,
  createProposalWithHash,
} from "../../../src/services/proposal.service.ts";
import { SAMPLE_PROPOSAL } from "../../fixtures/test-vectors.ts";
import { VALID_PROPOSAL_INPUT, MINIMAL_PROPOSAL_INPUT } from "../../fixtures/proposals.ts";

const TEST_MERCHANT = {
  did: "did:identipay:shop.example.com:test-id",
  name: "Test Shop",
  suiAddress: "0x" + "ab".repeat(32),
  publicKey: "cd".repeat(32),
};

Deno.test("buildProposal creates valid JSON-LD structure", () => {
  const proposal = buildProposal(
    "550e8400-e29b-41d4-a716-446655440000",
    TEST_MERCHANT,
    VALID_PROPOSAL_INPUT,
    "0x1::settlement",
  );

  assertEquals(proposal["@context"], "https://schema.identipay.io/v1");
  assertEquals(proposal["@type"], "CommerceProposal");
  assertEquals(proposal.merchant.did, TEST_MERCHANT.did);
  assertEquals(proposal.items.length, 2);
  assertEquals(proposal.settlementChain, "sui");
});

Deno.test("buildProposal contains NO buyer fields", () => {
  const proposal = buildProposal(
    "550e8400-e29b-41d4-a716-446655440000",
    TEST_MERCHANT,
    VALID_PROPOSAL_INPUT,
    "0x1::settlement",
  );

  // Privacy invariant: no buyer identity in proposal
  const json = JSON.stringify(proposal);
  assertEquals(json.includes("buyer"), false);
  assertEquals(json.includes("stealth"), false);
  assertEquals(json.includes("spendPubkey"), false);
  assertEquals(json.includes("viewPubkey"), false);
});

Deno.test("computeIntentHash is deterministic", async () => {
  const hash1 = await computeIntentHash(SAMPLE_PROPOSAL);
  const hash2 = await computeIntentHash(SAMPLE_PROPOSAL);

  assertEquals(hash1, hash2);
  assertEquals(hash1.length, 64);
  assertEquals(/^[0-9a-f]{64}$/.test(hash1), true);
});

Deno.test("computeIntentHash changes with different input", async () => {
  const hash1 = await computeIntentHash(SAMPLE_PROPOSAL);

  const modified = { ...SAMPLE_PROPOSAL, amount: { value: "999", currency: "SUI" } };
  const hash2 = await computeIntentHash(modified);

  assertNotEquals(hash1, hash2);
});

Deno.test("createProposalWithHash includes intentHash field", async () => {
  const proposal = await createProposalWithHash(
    "550e8400-e29b-41d4-a716-446655440000",
    TEST_MERCHANT,
    MINIMAL_PROPOSAL_INPUT,
    "0x1::settlement",
  );

  assertEquals(typeof proposal.intentHash, "string");
  assertEquals(proposal.intentHash.length, 64);
  assertEquals(/^[0-9a-f]{64}$/.test(proposal.intentHash), true);
});

Deno.test("intent hash does not include intentHash field in computation", async () => {
  const proposal = await createProposalWithHash(
    "550e8400-e29b-41d4-a716-446655440000",
    TEST_MERCHANT,
    MINIMAL_PROPOSAL_INPUT,
    "0x1::settlement",
  );

  // Recompute from proposal without intentHash
  const { intentHash: _removed, ...withoutHash } = proposal;
  const recomputed = await computeIntentHash(withoutHash);

  assertEquals(proposal.intentHash, recomputed);
});
