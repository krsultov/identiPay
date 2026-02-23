#[test_only]
module identipay::shielded_pool_tests;

use sui::test_scenario;
use sui::coin;
use sui::sui::SUI;
use identipay::shielded_pool;

// Note: We use SUI as the test coin type since it's available in the
// test framework. In production, this would be USDC.

// ============ Test Helpers ============

fun valid_commitment(): vector<u8> {
    vector[
        0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF, 0x11, 0x22,
        0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
        0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF, 0x11, 0x22,
        0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
    ]
}

fun alt_commitment(): vector<u8> {
    vector[
        0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
        0x99, 0x00, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF,
        0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
        0x99, 0x00, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF,
    ]
}

const DEPOSITOR: address = @0xD1;

// ============ Pool Creation Tests ============

#[test]
fun test_create_pool() {
    let mut scenario = test_scenario::begin(DEPOSITOR);

    shielded_pool::create_pool_for_testing<SUI>(scenario.ctx());

    scenario.next_tx(DEPOSITOR);
    let pool = scenario.take_shared<shielded_pool::ShieldedPool<SUI>>();

    assert!(shielded_pool::pool_balance(&pool) == 0);
    assert!(shielded_pool::next_leaf_index(&pool) == 0);

    test_scenario::return_shared(pool);
    scenario.end();
}

// ============ Deposit Tests ============

#[test]
fun test_deposit_success() {
    let mut scenario = test_scenario::begin(DEPOSITOR);

    shielded_pool::create_pool_for_testing<SUI>(scenario.ctx());

    scenario.next_tx(DEPOSITOR);
    let mut pool = scenario.take_shared<shielded_pool::ShieldedPool<SUI>>();

    let test_coin = coin::mint_for_testing<SUI>(1000, scenario.ctx());

    shielded_pool::deposit(
        &mut pool,
        test_coin,
        valid_commitment(),
        scenario.ctx(),
    );

    assert!(shielded_pool::pool_balance(&pool) == 1000);
    assert!(shielded_pool::next_leaf_index(&pool) == 1);

    test_scenario::return_shared(pool);
    scenario.end();
}

#[test]
fun test_multiple_deposits() {
    let mut scenario = test_scenario::begin(DEPOSITOR);
    shielded_pool::create_pool_for_testing<SUI>(scenario.ctx());

    scenario.next_tx(DEPOSITOR);
    let mut pool = scenario.take_shared<shielded_pool::ShieldedPool<SUI>>();

    let coin1 = coin::mint_for_testing<SUI>(500, scenario.ctx());
    shielded_pool::deposit(&mut pool, coin1, valid_commitment(), scenario.ctx());

    let coin2 = coin::mint_for_testing<SUI>(300, scenario.ctx());
    shielded_pool::deposit(&mut pool, coin2, alt_commitment(), scenario.ctx());

    assert!(shielded_pool::pool_balance(&pool) == 800);
    assert!(shielded_pool::next_leaf_index(&pool) == 2);

    test_scenario::return_shared(pool);
    scenario.end();
}

#[test]
fun test_deposit_single_unit() {
    let mut scenario = test_scenario::begin(DEPOSITOR);
    shielded_pool::create_pool_for_testing<SUI>(scenario.ctx());

    scenario.next_tx(DEPOSITOR);
    let mut pool = scenario.take_shared<shielded_pool::ShieldedPool<SUI>>();

    let coin = coin::mint_for_testing<SUI>(1, scenario.ctx());
    shielded_pool::deposit(&mut pool, coin, valid_commitment(), scenario.ctx());

    assert!(shielded_pool::pool_balance(&pool) == 1);

    test_scenario::return_shared(pool);
    scenario.end();
}

