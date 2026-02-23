#[test_only]
module identipay::announcements_tests;

use sui::test_scenario;
use identipay::announcements;

// ============ Test Helpers ============

fun valid_pubkey(): vector<u8> {
    vector[
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
    ]
}

// ============ Happy Path Tests ============

#[test]
fun test_announce_success() {
    let mut scenario = test_scenario::begin(@0xE1);

    announcements::announce(
        valid_pubkey(),
        42, // view_tag
        @0xF1,
        vector[], // empty metadata is fine
        scenario.ctx(),
    );

    scenario.end();
}

#[test]
fun test_announce_with_metadata() {
    let mut scenario = test_scenario::begin(@0xE1);

    announcements::announce(
        valid_pubkey(),
        0xFF, // max view_tag
        @0xF1,
        vector[0x01, 0x02, 0x03, 0x04], // encrypted memo
        scenario.ctx(),
    );

    scenario.end();
}

#[test]
fun test_announce_view_tag_zero() {
    let mut scenario = test_scenario::begin(@0xE1);

    announcements::announce(
        valid_pubkey(),
        0, // min view_tag
        @0xF1,
        vector[],
        scenario.ctx(),
    );

    scenario.end();
}

#[test]
fun test_announce_large_metadata() {
    let mut scenario = test_scenario::begin(@0xE1);

    let mut metadata = vector[];
    let mut i = 0u64;
    while (i < 500) {
        metadata.push_back((i % 256) as u8);
        i = i + 1;
    };

    announcements::announce(
        valid_pubkey(),
        128,
        @0xF1,
        metadata,
        scenario.ctx(),
    );

    scenario.end();
}

#[test]
fun test_multiple_announcements() {
    let mut scenario = test_scenario::begin(@0xE1);

    // Multiple announcements in same tx (simulating batch)
    announcements::announce(
        valid_pubkey(), 1, @0xF2, vector[], scenario.ctx(),
    );
    announcements::announce(
        valid_pubkey(), 2, @0xF3, vector[], scenario.ctx(),
    );
    announcements::announce(
        valid_pubkey(), 3, @0xF4, vector[], scenario.ctx(),
    );

    scenario.end();
}

// ============ Error Path Tests ============

#[test]
#[expected_failure(abort_code = identipay::announcements::EInvalidEphemeralPubkey)]
fun test_announce_invalid_pubkey_too_short() {
    let mut scenario = test_scenario::begin(@0xE1);

    announcements::announce(
        vector[1, 2, 3], // too short
        42,
        @0xF1,
        vector[],
        scenario.ctx(),
    );

    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::announcements::EInvalidEphemeralPubkey)]
fun test_announce_invalid_pubkey_too_long() {
    let mut scenario = test_scenario::begin(@0xE1);

    let mut long_key = valid_pubkey();
    long_key.push_back(99); // 33 bytes

    announcements::announce(
        long_key,
        42,
        @0xF1,
        vector[],
        scenario.ctx(),
    );

    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::announcements::EInvalidEphemeralPubkey)]
fun test_announce_empty_pubkey() {
    let mut scenario = test_scenario::begin(@0xE1);

    announcements::announce(
        vector[], // empty
        42,
        @0xF1,
        vector[],
        scenario.ctx(),
    );

    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::announcements::EInvalidEphemeralPubkey)]
fun test_announce_pubkey_31_bytes() {
    let mut scenario = test_scenario::begin(@0xE1);

    let mut key = valid_pubkey();
    key.pop_back(); // 31 bytes

    announcements::announce(
        key,
        42,
        @0xF1,
        vector[],
        scenario.ctx(),
    );

    scenario.end();
}
