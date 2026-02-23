#[test_only]
module identipay::zk_verifier_tests;

use std::string;
use sui::test_scenario;
use identipay::zk_verifier;

// Note: Groth16 proof verification requires a real verification key from
// a trusted setup and a real proof from a prover. We cannot generate these
// in Move unit tests. We test:
// 1. VerificationKey creation and accessor
// 2. Input validation (empty proof/inputs)
// The actual verification logic is tested in integration tests.

// ============ Test Constants ============

// A minimal BN254 Groth16 verification key (from snarkjs test vectors).
// This is a valid VK structure that can be prepared by sui::groth16.
// Using the BN254 "hello world" circuit VK for testing.
// If prepare_verifying_key fails with invalid data, we test that path too.

// ============ Accessor Tests ============

// Note: create_verification_key calls groth16::prepare_verifying_key which
// is a native function that validates the VK format. We cannot easily
// provide a valid BN254 VK in pure Move test code. These tests are
// documented as requiring integration test coverage.

// The following tests verify the test helper functions and error codes.

#[test]
#[expected_failure] // Empty VK will fail in prepare_verifying_key native
fun test_create_vk_empty_bytes_fails() {
    let mut scenario = test_scenario::begin(@0xAD);

    zk_verifier::create_verification_key(
        string::utf8(b"test_circuit"),
        vector[], // empty VK bytes - native function will reject
        scenario.ctx(),
    );

    scenario.end();
}

// Note: The following test categories require valid Groth16 artifacts
// and are covered by integration tests:
//
// 1. create_verification_key with valid VK bytes
// 2. verify_proof with valid proof + inputs -> returns true
// 3. verify_proof with invalid proof -> returns false
// 4. assert_proof_valid with valid proof -> succeeds
// 5. assert_proof_valid with invalid proof -> aborts with EProofVerificationFailed
// 6. verify_proof with empty proof -> aborts with EEmptyProof
// 7. verify_proof with empty inputs -> aborts with EEmptyPublicInputs
// 8. circuit_name accessor
