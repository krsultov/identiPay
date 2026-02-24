import { sha3_256 } from "@noble/hashes/sha3";
import { bytesToHex } from "@noble/hashes/utils";
// @deno-types="npm:@types/jsonld"
import jsonld from "jsonld";
import type { CommerceProposal, CreateProposalInput } from "../types/proposal.ts";

// Embedded JSON-LD context for identipay commerce proposals.
// This avoids network dependency on https://schema.identipay.io/v1.
const IDENTIPAY_CONTEXT = {
  "@context": {
    "@vocab": "https://schema.identipay.io/v1#",
    transactionId: "https://schema.identipay.io/v1#transactionId",
    merchant: "https://schema.identipay.io/v1#merchant",
    did: "https://schema.identipay.io/v1#did",
    name: "https://schema.identipay.io/v1#name",
    suiAddress: "https://schema.identipay.io/v1#suiAddress",
    publicKey: "https://schema.identipay.io/v1#publicKey",
    items: "https://schema.identipay.io/v1#items",
    quantity: "https://schema.identipay.io/v1#quantity",
    unitPrice: "https://schema.identipay.io/v1#unitPrice",
    amount: "https://schema.identipay.io/v1#amount",
    value: "https://schema.identipay.io/v1#value",
    currency: "https://schema.identipay.io/v1#currency",
    deliverables: "https://schema.identipay.io/v1#deliverables",
    receipt: "https://schema.identipay.io/v1#receipt",
    warranty: "https://schema.identipay.io/v1#warranty",
    durationDays: "https://schema.identipay.io/v1#durationDays",
    transferable: "https://schema.identipay.io/v1#transferable",
    constraints: "https://schema.identipay.io/v1#constraints",
    ageGate: "https://schema.identipay.io/v1#ageGate",
    regionRestriction: "https://schema.identipay.io/v1#regionRestriction",
    expiresAt: "https://schema.identipay.io/v1#expiresAt",
    intentHash: "https://schema.identipay.io/v1#intentHash",
    settlementChain: "https://schema.identipay.io/v1#settlementChain",
    settlementModule: "https://schema.identipay.io/v1#settlementModule",
  },
};

// Custom document loader that serves our embedded context
const customLoader = (url: string) => {
  if (url === "https://schema.identipay.io/v1") {
    return Promise.resolve({
      contextUrl: null as string | null,
      document: IDENTIPAY_CONTEXT,
      documentUrl: url,
    });
  }
  // Fall back to default loader for any other URLs
  // deno-lint-ignore no-explicit-any
  return (jsonld as unknown as Record<string, any>).documentLoaders.node()(url);
};

/**
 * Build a CommerceProposal JSON-LD from merchant data + input.
 * No buyer fields are included -- privacy invariant.
 */
export function buildProposal(
  transactionId: string,
  merchant: {
    did: string;
    name: string;
    suiAddress: string;
    publicKey: string;
  },
  input: CreateProposalInput,
  settlementModule: string,
): Omit<CommerceProposal, "intentHash"> {
  const expiresAt = new Date(
    Date.now() + input.expiresInSeconds * 1000,
  ).toISOString();

  return {
    "@context": "https://schema.identipay.io/v1",
    "@type": "CommerceProposal",
    transactionId,
    merchant: {
      did: merchant.did,
      name: merchant.name,
      suiAddress: merchant.suiAddress,
      publicKey: merchant.publicKey,
    },
    items: input.items,
    amount: input.amount,
    deliverables: input.deliverables,
    constraints: input.constraints,
    expiresAt,
    settlementChain: "sui",
    settlementModule,
  };
}

/**
 * Compute the intent hash of a proposal.
 * Process: URDNA2015 canonicalization -> SHA3-256
 * The intentHash field is NOT included in the hash input (it's the output).
 */
export async function computeIntentHash(
  proposal: Omit<CommerceProposal, "intentHash">,
): Promise<string> {
  // Canonicalize using JSON-LD URDNA2015 with our embedded context
  const canonicalized = await jsonld.canonize(proposal, {
    algorithm: "URDNA2015",
    format: "application/n-quads",
    documentLoader: customLoader,
  });

  // SHA3-256 hash
  const hash = sha3_256(new TextEncoder().encode(canonicalized as string));
  return bytesToHex(hash);
}

/**
 * Build a complete proposal with intent hash.
 */
export async function createProposalWithHash(
  transactionId: string,
  merchant: {
    did: string;
    name: string;
    suiAddress: string;
    publicKey: string;
  },
  input: CreateProposalInput,
  settlementModule: string,
): Promise<CommerceProposal> {
  const proposal = buildProposal(transactionId, merchant, input, settlementModule);
  const intentHash = await computeIntentHash(proposal);
  return { ...proposal, intentHash };
}
