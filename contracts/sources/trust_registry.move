/// Merchant Trust Registry for identiPay.
/// Stores verified merchant identities and their public keys for ECDH
/// artifact encryption. Wallets cross-reference merchants against this
/// registry before signing any intent (per whitepaper section 6).
module identipay::trust_registry;

use std::string::String;
use sui::event;
use sui::table::{Self, Table};

// ============ Structs ============

/// Shared registry of verified merchants. Created once at package publish.
public struct TrustRegistry has key {
    id: UID,
    /// Admin capability holder (can register merchants)
    admin: address,
    /// DID -> MerchantEntry lookup
    merchants: Table<String, MerchantEntry>,
}

/// A registered merchant's identity and public key.
public struct MerchantEntry has store, copy, drop {
    /// Decentralized Identifier (e.g., "did:identipay:shop.example.com:merchant123")
    did: String,
    /// Human-readable merchant name
    name: String,
    /// Merchant's Sui address for receiving payments
    sui_address: address,
    /// Merchant's public key for ECDH artifact encryption (X25519, 32 bytes).
    /// Buyers use this to derive the shared secret for encrypting receipt/warranty payloads.
    public_key: vector<u8>,
    /// Merchant's hostname (for URI resolution)
    hostname: String,
    /// Whether the merchant is currently active
    active: bool,
}

/// Emitted when a merchant is registered.
public struct MerchantRegistered has copy, drop {
    did: String,
    name: String,
    sui_address: address,
    hostname: String,
}

/// Emitted when a merchant is deactivated.
public struct MerchantDeactivated has copy, drop {
    did: String,
}

// ============ Constants ============

const ENotAdmin: u64 = 0;
const EMerchantAlreadyExists: u64 = 1;
const EMerchantNotFound: u64 = 2;
const EInvalidPublicKeyLength: u64 = 3;
const EEmptyDID: u64 = 4;
const EEmptyName: u64 = 5;

const PUBKEY_LENGTH: u64 = 32;

// ============ Init ============

/// Create the shared TrustRegistry. Called once on package publish.
fun init(ctx: &mut TxContext) {
    let registry = TrustRegistry {
        id: object::new(ctx),
        admin: ctx.sender(),
        merchants: table::new(ctx),
    };
    transfer::share_object(registry);
}

#[test_only]
public fun init_for_testing(ctx: &mut TxContext) {
    init(ctx);
}

// ============ Admin Functions ============

/// Register a new merchant in the trust registry.
/// Only callable by the admin.
entry fun register_merchant(
    registry: &mut TrustRegistry,
    did: String,
    name: String,
    sui_address: address,
    public_key: vector<u8>,
    hostname: String,
    ctx: &TxContext,
) {
    assert!(ctx.sender() == registry.admin, ENotAdmin);
    assert!(!did.is_empty(), EEmptyDID);
    assert!(!name.is_empty(), EEmptyName);
    assert!(public_key.length() == PUBKEY_LENGTH, EInvalidPublicKeyLength);
    assert!(!registry.merchants.contains(did), EMerchantAlreadyExists);

    let entry = MerchantEntry {
        did,
        name,
        sui_address,
        public_key,
        hostname,
        active: true,
    };

    event::emit(MerchantRegistered {
        did,
        name,
        sui_address,
        hostname,
    });

    registry.merchants.add(did, entry);
}

/// Deactivate a merchant (soft delete).
entry fun deactivate_merchant(
    registry: &mut TrustRegistry,
    did: String,
    ctx: &TxContext,
) {
    assert!(ctx.sender() == registry.admin, ENotAdmin);
    assert!(registry.merchants.contains(did), EMerchantNotFound);

    let entry = registry.merchants.borrow_mut(did);
    entry.active = false;

    event::emit(MerchantDeactivated { did });
}

/// Update a merchant's public key (e.g., key rotation).
entry fun update_merchant_key(
    registry: &mut TrustRegistry,
    did: String,
    new_public_key: vector<u8>,
    ctx: &TxContext,
) {
    assert!(ctx.sender() == registry.admin, ENotAdmin);
    assert!(new_public_key.length() == PUBKEY_LENGTH, EInvalidPublicKeyLength);
    assert!(registry.merchants.contains(did), EMerchantNotFound);

    let entry = registry.merchants.borrow_mut(did);
    entry.public_key = new_public_key;
}

// ============ Read Functions ============

/// Look up a merchant by DID. Aborts if not found.
public fun lookup_merchant(registry: &TrustRegistry, did: String): &MerchantEntry {
    assert!(registry.merchants.contains(did), EMerchantNotFound);
    registry.merchants.borrow(did)
}

/// Check if a merchant exists and is active.
public fun is_merchant_active(registry: &TrustRegistry, did: String): bool {
    if (!registry.merchants.contains(did)) {
        return false
    };
    let entry = registry.merchants.borrow(did);
    entry.active
}

// ============ MerchantEntry Accessors ============

public fun merchant_did(entry: &MerchantEntry): String { entry.did }
public fun merchant_name(entry: &MerchantEntry): String { entry.name }
public fun merchant_sui_address(entry: &MerchantEntry): address { entry.sui_address }
public fun merchant_public_key(entry: &MerchantEntry): vector<u8> { entry.public_key }
public fun merchant_hostname(entry: &MerchantEntry): String { entry.hostname }
public fun merchant_active(entry: &MerchantEntry): bool { entry.active }
