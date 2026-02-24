/**
 * Cross-platform test vectors for intent hash and stealth address derivation.
 * These values must produce identical results in TypeScript, Kotlin, and Move.
 *
 * Keys are properly generated Ed25519 (spend) and X25519 (view/ephemeral) keypairs.
 */

// Valid Ed25519 keypair for spending
export const TEST_SPEND_PRIVKEY =
  "5f9f8af4a6af1d0dadf7196afbfcfc7782699eb9e6f4dc02519d3f1447fde0c5";
export const TEST_SPEND_PUBKEY =
  "4db86d57961633751cee277d07e680bbb7ca568ad443bae002d79cc94ce03c09";

// Valid X25519 keypair for viewing
export const TEST_VIEW_PRIVKEY =
  "b9fa4248590ac7241e2134737518bafb6767765acb41c1d9882a128fcf77c498";
export const TEST_VIEW_PUBKEY =
  "a97851cabd459cb6ef4f507562b077527c5e8864c3aa078af0470c98724bca3a";

// Valid X25519 ephemeral keypair for deterministic tests
export const TEST_EPHEMERAL_PRIVKEY =
  "31968ee9f2e34e7ede92a134608a35aa60447cdad9b97ce2f874bc69fc06f173";
export const TEST_EPHEMERAL_PUBKEY =
  "c94b5a660ea953fbe2aff147c3165f96161323a5605da92ccf4704ac0899e75a";

// Sample CommerceProposal for intent hash testing
export const SAMPLE_PROPOSAL = {
  "@context": "https://schema.identipay.io/v1" as const,
  "@type": "CommerceProposal" as const,
  transactionId: "550e8400-e29b-41d4-a716-446655440000",
  merchant: {
    did: "did:identipay:shop.example.com:550e8400-e29b-41d4-a716-446655440001",
    name: "Test Shop",
    suiAddress: "0x" + "ab".repeat(32),
    publicKey: "cd".repeat(32),
  },
  items: [
    {
      name: "Widget",
      quantity: 1,
      unitPrice: "1000000000",
    },
  ],
  amount: {
    value: "1000000000",
    currency: "SUI",
  },
  deliverables: {
    receipt: true,
  },
  expiresAt: "2030-01-01T00:00:00.000Z",
  settlementChain: "sui" as const,
  settlementModule: "0x1::settlement",
};

// Merchant test data
export const TEST_MERCHANT = {
  name: "Test Merchant",
  suiAddress: "0x" + "ab".repeat(32),
  hostname: "merchant.example.com",
  publicKey: "cd".repeat(32),
};
