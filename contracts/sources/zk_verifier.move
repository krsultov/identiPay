/// ZK proof verification for identiPay using Groth16 over BN254.
/// Verifies eligibility proofs (e.g., age >= threshold) and identity
/// registration proofs. Uses the sui::groth16 native module.
///
/// Per the whitepaper (section 4.6): proofs are bound to the intent hash
/// to prevent replay. The verifier learns the predicate truth but not the
/// underlying attributes.
module identipay::zk_verifier;

use sui::groth16;

// ============ Structs ============

/// Stores a prepared verification key for a specific circuit.
/// Created once during initialization and shared for all verifications.
public struct VerificationKey has key, store {
    id: UID,
    /// Human-readable circuit identifier (e.g., "age_check", "identity_registration", "pool_spend")
    circuit_name: std::string::String,
    /// The prepared verifying key (pre-processed for efficient verification)
    pvk: groth16::PreparedVerifyingKey,
}

// ============ Constants ============

const EProofVerificationFailed: u64 = 0;
const EEmptyProof: u64 = 1;
const EEmptyPublicInputs: u64 = 2;

// ============ Public Functions ============

/// Create and share a new verification key for a circuit.
/// Called once during deployment to register each circuit's verification key.
/// The raw_vk is the serialized Groth16 verification key from the circuit's
/// trusted setup.
public fun create_verification_key(
    circuit_name: std::string::String,
    raw_vk: vector<u8>,
    ctx: &mut TxContext,
) {
    let curve = groth16::bn254();
    let pvk = groth16::prepare_verifying_key(&curve, &raw_vk);

    let vk = VerificationKey {
        id: object::new(ctx),
        circuit_name,
        pvk,
    };

    transfer::share_object(vk);
}

/// Verify a Groth16 proof against the stored verification key.
/// Returns true if valid, false otherwise. Does not abort -- the caller
/// (settlement module) decides what to do on failure.
public(package) fun verify_proof(
    vk: &VerificationKey,
    proof_bytes: &vector<u8>,
    public_inputs_bytes: &vector<u8>,
): bool {
    assert!(!proof_bytes.is_empty(), EEmptyProof);
    assert!(!public_inputs_bytes.is_empty(), EEmptyPublicInputs);

    let curve = groth16::bn254();
    let proof_points = groth16::proof_points_from_bytes(*proof_bytes);
    let public_inputs = groth16::public_proof_inputs_from_bytes(*public_inputs_bytes);

    groth16::verify_groth16_proof(&curve, &vk.pvk, &public_inputs, &proof_points)
}

/// Verify a proof and abort if invalid. Convenience wrapper for use in
/// settlement where an invalid proof must halt execution.
public(package) fun assert_proof_valid(
    vk: &VerificationKey,
    proof_bytes: &vector<u8>,
    public_inputs_bytes: &vector<u8>,
) {
    let valid = verify_proof(vk, proof_bytes, public_inputs_bytes);
    assert!(valid, EProofVerificationFailed);
}

// ============ Accessors ============

public fun circuit_name(vk: &VerificationKey): std::string::String { vk.circuit_name }

// ============ Test Helpers ============

#[test_only]
public fun test_verify_proof(
    vk: &VerificationKey,
    proof_bytes: &vector<u8>,
    public_inputs_bytes: &vector<u8>,
): bool {
    verify_proof(vk, proof_bytes, public_inputs_bytes)
}

#[test_only]
public fun test_assert_proof_valid(
    vk: &VerificationKey,
    proof_bytes: &vector<u8>,
    public_inputs_bytes: &vector<u8>,
) {
    assert_proof_valid(vk, proof_bytes, public_inputs_bytes);
}

#[test_only]
public fun test_destroy_vk(vk: VerificationKey) {
    let VerificationKey { id, circuit_name: _, pvk: _ } = vk;
    object::delete(id);
}
