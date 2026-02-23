/// Settlement orchestrator for identiPay.
/// The core execute_commerce entry function that atomically:
/// 1. Verifies the ZK eligibility proof (if age-gated)
/// 2. Verifies the buyer's intent signature (Ed25519 over intent hash)
/// 3. Transfers USDC to the merchant
/// 4. Mints an encrypted receipt to the buyer's stealth address
/// 5. Optionally mints an encrypted warranty to the buyer's stealth address
/// 6. Emits a settlement event
///
/// Per whitepaper section 5 (main.tex:334-376): the PTB either succeeds
/// entirely or fails entirely -- no partial execution.
module identipay::settlement;

use sui::coin::Coin;
use sui::event;
use sui::table::{Self, Table};
use identipay::receipt;
use identipay::warranty;
use identipay::intent;
use identipay::zk_verifier::{Self, VerificationKey};

// ============ Structs ============

/// Shared state for settlement replay protection.
/// Tracks executed intent hashes to prevent the same signed intent
/// from being settled more than once.
public struct SettlementState has key {
    id: UID,
    /// intent_hash -> true (prevents replay of the same signed intent)
    executed_intents: Table<vector<u8>, bool>,
}

/// Emitted on successful settlement. Indexed by intent_hash (not buyer identity).
public struct SettlementEvent has copy, drop {
    intent_hash: vector<u8>,
    merchant: address,
    amount: u64,
    receipt_id: ID,
    warranty_id: Option<ID>,
    buyer_stealth_address: address,
}

// ============ Constants ============

const EInvalidAmount: u64 = 0;
const EProposalExpired: u64 = 1;
const EIntentAlreadyExecuted: u64 = 2;

// ============ Init ============

fun init(ctx: &mut TxContext) {
    let state = SettlementState {
        id: object::new(ctx),
        executed_intents: table::new(ctx),
    };
    transfer::share_object(state);
}

#[test_only]
public fun init_for_testing(ctx: &mut TxContext) {
    init(ctx);
}

// ============ Entry Functions ============

/// Execute a full commerce settlement atomically.
///
/// This is the main entry point for identiPay transactions.
/// The buyer's wallet constructs a PTB calling this function with all
/// required parameters after:
/// - Signing the intent hash
/// - Generating ZK proof (if constraints require it)
/// - Computing a stealth address for artifact delivery
/// - Encrypting the artifact payload via ECDH with merchant's public key
///
/// Generic over token type T (typically USDC on testnet).
entry fun execute_commerce<T>(
    state: &mut SettlementState,
    // Payment
    payment: Coin<T>,
    merchant: address,
    // Buyer's one-time stealth address for receiving artifacts
    buyer_stealth_addr: address,
    // Intent verification
    intent_sig: vector<u8>,
    intent_hash: vector<u8>,
    buyer_pubkey: vector<u8>,
    // Proposal expiry (epoch ms) -- must be in the future
    proposal_expiry: u64,
    // ZK proof (eligibility / age check)
    zk_vk: &VerificationKey,
    zk_proof: vector<u8>,
    zk_public_inputs: vector<u8>,
    // Encrypted receipt payload
    encrypted_payload: vector<u8>,
    payload_nonce: vector<u8>,
    ephemeral_pubkey: vector<u8>,
    // Warranty (optional -- pass empty vectors if no warranty)
    encrypted_warranty_terms: vector<u8>,
    warranty_terms_nonce: vector<u8>,
    warranty_expiry: u64,
    warranty_transferable: bool,
    ctx: &mut TxContext,
) {
    let amount = payment.value();
    assert!(amount > 0, EInvalidAmount);

    // 0a. Check proposal expiry (whitepaper: "expired proposal" fails)
    assert!(proposal_expiry > ctx.epoch_timestamp_ms(), EProposalExpired);

    // 0b. Replay protection: ensure this intent hasn't been settled before
    assert!(!state.executed_intents.contains(intent_hash), EIntentAlreadyExecuted);
    state.executed_intents.add(intent_hash, true);

    // 1. Verify ZK proof of buyer eligibility
    zk_verifier::assert_proof_valid(zk_vk, &zk_proof, &zk_public_inputs);

    // 2. Verify intent signature (buyer signed the canonical intent hash)
    intent::verify_intent_signature(&intent_sig, &intent_hash, &buyer_pubkey);

    // 3. Transfer payment to merchant
    transfer::public_transfer(payment, merchant);

    // 4. Mint encrypted receipt to buyer's stealth address
    let receipt_obj = receipt::mint_receipt(
        merchant,
        intent_hash,
        encrypted_payload,
        payload_nonce,
        ephemeral_pubkey,
        ctx,
    );
    let receipt_id = receipt::id(&receipt_obj);
    transfer::public_transfer(receipt_obj, buyer_stealth_addr);

    // 5. Mint warranty if applicable (non-empty terms indicates warranty requested)
    let warranty_id = if (!encrypted_warranty_terms.is_empty()) {
        let warranty_obj = warranty::mint_warranty(
            receipt_id,
            merchant,
            intent_hash,
            encrypted_warranty_terms,
            warranty_terms_nonce,
            ephemeral_pubkey,
            warranty_expiry,
            warranty_transferable,
            ctx,
        );
        let wid = option::some(object::id(&warranty_obj));
        transfer::public_transfer(warranty_obj, buyer_stealth_addr);
        wid
    } else {
        option::none()
    };

    // 6. Emit settlement event (indexed by intent hash, NOT by buyer identity)
    event::emit(SettlementEvent {
        intent_hash,
        merchant,
        amount,
        receipt_id,
        warranty_id,
        buyer_stealth_address: buyer_stealth_addr,
    });
}

