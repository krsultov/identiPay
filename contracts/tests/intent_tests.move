#[test_only]
module identipay::intent_tests;

use identipay::intent;

// ============ Test Helpers ============

fun valid_signature(): vector<u8> {
    // 64-byte dummy signature (will fail Ed25519 verify but passes length check)
    vector[
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
        33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48,
        49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64,
    ]
}

fun valid_pubkey(): vector<u8> {
    vector[
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
    ]
}

fun valid_intent_hash(): vector<u8> {
    vector[0xDE, 0xAD, 0xBE, 0xEF]
}

// ============ Input Validation Tests ============
// Note: We can't test actual Ed25519 verification in Move unit tests
// because we can't generate valid signatures. We test input validation instead.
// Actual crypto verification is tested in integration tests.

#[test]
#[expected_failure(abort_code = identipay::intent::EInvalidSignatureLength)]
fun test_signature_too_short() {
    let short_sig = vector[1, 2, 3]; // 3 bytes instead of 64
    let hash = valid_intent_hash();
    let pubkey = valid_pubkey();
    intent::test_verify_intent_signature(&short_sig, &hash, &pubkey);
}

#[test]
#[expected_failure(abort_code = identipay::intent::EInvalidSignatureLength)]
fun test_signature_too_long() {
    let mut long_sig = valid_signature();
    long_sig.push_back(0xFF); // 65 bytes
    let hash = valid_intent_hash();
    let pubkey = valid_pubkey();
    intent::test_verify_intent_signature(&long_sig, &hash, &pubkey);
}

#[test]
#[expected_failure(abort_code = identipay::intent::EInvalidSignatureLength)]
fun test_signature_empty() {
    let empty_sig = vector[];
    let hash = valid_intent_hash();
    let pubkey = valid_pubkey();
    intent::test_verify_intent_signature(&empty_sig, &hash, &pubkey);
}

#[test]
#[expected_failure(abort_code = identipay::intent::EInvalidPublicKeyLength)]
fun test_pubkey_too_short() {
    let sig = valid_signature();
    let hash = valid_intent_hash();
    let short_pubkey = vector[1, 2, 3, 4]; // 4 bytes instead of 32
    intent::test_verify_intent_signature(&sig, &hash, &short_pubkey);
}

#[test]
#[expected_failure(abort_code = identipay::intent::EInvalidPublicKeyLength)]
fun test_pubkey_too_long() {
    let sig = valid_signature();
    let hash = valid_intent_hash();
    let mut long_pubkey = valid_pubkey();
    long_pubkey.push_back(0xFF); // 33 bytes
    intent::test_verify_intent_signature(&sig, &hash, &long_pubkey);
}

#[test]
#[expected_failure(abort_code = identipay::intent::EInvalidPublicKeyLength)]
fun test_pubkey_empty() {
    let sig = valid_signature();
    let hash = valid_intent_hash();
    let empty_pubkey = vector[];
    intent::test_verify_intent_signature(&sig, &hash, &empty_pubkey);
}

#[test]
#[expected_failure(abort_code = identipay::intent::EEmptyIntentHash)]
fun test_intent_hash_empty() {
    let sig = valid_signature();
    let empty_hash = vector[];
    let pubkey = valid_pubkey();
    intent::test_verify_intent_signature(&sig, &empty_hash, &pubkey);
}

// Note: We cannot test a valid Ed25519 signature in Move unit tests
// because sui::ed25519::ed25519_verify is a native function that
// requires real key material. The following test confirms that random
// data correctly fails verification (abort_code = EInvalidSignature).

#[test]
#[expected_failure(abort_code = identipay::intent::EInvalidSignature)]
fun test_invalid_signature_fails_verification() {
    let sig = valid_signature(); // dummy 64 bytes, not a real signature
    let hash = valid_intent_hash();
    let pubkey = valid_pubkey(); // dummy 32 bytes, not a real pubkey
    intent::test_verify_intent_signature(&sig, &hash, &pubkey);
}

// ============ Boundary Tests ============

#[test]
#[expected_failure(abort_code = identipay::intent::EInvalidSignatureLength)]
fun test_signature_63_bytes() {
    let mut sig = valid_signature();
    sig.pop_back(); // 63 bytes
    let hash = valid_intent_hash();
    let pubkey = valid_pubkey();
    intent::test_verify_intent_signature(&sig, &hash, &pubkey);
}

#[test]
#[expected_failure(abort_code = identipay::intent::EInvalidPublicKeyLength)]
fun test_pubkey_31_bytes() {
    let sig = valid_signature();
    let hash = valid_intent_hash();
    let mut pubkey = valid_pubkey();
    pubkey.pop_back(); // 31 bytes
    intent::test_verify_intent_signature(&sig, &hash, &pubkey);
}
