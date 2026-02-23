/// Receipt objects for identiPay commerce settlements.
/// Receipts are minted atomically during settlement and delivered to the buyer's
/// one-time stealth address. The payload (items, amounts) is AES-256-GCM encrypted
/// off-chain -- the contract stores opaque ciphertext decryptable only by the buyer
/// (via stealth key) and the merchant (via merchant private key + ephemeral pubkey ECDH).
module identipay::receipt;

use sui::event;

// ============ Structs ============

/// On-chain receipt object minted during atomic settlement.
/// Transferred to the buyer's stealth address.
public struct ReceiptObject has key, store {
    id: UID,
    /// Merchant's Sui address (plaintext -- needed for settlement routing)
    merchant: address,
    /// SHA3-256 hash of the canonicalized intent (plaintext -- needed for verification)
    intent_hash: vector<u8>,
    /// AES-256-GCM ciphertext of the receipt payload (items, amounts, merchant name)
    encrypted_payload: vector<u8>,
    /// 12-byte GCM nonce
    payload_nonce: vector<u8>,
    /// Ephemeral public key E = e*G (for merchant ECDH decryption)
    ephemeral_pubkey: vector<u8>,
    /// Settlement timestamp (epoch ms)
    timestamp: u64,
}

/// Emitted when a receipt is minted.
public struct ReceiptMinted has copy, drop {
    receipt_id: ID,
    merchant: address,
    intent_hash: vector<u8>,
    timestamp: u64,
}

// ============ Constants ============

const EInvalidNonceLength: u64 = 0;
const EInvalidEphemeralPubkey: u64 = 1;
const EEmptyPayload: u64 = 2;
const EEmptyIntentHash: u64 = 3;

const NONCE_LENGTH: u64 = 12;
const PUBKEY_LENGTH: u64 = 32;

// ============ Public Functions ============

/// Mint a new receipt. Called by the settlement module during atomic execution.
/// The encrypted_payload is prepared off-chain by the buyer's wallet.
public(package) fun mint_receipt(
    merchant: address,
    intent_hash: vector<u8>,
    encrypted_payload: vector<u8>,
    payload_nonce: vector<u8>,
    ephemeral_pubkey: vector<u8>,
    ctx: &mut TxContext,
): ReceiptObject {
    assert!(payload_nonce.length() == NONCE_LENGTH, EInvalidNonceLength);
    assert!(ephemeral_pubkey.length() == PUBKEY_LENGTH, EInvalidEphemeralPubkey);
    assert!(!encrypted_payload.is_empty(), EEmptyPayload);
    assert!(!intent_hash.is_empty(), EEmptyIntentHash);

    let receipt = ReceiptObject {
        id: object::new(ctx),
        merchant,
        intent_hash,
        encrypted_payload,
        payload_nonce,
        ephemeral_pubkey,
        timestamp: ctx.epoch_timestamp_ms(),
    };

    event::emit(ReceiptMinted {
        receipt_id: object::id(&receipt),
        merchant,
        intent_hash: receipt.intent_hash,
        timestamp: receipt.timestamp,
    });

    receipt
}

// ============ Accessors ============

// ============ Test Helpers ============

#[test_only]
public fun test_mint_receipt(
    merchant: address,
    intent_hash: vector<u8>,
    encrypted_payload: vector<u8>,
    payload_nonce: vector<u8>,
    ephemeral_pubkey: vector<u8>,
    ctx: &mut TxContext,
): ReceiptObject {
    mint_receipt(merchant, intent_hash, encrypted_payload, payload_nonce, ephemeral_pubkey, ctx)
}

#[test_only]
public fun test_destroy(receipt: ReceiptObject) {
    let ReceiptObject { id, merchant: _, intent_hash: _, encrypted_payload: _, payload_nonce: _, ephemeral_pubkey: _, timestamp: _ } = receipt;
    object::delete(id);
}

// ============ Accessors ============

public fun merchant(receipt: &ReceiptObject): address { receipt.merchant }
public fun intent_hash(receipt: &ReceiptObject): vector<u8> { receipt.intent_hash }
public fun id(receipt: &ReceiptObject): ID { object::id(receipt) }
public fun timestamp(receipt: &ReceiptObject): u64 { receipt.timestamp }
