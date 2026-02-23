/// Meta-Address Registry for identiPay stealth identity system.
/// Maps human-readable names (e.g., "krum" -> @krum.idpay) to stealth
/// meta-addresses (spend + view public keys). Names never resolve to
/// Sui addresses -- only to cryptographic public keys for stealth address
/// derivation. Per whitepaper section 4.3.
///
/// Anti-sybil: one government credential (identity commitment) can register
/// exactly one name. Registration requires a ZK proof that the commitment
/// is backed by a valid government credential.
module identipay::meta_address_registry;

use std::string::String;
use sui::event;
use sui::table::{Self, Table};
use identipay::zk_verifier::{Self, VerificationKey};

// ============ Structs ============

/// Shared registry mapping names to stealth meta-addresses.
/// Per whitepaper: "The registry stores public keys only -- no Sui address is recorded."
public struct MetaAddressRegistry has key {
    id: UID,
    /// name -> MetaAddressEntry object ID (for ownership tracking)
    names: Table<String, ID>,
    /// name -> MetaAddress (spend + view pubkeys) for direct on-chain resolution.
    /// This is the primary lookup used by resolve_name(). Pubkeys are stored here
    /// so any on-chain caller can resolve a name without needing the owned entry object.
    meta_addresses: Table<String, MetaAddress>,
    /// identity_commitment -> name (anti-sybil: one credential = one name)
    commitments: Table<vector<u8>, String>,
}

/// On-chain lookup record: the public keys needed for stealth address derivation.
/// Stored in the registry's meta_addresses table for direct resolution.
/// No Sui address -- only cryptographic public keys.
public struct MetaAddress has store, copy, drop {
    /// Ed25519 spending public key (32 bytes)
    spend_pubkey: vector<u8>,
    /// X25519 viewing public key (32 bytes)
    view_pubkey: vector<u8>,
}

/// Owned object representing a registered name. Held by the registrant
/// so they can rotate keys.
public struct MetaAddressEntry has key, store {
    id: UID,
    /// The registered name (e.g., "krum")
    name: String,
    /// Ed25519 spending public key (32 bytes) -- for stealth address derivation
    spend_pubkey: vector<u8>,
    /// X25519 viewing public key (32 bytes) -- for detecting incoming payments
    view_pubkey: vector<u8>,
    /// Poseidon hash of credential fields (anti-sybil binding)
    identity_commitment: vector<u8>,
    /// Registration timestamp
    created_at: u64,
}

/// Emitted on successful name registration.
public struct NameRegistered has copy, drop {
    name: String,
    spend_pubkey: vector<u8>,
    view_pubkey: vector<u8>,
    identity_commitment: vector<u8>,
}

/// Emitted on key rotation.
public struct KeysRotated has copy, drop {
    name: String,
    new_spend_pubkey: vector<u8>,
    new_view_pubkey: vector<u8>,
}

// ============ Constants ============

const ENameAlreadyTaken: u64 = 0;
const ECommitmentAlreadyRegistered: u64 = 1;
const EInvalidNameLength: u64 = 2;
const EInvalidNameCharacter: u64 = 3;
const EInvalidSpendPubkeyLength: u64 = 4;
const EInvalidViewPubkeyLength: u64 = 5;
const EEmptyIdentityCommitment: u64 = 6;
const EProofVerificationFailed: u64 = 7;
const ENameNotFound: u64 = 8;

const PUBKEY_LENGTH: u64 = 32;
const MIN_NAME_LENGTH: u64 = 3;
const MAX_NAME_LENGTH: u64 = 20;

// ============ Init ============

fun init(ctx: &mut TxContext) {
    let registry = MetaAddressRegistry {
        id: object::new(ctx),
        names: table::new(ctx),
        meta_addresses: table::new(ctx),
        commitments: table::new(ctx),
    };
    transfer::share_object(registry);
}

#[test_only]
public fun init_for_testing(ctx: &mut TxContext) {
    init(ctx);
}

// ============ Public Functions ============

/// Register a new name with a stealth meta-address.
/// Requires a ZK proof that the identity commitment is backed by a valid credential.
/// One commitment can only register one name (anti-sybil).
entry fun register_name(
    registry: &mut MetaAddressRegistry,
    vk: &VerificationKey,
    name: String,
    spend_pubkey: vector<u8>,
    view_pubkey: vector<u8>,
    identity_commitment: vector<u8>,
    zk_proof: vector<u8>,
    zk_public_inputs: vector<u8>,
    ctx: &mut TxContext,
) {
    // Validate name format
    validate_name(&name);

    // Validate key lengths
    assert!(spend_pubkey.length() == PUBKEY_LENGTH, EInvalidSpendPubkeyLength);
    assert!(view_pubkey.length() == PUBKEY_LENGTH, EInvalidViewPubkeyLength);
    assert!(!identity_commitment.is_empty(), EEmptyIdentityCommitment);

    // Check uniqueness
    assert!(!registry.names.contains(name), ENameAlreadyTaken);
    assert!(!registry.commitments.contains(identity_commitment), ECommitmentAlreadyRegistered);

    // Verify ZK proof of valid credential
    let proof_valid = zk_verifier::verify_proof(vk, &zk_proof, &zk_public_inputs);
    assert!(proof_valid, EProofVerificationFailed);

    // Create the entry (owned by the registrant for key rotation)
    let entry = MetaAddressEntry {
        id: object::new(ctx),
        name,
        spend_pubkey,
        view_pubkey,
        identity_commitment,
        created_at: ctx.epoch_timestamp_ms(),
    };

    // Record name -> entry object ID mapping (for ownership tracking)
    registry.names.add(name, object::id(&entry));
    // Record name -> meta-address (spend + view pubkeys) for on-chain resolution
    registry.meta_addresses.add(name, MetaAddress { spend_pubkey, view_pubkey });
    // Record commitment -> name mapping (anti-sybil)
    registry.commitments.add(identity_commitment, name);

    event::emit(NameRegistered {
        name,
        spend_pubkey,
        view_pubkey,
        identity_commitment,
    });

    transfer::transfer(entry, ctx.sender());
}

