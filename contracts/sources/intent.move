/// Intent signature verification for identiPay.
/// Verifies that the buyer signed the canonicalized intent hash using Ed25519.
/// Per the whitepaper (section 5.3): signatures bind to canonicalized proposal
/// hashes, providing semantic binding of intent to execution.
module identipay::intent;

use sui::ed25519;

// ============ Constants ============

const EInvalidSignature: u64 = 0;
const EInvalidSignatureLength: u64 = 1;
const EInvalidPublicKeyLength: u64 = 2;
const EEmptyIntentHash: u64 = 3;

const SIGNATURE_LENGTH: u64 = 64;
const PUBKEY_LENGTH: u64 = 32;

// ============ Public Functions ============

/// Verify that `intent_sig` is a valid Ed25519 signature over `intent_hash`
/// by the holder of `public_key`. Aborts if verification fails.
///
/// The intent_hash is the SHA3-256 of the URDNA2015-canonicalized JSON-LD
/// commerce proposal, computed off-chain by the wallet.
public(package) fun verify_intent_signature(
    intent_sig: &vector<u8>,
    intent_hash: &vector<u8>,
    public_key: &vector<u8>,
) {
    assert!(intent_sig.length() == SIGNATURE_LENGTH, EInvalidSignatureLength);
    assert!(public_key.length() == PUBKEY_LENGTH, EInvalidPublicKeyLength);
    assert!(!intent_hash.is_empty(), EEmptyIntentHash);

    let valid = ed25519::ed25519_verify(intent_sig, public_key, intent_hash);
    assert!(valid, EInvalidSignature);
}

// ============ Test Helpers ============

#[test_only]
public fun test_verify_intent_signature(
    intent_sig: &vector<u8>,
    intent_hash: &vector<u8>,
    public_key: &vector<u8>,
) {
    verify_intent_signature(intent_sig, intent_hash, public_key);
}
