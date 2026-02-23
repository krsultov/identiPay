/// Warranty objects for identiPay commerce settlements.
/// Warranties are soulbound by default (bound to the stealth address holder)
/// or transferable if flagged (for resale scenarios). Expiry is in plaintext
/// for on-chain enforcement; terms are encrypted like receipt payloads.
#[allow(lint(custom_state_change))]
module identipay::warranty;

use sui::event;

// ============ Structs ============

/// On-chain warranty object minted during atomic settlement.
public struct WarrantyObject has key, store {
    id: UID,
    /// Reference to the associated receipt
    receipt_id: ID,
    /// Merchant who issued the warranty
    merchant: address,
    /// Intent hash binding this warranty to a specific transaction
    intent_hash: vector<u8>,
    /// AES-256-GCM ciphertext of warranty terms (duration, exclusions, service endpoints)
    encrypted_terms: vector<u8>,
    /// 12-byte GCM nonce for terms decryption
    terms_nonce: vector<u8>,
    /// Ephemeral public key E = e*G (same as receipt, for ECDH decryption)
    ephemeral_pubkey: vector<u8>,
    /// Warranty expiry timestamp in epoch ms (plaintext for on-chain enforcement)
    expiry: u64,
    /// Whether this warranty can be transferred (e.g., on resale)
    transferable: bool,
}

/// Emitted when a warranty is minted.
public struct WarrantyMinted has copy, drop {
    warranty_id: ID,
    receipt_id: ID,
    merchant: address,
    intent_hash: vector<u8>,
    expiry: u64,
    transferable: bool,
}

/// Emitted when a warranty is transferred.
public struct WarrantyTransferred has copy, drop {
    warranty_id: ID,
    from: address,
    to: address,
}

// ============ Constants ============

const EWarrantyNotTransferable: u64 = 0;
const EWarrantyExpired: u64 = 1;
const EInvalidNonceLength: u64 = 2;
const EInvalidEphemeralPubkey: u64 = 3;
const EEmptyTerms: u64 = 4;
const EInvalidExpiry: u64 = 5;

const NONCE_LENGTH: u64 = 12;
const PUBKEY_LENGTH: u64 = 32;

// ============ Public Functions ============

/// Mint a new warranty. Called by the settlement module during atomic execution.
public(package) fun mint_warranty(
    receipt_id: ID,
    merchant: address,
    intent_hash: vector<u8>,
    encrypted_terms: vector<u8>,
    terms_nonce: vector<u8>,
    ephemeral_pubkey: vector<u8>,
    expiry: u64,
    transferable: bool,
    ctx: &mut TxContext,
): WarrantyObject {
    assert!(terms_nonce.length() == NONCE_LENGTH, EInvalidNonceLength);
    assert!(ephemeral_pubkey.length() == PUBKEY_LENGTH, EInvalidEphemeralPubkey);
    assert!(!encrypted_terms.is_empty(), EEmptyTerms);
    assert!(expiry > 0, EInvalidExpiry);

    let warranty = WarrantyObject {
        id: object::new(ctx),
        receipt_id,
        merchant,
        intent_hash,
        encrypted_terms,
        terms_nonce,
        ephemeral_pubkey,
        expiry,
        transferable,
    };

    event::emit(WarrantyMinted {
        warranty_id: object::id(&warranty),
        receipt_id,
        merchant,
        intent_hash: warranty.intent_hash,
        expiry,
        transferable,
    });

    warranty
}

/// Transfer a warranty to a new address. Only allowed if the warranty is
/// marked as transferable and has not expired.
public fun transfer_warranty(
    warranty: WarrantyObject,
    recipient: address,
    ctx: &TxContext,
) {
    assert!(warranty.transferable, EWarrantyNotTransferable);
    assert!(warranty.expiry > ctx.epoch_timestamp_ms(), EWarrantyExpired);

    event::emit(WarrantyTransferred {
        warranty_id: object::id(&warranty),
        from: ctx.sender(),
        to: recipient,
    });

    transfer::transfer(warranty, recipient);
}

// ============ Test Helpers ============

#[test_only]
public fun test_mint_warranty(
    receipt_id: ID,
    merchant: address,
    intent_hash: vector<u8>,
    encrypted_terms: vector<u8>,
    terms_nonce: vector<u8>,
    ephemeral_pubkey: vector<u8>,
    expiry: u64,
    transferable: bool,
    ctx: &mut TxContext,
): WarrantyObject {
    mint_warranty(receipt_id, merchant, intent_hash, encrypted_terms, terms_nonce, ephemeral_pubkey, expiry, transferable, ctx)
}

#[test_only]
public fun test_destroy(warranty: WarrantyObject) {
    let WarrantyObject { id, receipt_id: _, merchant: _, intent_hash: _, encrypted_terms: _, terms_nonce: _, ephemeral_pubkey: _, expiry: _, transferable: _ } = warranty;
    object::delete(id);
}

// ============ Accessors ============

public fun receipt_id(warranty: &WarrantyObject): ID { warranty.receipt_id }
public fun merchant(warranty: &WarrantyObject): address { warranty.merchant }
public fun expiry(warranty: &WarrantyObject): u64 { warranty.expiry }
public fun is_transferable(warranty: &WarrantyObject): bool { warranty.transferable }
public fun is_expired(warranty: &WarrantyObject, ctx: &TxContext): bool {
    warranty.expiry <= ctx.epoch_timestamp_ms()
}