/// Execute a commerce settlement without ZK proof requirement.
/// For transactions that don't have age gates or other constraints.
entry fun execute_commerce_no_zk<T>(
    state: &mut SettlementState,
    payment: Coin<T>,
    merchant: address,
    buyer_stealth_addr: address,
    intent_sig: vector<u8>,
    intent_hash: vector<u8>,
    buyer_pubkey: vector<u8>,
    proposal_expiry: u64,
    encrypted_payload: vector<u8>,
    payload_nonce: vector<u8>,
    ephemeral_pubkey: vector<u8>,
    encrypted_warranty_terms: vector<u8>,
    warranty_terms_nonce: vector<u8>,
    warranty_expiry: u64,
    warranty_transferable: bool,
    ctx: &mut TxContext,
) {
    let amount = payment.value();
    assert!(amount > 0, EInvalidAmount);

    // 0a. Check proposal expiry
    assert!(proposal_expiry > ctx.epoch_timestamp_ms(), EProposalExpired);

    // 0b. Replay protection
    assert!(!state.executed_intents.contains(intent_hash), EIntentAlreadyExecuted);
    state.executed_intents.add(intent_hash, true);

    // 1. Verify intent signature
    intent::verify_intent_signature(&intent_sig, &intent_hash, &buyer_pubkey);

    // 2. Transfer payment to merchant
    transfer::public_transfer(payment, merchant);

    // 3. Mint encrypted receipt
    let receipt_obj = receipt::mint_receipt(
        merchant,
        intent_hash,
        encrypted_payload,
        payload_nonce,
        ephemeral_pubkey,
        ctx,
    );
    let receipt_id = receipt::id(&receipt_obj);
    transfer::public_transfer(receipt_obj, buyer_stealth_addr);

    // 4. Mint warranty if applicable
    let warranty_id = if (!encrypted_warranty_terms.is_empty()) {
        let warranty_obj = warranty::mint_warranty(
            receipt_id,
            merchant,
            intent_hash,
            encrypted_warranty_terms,
            warranty_terms_nonce,
            ephemeral_pubkey,
            warranty_expiry,
            warranty_transferable,
            ctx,
        );
        let wid = option::some(object::id(&warranty_obj));
        transfer::public_transfer(warranty_obj, buyer_stealth_addr);
        wid
    } else {
        option::none()
    };

    // 5. Emit settlement event
    event::emit(SettlementEvent {
        intent_hash,
        merchant,
        amount,
        receipt_id,
        warranty_id,
        buyer_stealth_address: buyer_stealth_addr,
    });
}

// ============ Read Functions ============

/// Check if an intent hash has already been executed.
public fun is_intent_executed(state: &SettlementState, intent_hash: vector<u8>): bool {
    state.executed_intents.contains(intent_hash)
}
