#[test_only]
module identipay::receipt_tests;

use sui::test_scenario;
use identipay::receipt;

// ============ Test Helpers ============

fun valid_nonce(): vector<u8> {
    vector[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]
}

fun valid_pubkey(): vector<u8> {
    vector[
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
    ]
}

fun valid_payload(): vector<u8> {
    vector[0xCA, 0xFE, 0xBA, 0xBE]
}

fun valid_intent_hash(): vector<u8> {
    vector[0xDE, 0xAD, 0xBE, 0xEF]
}

// ============ Happy Path Tests ============

#[test]
fun test_mint_receipt_success() {
    let merchant = @0xA1;
    let mut scenario = test_scenario::begin(@0xB1);

    let receipt = receipt::test_mint_receipt(
        merchant,
        valid_intent_hash(),
        valid_payload(),
        valid_nonce(),
        valid_pubkey(),
        scenario.ctx(),
    );

    assert!(receipt.merchant() == merchant);
    assert!(receipt.intent_hash() == valid_intent_hash());
    assert!(receipt.timestamp() == 0); // epoch_timestamp_ms is 0 in test

    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
fun test_receipt_accessors() {
    let merchant = @0xABC;
    let mut scenario = test_scenario::begin(@0xB1);

    let receipt = receipt::test_mint_receipt(
        merchant,
        valid_intent_hash(),
        valid_payload(),
        valid_nonce(),
        valid_pubkey(),
        scenario.ctx(),
    );

    // Verify all accessors work
    assert!(receipt.merchant() == @0xABC);
    assert!(receipt.intent_hash() == valid_intent_hash());
    let _id = receipt.id();
    let _ts = receipt.timestamp();

    receipt::test_destroy(receipt);
    scenario.end();
}

// ============ Error Path Tests ============

#[test]
#[expected_failure(abort_code = identipay::receipt::EInvalidNonceLength)]
fun test_mint_receipt_invalid_nonce_too_short() {
    let mut scenario = test_scenario::begin(@0xB1);
    let receipt = receipt::test_mint_receipt(
        @0xA1,
        valid_intent_hash(),
        valid_payload(),
        vector[1, 2, 3], // too short (3 instead of 12)
        valid_pubkey(),
        scenario.ctx(),
    );
    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::receipt::EInvalidNonceLength)]
fun test_mint_receipt_invalid_nonce_too_long() {
    let mut scenario = test_scenario::begin(@0xB1);
    let receipt = receipt::test_mint_receipt(
        @0xA1,
        valid_intent_hash(),
        valid_payload(),
        vector[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13], // 13 bytes
        valid_pubkey(),
        scenario.ctx(),
    );
    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::receipt::EInvalidNonceLength)]
fun test_mint_receipt_empty_nonce() {
    let mut scenario = test_scenario::begin(@0xB1);
    let receipt = receipt::test_mint_receipt(
        @0xA1,
        valid_intent_hash(),
        valid_payload(),
        vector[], // empty
        valid_pubkey(),
        scenario.ctx(),
    );
    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::receipt::EInvalidEphemeralPubkey)]
fun test_mint_receipt_invalid_pubkey_too_short() {
    let mut scenario = test_scenario::begin(@0xB1);
    let receipt = receipt::test_mint_receipt(
        @0xA1,
        valid_intent_hash(),
        valid_payload(),
        valid_nonce(),
        vector[1, 2, 3, 4, 5], // 5 instead of 32
        scenario.ctx(),
    );
    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::receipt::EInvalidEphemeralPubkey)]
fun test_mint_receipt_invalid_pubkey_too_long() {
    let mut scenario = test_scenario::begin(@0xB1);
    let mut long_key = valid_pubkey();
    long_key.push_back(99); // 33 bytes
    let receipt = receipt::test_mint_receipt(
        @0xA1,
        valid_intent_hash(),
        valid_payload(),
        valid_nonce(),
        long_key,
        scenario.ctx(),
    );
    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::receipt::EEmptyPayload)]
fun test_mint_receipt_empty_payload() {
    let mut scenario = test_scenario::begin(@0xB1);
    let receipt = receipt::test_mint_receipt(
        @0xA1,
        valid_intent_hash(),
        vector[], // empty payload
        valid_nonce(),
        valid_pubkey(),
        scenario.ctx(),
    );
    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::receipt::EEmptyIntentHash)]
fun test_mint_receipt_empty_intent_hash() {
    let mut scenario = test_scenario::begin(@0xB1);
    let receipt = receipt::test_mint_receipt(
        @0xA1,
        vector[], // empty intent hash
        valid_payload(),
        valid_nonce(),
        valid_pubkey(),
        scenario.ctx(),
    );
    receipt::test_destroy(receipt);
    scenario.end();
}

// ============ Boundary Tests ============

#[test]
fun test_mint_receipt_large_payload() {
    let mut scenario = test_scenario::begin(@0xB1);
    // Test with a larger payload (simulating real encrypted data)
    let mut large_payload = vector[];
    let mut i = 0u64;
    while (i < 1000u64) {
        large_payload.push_back((i % 256) as u8);
        i = i + 1;
    };

    let receipt = receipt::test_mint_receipt(
        @0xA1,
        valid_intent_hash(),
        large_payload,
        valid_nonce(),
        valid_pubkey(),
        scenario.ctx(),
    );

    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
fun test_mint_receipt_single_byte_payload() {
    let mut scenario = test_scenario::begin(@0xB1);
    let receipt = receipt::test_mint_receipt(
        @0xA1,
        valid_intent_hash(),
        vector[0xFF], // minimal valid payload
        valid_nonce(),
        valid_pubkey(),
        scenario.ctx(),
    );

    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
fun test_mint_multiple_receipts_unique_ids() {
    let mut scenario = test_scenario::begin(@0xB1);

    let r1 = receipt::test_mint_receipt(
        @0xA1,
        valid_intent_hash(),
        valid_payload(),
        valid_nonce(),
        valid_pubkey(),
        scenario.ctx(),
    );
    let r2 = receipt::test_mint_receipt(
        @0xA1,
        valid_intent_hash(),
        valid_payload(),
        valid_nonce(),
        valid_pubkey(),
        scenario.ctx(),
    );

    // Each receipt must have a unique ID
    assert!(r1.id() != r2.id());

    receipt::test_destroy(r1);
    receipt::test_destroy(r2);
    scenario.end();
}
