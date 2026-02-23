#[test_only]
module identipay::trust_registry_tests;

use std::string;
use sui::test_scenario;
use identipay::trust_registry::{Self, TrustRegistry};

// ============ Test Helpers ============

fun valid_pubkey(): vector<u8> {
    vector[
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32,
    ]
}

fun alt_pubkey(): vector<u8> {
    vector[
        32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17,
        16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1,
    ]
}

const ADMIN: address = @0xAD;
const NON_ADMIN: address = @0xBD;
const MERCHANT_ADDR: address = @0xA1;

// ============ Happy Path Tests ============

#[test]
fun test_register_merchant_success() {
    let mut scenario = test_scenario::begin(ADMIN);

    // Init creates the shared registry
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    let mut registry = scenario.take_shared<TrustRegistry>();

    trust_registry::register_merchant(
        &mut registry,
        string::utf8(b"did:identipay:shop.example.com:m1"),
        string::utf8(b"CoffeeShop"),
        MERCHANT_ADDR,
        valid_pubkey(),
        string::utf8(b"shop.example.com"),
        scenario.ctx(),
    );

    // Verify lookup works
    let entry = trust_registry::lookup_merchant(
        &registry,
        string::utf8(b"did:identipay:shop.example.com:m1"),
    );
    assert!(trust_registry::merchant_name(entry) == string::utf8(b"CoffeeShop"));
    assert!(trust_registry::merchant_sui_address(entry) == MERCHANT_ADDR);
    assert!(trust_registry::merchant_public_key(entry) == valid_pubkey());
    assert!(trust_registry::merchant_hostname(entry) == string::utf8(b"shop.example.com"));
    assert!(trust_registry::merchant_active(entry));

    // Check is_merchant_active
    assert!(trust_registry::is_merchant_active(
        &registry,
        string::utf8(b"did:identipay:shop.example.com:m1"),
    ));

    test_scenario::return_shared(registry);
    scenario.end();
}

#[test]
fun test_register_multiple_merchants() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    let mut registry = scenario.take_shared<TrustRegistry>();

    trust_registry::register_merchant(
        &mut registry,
        string::utf8(b"did:m1"), string::utf8(b"Shop1"), @0x1,
        valid_pubkey(), string::utf8(b"shop1.com"), scenario.ctx(),
    );

    trust_registry::register_merchant(
        &mut registry,
        string::utf8(b"did:m2"), string::utf8(b"Shop2"), @0x2,
        valid_pubkey(), string::utf8(b"shop2.com"), scenario.ctx(),
    );

    assert!(trust_registry::is_merchant_active(&registry, string::utf8(b"did:m1")));
    assert!(trust_registry::is_merchant_active(&registry, string::utf8(b"did:m2")));

    test_scenario::return_shared(registry);
    scenario.end();
}

#[test]
fun test_deactivate_merchant() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    let mut registry = scenario.take_shared<TrustRegistry>();

    trust_registry::register_merchant(
        &mut registry,
        string::utf8(b"did:m1"), string::utf8(b"Shop1"), @0x1,
        valid_pubkey(), string::utf8(b"shop1.com"), scenario.ctx(),
    );

    assert!(trust_registry::is_merchant_active(&registry, string::utf8(b"did:m1")));

    trust_registry::deactivate_merchant(
        &mut registry,
        string::utf8(b"did:m1"),
        scenario.ctx(),
    );

    assert!(!trust_registry::is_merchant_active(&registry, string::utf8(b"did:m1")));

    // Lookup still works (soft delete)
    let entry = trust_registry::lookup_merchant(&registry, string::utf8(b"did:m1"));
    assert!(!trust_registry::merchant_active(entry));

    test_scenario::return_shared(registry);
    scenario.end();
}

#[test]
fun test_update_merchant_key() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    let mut registry = scenario.take_shared<TrustRegistry>();

    trust_registry::register_merchant(
        &mut registry,
        string::utf8(b"did:m1"), string::utf8(b"Shop1"), @0x1,
        valid_pubkey(), string::utf8(b"shop1.com"), scenario.ctx(),
    );

    // Update key
    trust_registry::update_merchant_key(
        &mut registry,
        string::utf8(b"did:m1"),
        alt_pubkey(),
        scenario.ctx(),
    );

    let entry = trust_registry::lookup_merchant(&registry, string::utf8(b"did:m1"));
    assert!(trust_registry::merchant_public_key(entry) == alt_pubkey());

    test_scenario::return_shared(registry);
    scenario.end();
}

#[test]
fun test_is_merchant_active_nonexistent() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    let registry = scenario.take_shared<TrustRegistry>();

    // Non-existent merchant returns false (not abort)
    assert!(!trust_registry::is_merchant_active(
        &registry,
        string::utf8(b"did:nonexistent"),
    ));

    test_scenario::return_shared(registry);
    scenario.end();
}

// ============ Error Path Tests ============

#[test]
#[expected_failure(abort_code = identipay::trust_registry::ENotAdmin)]
fun test_register_not_admin() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(NON_ADMIN); // non-admin tries to register
    let mut registry = scenario.take_shared<TrustRegistry>();

    trust_registry::register_merchant(
        &mut registry,
        string::utf8(b"did:m1"), string::utf8(b"Shop1"), @0x1,
        valid_pubkey(), string::utf8(b"shop1.com"), scenario.ctx(),
    );

    test_scenario::return_shared(registry);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::trust_registry::EMerchantAlreadyExists)]