/// Rotate keys for a registered name. The caller must own the MetaAddressEntry.
/// Requires a ZK proof binding to the same identity commitment.
/// Updates both the owned entry and the registry's meta_addresses lookup table.
entry fun rotate_keys(
    registry: &mut MetaAddressRegistry,
    entry: &mut MetaAddressEntry,
    vk: &VerificationKey,
    new_spend_pubkey: vector<u8>,
    new_view_pubkey: vector<u8>,
    zk_proof: vector<u8>,
    zk_public_inputs: vector<u8>,
) {
    assert!(new_spend_pubkey.length() == PUBKEY_LENGTH, EInvalidSpendPubkeyLength);
    assert!(new_view_pubkey.length() == PUBKEY_LENGTH, EInvalidViewPubkeyLength);

    // Verify ZK proof (must prove same identity commitment)
    let proof_valid = zk_verifier::verify_proof(vk, &zk_proof, &zk_public_inputs);
    assert!(proof_valid, EProofVerificationFailed);

    // Update the owned entry
    entry.spend_pubkey = new_spend_pubkey;
    entry.view_pubkey = new_view_pubkey;

    // Update the registry lookup table so resolve_name returns fresh keys
    let meta = registry.meta_addresses.borrow_mut(entry.name);
    meta.spend_pubkey = new_spend_pubkey;
    meta.view_pubkey = new_view_pubkey;

    event::emit(KeysRotated {
        name: entry.name,
        new_spend_pubkey,
        new_view_pubkey,
    });
}

// ============ Read Functions ============

/// Resolve a name to its meta-address (spend + view public keys).
/// This is the primary lookup function per whitepaper section 4.5:
/// "Resolve the name from the on-chain registry: (K_spend, K_view)."
/// Any on-chain caller or off-chain reader can resolve a name directly
/// from the shared registry without needing the owned MetaAddressEntry.
public fun resolve_name(registry: &MetaAddressRegistry, name: String): (vector<u8>, vector<u8>) {
    assert!(registry.meta_addresses.contains(name), ENameNotFound);
    let meta = registry.meta_addresses.borrow(name);
    (meta.spend_pubkey, meta.view_pubkey)
}

/// Resolve from an owned MetaAddressEntry object directly.
/// Useful when the caller already has a reference to the entry.
public fun resolve_entry(entry: &MetaAddressEntry): (vector<u8>, vector<u8>) {
    (entry.spend_pubkey, entry.view_pubkey)
}

/// Check if a name is registered.
public fun name_exists(registry: &MetaAddressRegistry, name: String): bool {
    registry.names.contains(name)
}

/// Check if an identity commitment is already registered.
public fun commitment_exists(registry: &MetaAddressRegistry, commitment: vector<u8>): bool {
    registry.commitments.contains(commitment)
}

// ============ Accessors ============

public fun entry_name(entry: &MetaAddressEntry): String { entry.name }
public fun entry_spend_pubkey(entry: &MetaAddressEntry): vector<u8> { entry.spend_pubkey }
public fun entry_view_pubkey(entry: &MetaAddressEntry): vector<u8> { entry.view_pubkey }
public fun entry_identity_commitment(entry: &MetaAddressEntry): vector<u8> { entry.identity_commitment }
public fun entry_created_at(entry: &MetaAddressEntry): u64 { entry.created_at }

// ============ Internal Functions ============

/// Validate name format: 3-20 chars, lowercase alphanumeric + hyphens,
/// cannot start or end with hyphen.
fun validate_name(name: &String) {
    let bytes = name.as_bytes();
    let len = bytes.length();
    assert!(len >= MIN_NAME_LENGTH && len <= MAX_NAME_LENGTH, EInvalidNameLength);

    // Cannot start or end with hyphen
    assert!(*bytes.borrow(0) != 0x2D, EInvalidNameCharacter); // '-'
    assert!(*bytes.borrow(len - 1) != 0x2D, EInvalidNameCharacter);

    let mut i = 0;
    while (i < len) {
        let c = *bytes.borrow(i);
        // a-z (0x61-0x7A), 0-9 (0x30-0x39), hyphen (0x2D)
        let valid = (c >= 0x61 && c <= 0x7A) ||
                    (c >= 0x30 && c <= 0x39) ||
                    (c == 0x2D);
        assert!(valid, EInvalidNameCharacter);
        i = i + 1;
    };
}
