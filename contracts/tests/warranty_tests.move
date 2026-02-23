#[test_only]
module identipay::warranty_tests;

use sui::test_scenario;
use identipay::warranty;
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

fun valid_terms(): vector<u8> {
    vector[0xCA, 0xFE, 0xBA, 0xBE]
}

fun valid_intent_hash(): vector<u8> {
    vector[0xDE, 0xAD, 0xBE, 0xEF]
}

fun valid_payload(): vector<u8> {
    vector[0x01, 0x02, 0x03, 0x04]
}

const FUTURE_EXPIRY: u64 = 1_000_000_000_000; // far future

// ============ Happy Path Tests ============

#[test]
fun test_mint_warranty_transferable() {
    let merchant = @0xA1;
    let mut scenario = test_scenario::begin(@0xB1);

    // Create receipt first to get receipt_id
    let receipt = receipt::test_mint_receipt(
        merchant,
        valid_intent_hash(),
        valid_payload(),
        valid_nonce(),
        valid_pubkey(),
        scenario.ctx(),
    );
    let receipt_id = receipt::id(&receipt);

    let warranty = warranty::test_mint_warranty(
        receipt_id,
        merchant,
        valid_intent_hash(),
        valid_terms(),
        valid_nonce(),
        valid_pubkey(),
        FUTURE_EXPIRY,
        true, // transferable
        scenario.ctx(),
    );

    assert!(warranty.merchant() == merchant);
    assert!(warranty.receipt_id() == receipt_id);
    assert!(warranty.expiry() == FUTURE_EXPIRY);
    assert!(warranty.is_transferable());
    assert!(!warranty.is_expired(scenario.ctx()));

    warranty::test_destroy(warranty);
    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
fun test_mint_warranty_soulbound() {
    let mut scenario = test_scenario::begin(@0xB1);

    let receipt = receipt::test_mint_receipt(
        @0xA1,
        valid_intent_hash(),
        valid_payload(),
        valid_nonce(),
        valid_pubkey(),
        scenario.ctx(),
    );
    let receipt_id = receipt::id(&receipt);

    let warranty = warranty::test_mint_warranty(
        receipt_id,
        @0xA1,
        valid_intent_hash(),
        valid_terms(),
        valid_nonce(),
        valid_pubkey(),
        FUTURE_EXPIRY,
        false, // soulbound
        scenario.ctx(),
    );

    assert!(!warranty.is_transferable());

    warranty::test_destroy(warranty);
    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
fun test_transfer_transferable_warranty() {
    let buyer = @0xB1;
    let recipient = @0xC1;
    let mut scenario = test_scenario::begin(buyer);

    let receipt = receipt::test_mint_receipt(
        @0xA1,
        valid_intent_hash(),
        valid_payload(),
        valid_nonce(),
        valid_pubkey(),
        scenario.ctx(),
    );
    let receipt_id = receipt::id(&receipt);

    let warranty = warranty::test_mint_warranty(
        receipt_id,
        @0xA1,
        valid_intent_hash(),
        valid_terms(),
        valid_nonce(),
        valid_pubkey(),
        FUTURE_EXPIRY,
        true,
        scenario.ctx(),
    );

    // Transfer should succeed for transferable warranty
    warranty.transfer_warranty(recipient, scenario.ctx());

    receipt::test_destroy(receipt);
    scenario.end();
}

// ============ Error Path Tests ============

#[test]
#[expected_failure(abort_code = identipay::warranty::EWarrantyNotTransferable)]
fun test_transfer_soulbound_warranty_fails() {
    let mut scenario = test_scenario::begin(@0xB1);

    let receipt = receipt::test_mint_receipt(
        @0xA1,
        valid_intent_hash(),
        valid_payload(),
        valid_nonce(),
        valid_pubkey(),
        scenario.ctx(),
    );
    let receipt_id = receipt::id(&receipt);

    let warranty = warranty::test_mint_warranty(
        receipt_id,
        @0xA1,
        valid_intent_hash(),
        valid_terms(),
        valid_nonce(),
        valid_pubkey(),
        FUTURE_EXPIRY,
        false, // soulbound
        scenario.ctx(),
    );

    // This should fail -- warranty is not transferable
    warranty.transfer_warranty(@0xC1, scenario.ctx());

    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::warranty::EInvalidNonceLength)]
fun test_mint_warranty_invalid_nonce() {
    let mut scenario = test_scenario::begin(@0xB1);
    let receipt = receipt::test_mint_receipt(
        @0xA1, valid_intent_hash(), valid_payload(),
        valid_nonce(), valid_pubkey(), scenario.ctx(),
    );
    let receipt_id = receipt::id(&receipt);

    let warranty = warranty::test_mint_warranty(
        receipt_id, @0xA1, valid_intent_hash(), valid_terms(),
        vector[1, 2, 3], // too short
        valid_pubkey(), FUTURE_EXPIRY, true, scenario.ctx(),
    );

    warranty::test_destroy(warranty);
    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::warranty::EInvalidEphemeralPubkey)]