fun test_register_duplicate_did() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    let mut registry = scenario.take_shared<TrustRegistry>();

    trust_registry::register_merchant(
        &mut registry,
        string::utf8(b"did:m1"), string::utf8(b"Shop1"), @0x1,
        valid_pubkey(), string::utf8(b"shop1.com"), scenario.ctx(),
    );

    // Duplicate registration
    trust_registry::register_merchant(
        &mut registry,
        string::utf8(b"did:m1"), string::utf8(b"Shop2"), @0x2,
        valid_pubkey(), string::utf8(b"shop2.com"), scenario.ctx(),
    );

    test_scenario::return_shared(registry);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::trust_registry::EMerchantNotFound)]
fun test_deactivate_nonexistent() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    let mut registry = scenario.take_shared<TrustRegistry>();

    trust_registry::deactivate_merchant(
        &mut registry,
        string::utf8(b"did:nonexistent"),
        scenario.ctx(),
    );

    test_scenario::return_shared(registry);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::trust_registry::ENotAdmin)]
fun test_deactivate_not_admin() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    {
        let mut registry = scenario.take_shared<TrustRegistry>();
        trust_registry::register_merchant(
            &mut registry,
            string::utf8(b"did:m1"), string::utf8(b"Shop1"), @0x1,
            valid_pubkey(), string::utf8(b"shop1.com"), scenario.ctx(),
        );
        test_scenario::return_shared(registry);
    };

    scenario.next_tx(NON_ADMIN);
    let mut registry = scenario.take_shared<TrustRegistry>();

    trust_registry::deactivate_merchant(
        &mut registry,
        string::utf8(b"did:m1"),
        scenario.ctx(),
    );

    test_scenario::return_shared(registry);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::trust_registry::ENotAdmin)]
fun test_update_key_not_admin() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    {
        let mut registry = scenario.take_shared<TrustRegistry>();
        trust_registry::register_merchant(
            &mut registry,
            string::utf8(b"did:m1"), string::utf8(b"Shop1"), @0x1,
            valid_pubkey(), string::utf8(b"shop1.com"), scenario.ctx(),
        );
        test_scenario::return_shared(registry);
    };

    scenario.next_tx(NON_ADMIN);
    let mut registry = scenario.take_shared<TrustRegistry>();

    trust_registry::update_merchant_key(
        &mut registry,
        string::utf8(b"did:m1"),
        alt_pubkey(),
        scenario.ctx(),
    );

    test_scenario::return_shared(registry);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::trust_registry::EMerchantNotFound)]
fun test_lookup_nonexistent() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    let registry = scenario.take_shared<TrustRegistry>();

    let _entry = trust_registry::lookup_merchant(
        &registry,
        string::utf8(b"did:nonexistent"),
    );

    test_scenario::return_shared(registry);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::trust_registry::EInvalidPublicKeyLength)]
fun test_register_invalid_pubkey_length() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    let mut registry = scenario.take_shared<TrustRegistry>();

    trust_registry::register_merchant(
        &mut registry,
        string::utf8(b"did:m1"), string::utf8(b"Shop1"), @0x1,
        vector[1, 2, 3], // invalid length
        string::utf8(b"shop1.com"), scenario.ctx(),
    );

    test_scenario::return_shared(registry);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::trust_registry::EInvalidPublicKeyLength)]
fun test_update_key_invalid_length() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    let mut registry = scenario.take_shared<TrustRegistry>();

    trust_registry::register_merchant(
        &mut registry,
        string::utf8(b"did:m1"), string::utf8(b"Shop1"), @0x1,
        valid_pubkey(), string::utf8(b"shop1.com"), scenario.ctx(),
    );

    trust_registry::update_merchant_key(
        &mut registry,
        string::utf8(b"did:m1"),
        vector[1, 2, 3], // invalid length
        scenario.ctx(),
    );

    test_scenario::return_shared(registry);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::trust_registry::EEmptyDID)]
fun test_register_empty_did() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    let mut registry = scenario.take_shared<TrustRegistry>();

    trust_registry::register_merchant(
        &mut registry,
        string::utf8(b""), // empty DID
        string::utf8(b"Shop1"), @0x1,
        valid_pubkey(), string::utf8(b"shop1.com"), scenario.ctx(),
    );

    test_scenario::return_shared(registry);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::trust_registry::EEmptyName)]
fun test_register_empty_name() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    let mut registry = scenario.take_shared<TrustRegistry>();

    trust_registry::register_merchant(
        &mut registry,
        string::utf8(b"did:m1"),
        string::utf8(b""), // empty name
        @0x1,
        valid_pubkey(), string::utf8(b"shop1.com"), scenario.ctx(),
    );

    test_scenario::return_shared(registry);
    scenario.end();
}

// ============ Accessor Tests ============

#[test]
fun test_merchant_entry_accessors() {
    let mut scenario = test_scenario::begin(ADMIN);
    trust_registry::init_for_testing(scenario.ctx());

    scenario.next_tx(ADMIN);
    let mut registry = scenario.take_shared<TrustRegistry>();

    trust_registry::register_merchant(
        &mut registry,
        string::utf8(b"did:test"), string::utf8(b"TestShop"), @0xABC,
        valid_pubkey(), string::utf8(b"test.com"), scenario.ctx(),
    );

    let entry = trust_registry::lookup_merchant(&registry, string::utf8(b"did:test"));
    assert!(trust_registry::merchant_did(entry) == string::utf8(b"did:test"));
    assert!(trust_registry::merchant_name(entry) == string::utf8(b"TestShop"));
    assert!(trust_registry::merchant_sui_address(entry) == @0xABC);
    assert!(trust_registry::merchant_public_key(entry) == valid_pubkey());
    assert!(trust_registry::merchant_hostname(entry) == string::utf8(b"test.com"));
    assert!(trust_registry::merchant_active(entry) == true);

    test_scenario::return_shared(registry);
    scenario.end();
}