#[test]
fun test_deposit_large_amount() {
    let mut scenario = test_scenario::begin(DEPOSITOR);
    shielded_pool::create_pool_for_testing<SUI>(scenario.ctx());

    scenario.next_tx(DEPOSITOR);
    let mut pool = scenario.take_shared<shielded_pool::ShieldedPool<SUI>>();

    let coin = coin::mint_for_testing<SUI>(1_000_000_000_000, scenario.ctx());
    shielded_pool::deposit(&mut pool, coin, valid_commitment(), scenario.ctx());

    assert!(shielded_pool::pool_balance(&pool) == 1_000_000_000_000);

    test_scenario::return_shared(pool);
    scenario.end();
}

// ============ Deposit Error Tests ============

#[test]
#[expected_failure(abort_code = identipay::shielded_pool::EEmptyCommitment)]
fun test_deposit_empty_commitment() {
    let mut scenario = test_scenario::begin(DEPOSITOR);
    shielded_pool::create_pool_for_testing<SUI>(scenario.ctx());

    scenario.next_tx(DEPOSITOR);
    let mut pool = scenario.take_shared<shielded_pool::ShieldedPool<SUI>>();

    let coin = coin::mint_for_testing<SUI>(1000, scenario.ctx());
    shielded_pool::deposit(&mut pool, coin, vector[], scenario.ctx());

    test_scenario::return_shared(pool);
    scenario.end();
}

#[test]
#[expected_failure(abort_code = identipay::shielded_pool::EInvalidAmount)]
fun test_deposit_zero_amount() {
    let mut scenario = test_scenario::begin(DEPOSITOR);
    shielded_pool::create_pool_for_testing<SUI>(scenario.ctx());

    scenario.next_tx(DEPOSITOR);
    let mut pool = scenario.take_shared<shielded_pool::ShieldedPool<SUI>>();

    let coin = coin::mint_for_testing<SUI>(0, scenario.ctx());
    shielded_pool::deposit(&mut pool, coin, valid_commitment(), scenario.ctx());

    test_scenario::return_shared(pool);
    scenario.end();
}

// ============ Accessor Tests ============

#[test]
fun test_pool_accessors() {
    let mut scenario = test_scenario::begin(DEPOSITOR);
    shielded_pool::create_pool_for_testing<SUI>(scenario.ctx());

    scenario.next_tx(DEPOSITOR);
    let mut pool = scenario.take_shared<shielded_pool::ShieldedPool<SUI>>();

    // Empty pool state
    assert!(shielded_pool::pool_balance(&pool) == 0);
    assert!(shielded_pool::next_leaf_index(&pool) == 0);
    assert!(!shielded_pool::is_nullifier_spent(&pool, vector[0x01]));

    // After deposit
    let coin = coin::mint_for_testing<SUI>(100, scenario.ctx());
    shielded_pool::deposit(&mut pool, coin, valid_commitment(), scenario.ctx());

    assert!(shielded_pool::pool_balance(&pool) == 100);
    assert!(shielded_pool::next_leaf_index(&pool) == 1);
    let _root = shielded_pool::merkle_root(&pool);

    test_scenario::return_shared(pool);
    scenario.end();
}

#[test]
fun test_nullifier_not_spent_initially() {
    let mut scenario = test_scenario::begin(DEPOSITOR);
    shielded_pool::create_pool_for_testing<SUI>(scenario.ctx());

    scenario.next_tx(DEPOSITOR);
    let pool = scenario.take_shared<shielded_pool::ShieldedPool<SUI>>();

    assert!(!shielded_pool::is_nullifier_spent(&pool, vector[0x01, 0x02]));
    assert!(!shielded_pool::is_nullifier_spent(&pool, valid_commitment()));

    test_scenario::return_shared(pool);
    scenario.end();
}

// Note: Withdraw tests require a valid Groth16 proof and VerificationKey,
// which cannot be created in Move unit tests without a real trusted setup.
// Withdraw functionality (ZK verification, nullifier tracking, balance
// splitting) is tested in integration tests with actual circuit artifacts.
//
// What we CAN verify about withdraw in unit tests:
// - Input validation (amount > 0, non-empty nullifier, sufficient balance)
// - These would require a VerificationKey object, so they're also
//   deferred to integration tests.