fun test_mint_warranty_invalid_pubkey() {
    let mut scenario = test_scenario::begin(@0xB1);
    let receipt = receipt::test_mint_receipt(
        @0xA1, valid_intent_hash(), valid_payload(),
        valid_nonce(), valid_pubkey(), scenario.ctx(),
    );
    let receipt_id = receipt::id(&receipt);

    let warranty = warranty::test_mint_warranty(
        receipt_id, @0xA1, valid_intent_hash(), valid_terms(),
        valid_nonce(),
        vector[1, 2, 3], // too short
        FUTURE_EXPIRY, true, scenario.ctx(),
    );

    warranty::test_destroy(warranty);
    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::warranty::EEmptyTerms)]
fun test_mint_warranty_empty_terms() {
    let mut scenario = test_scenario::begin(@0xB1);
    let receipt = receipt::test_mint_receipt(
        @0xA1, valid_intent_hash(), valid_payload(),
        valid_nonce(), valid_pubkey(), scenario.ctx(),
    );
    let receipt_id = receipt::id(&receipt);

    let warranty = warranty::test_mint_warranty(
        receipt_id, @0xA1, valid_intent_hash(),
        vector[], // empty terms
        valid_nonce(), valid_pubkey(), FUTURE_EXPIRY, true, scenario.ctx(),
    );

    warranty::test_destroy(warranty);
    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::warranty::EInvalidExpiry)]
fun test_mint_warranty_zero_expiry() {
    let mut scenario = test_scenario::begin(@0xB1);
    let receipt = receipt::test_mint_receipt(
        @0xA1, valid_intent_hash(), valid_payload(),
        valid_nonce(), valid_pubkey(), scenario.ctx(),
    );
    let receipt_id = receipt::id(&receipt);

    let warranty = warranty::test_mint_warranty(
        receipt_id, @0xA1, valid_intent_hash(), valid_terms(),
        valid_nonce(), valid_pubkey(),
        0, // zero expiry
        true, scenario.ctx(),
    );

    warranty::test_destroy(warranty);
    receipt::test_destroy(receipt);
    scenario.end();
}

// ============ Boundary Tests ============

#[test]
fun test_warranty_expiry_min_valid() {
    let mut scenario = test_scenario::begin(@0xB1);
    let receipt = receipt::test_mint_receipt(
        @0xA1, valid_intent_hash(), valid_payload(),
        valid_nonce(), valid_pubkey(), scenario.ctx(),
    );
    let receipt_id = receipt::id(&receipt);

    // Minimum valid expiry is 1
    let warranty = warranty::test_mint_warranty(
        receipt_id, @0xA1, valid_intent_hash(), valid_terms(),
        valid_nonce(), valid_pubkey(), 1, true, scenario.ctx(),
    );

    assert!(warranty.expiry() == 1);

    warranty::test_destroy(warranty);
    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
fun test_warranty_is_expired_check() {
    let mut scenario = test_scenario::begin(@0xB1);
    let receipt = receipt::test_mint_receipt(
        @0xA1, valid_intent_hash(), valid_payload(),
        valid_nonce(), valid_pubkey(), scenario.ctx(),
    );
    let receipt_id = receipt::id(&receipt);

    // epoch_timestamp_ms is 0 in test, so expiry=1 should not be expired
    let w1 = warranty::test_mint_warranty(
        receipt_id, @0xA1, valid_intent_hash(), valid_terms(),
        valid_nonce(), valid_pubkey(), 1, true, scenario.ctx(),
    );
    assert!(!w1.is_expired(scenario.ctx())); // 1 > 0

    warranty::test_destroy(w1);
    receipt::test_destroy(receipt);
    scenario.end();
}

#[test]
fun test_multiple_warranties_unique_ids() {
    let mut scenario = test_scenario::begin(@0xB1);
    let receipt = receipt::test_mint_receipt(
        @0xA1, valid_intent_hash(), valid_payload(),
        valid_nonce(), valid_pubkey(), scenario.ctx(),
    );
    let receipt_id = receipt::id(&receipt);

    let w1 = warranty::test_mint_warranty(
        receipt_id, @0xA1, valid_intent_hash(), valid_terms(),
        valid_nonce(), valid_pubkey(), FUTURE_EXPIRY, true, scenario.ctx(),
    );
    let w2 = warranty::test_mint_warranty(
        receipt_id, @0xA1, valid_intent_hash(), valid_terms(),
        valid_nonce(), valid_pubkey(), FUTURE_EXPIRY, false, scenario.ctx(),
    );

    assert!(object::id(&w1) != object::id(&w2));

    warranty::test_destroy(w1);
    warranty::test_destroy(w2);
    receipt::test_destroy(receipt);
    scenario.end();
}
