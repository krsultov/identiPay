/// Shielded Pool for identiPay -- privacy firewall for coin merging.
/// Prevents address clustering when spending from multiple stealth addresses.
/// Users deposit USDC with a Poseidon note commitment, then withdraw to a
/// fresh stealth address via a ZK proof of note ownership.
/// Per whitepaper section 4.8.
///
/// Generic over token type T so it works with any coin (USDC, SUI, etc.).
module identipay::shielded_pool;

use sui::balance::{Self, Balance};
use sui::coin::{Self, Coin};
use sui::event;
use sui::hash;
use sui::table::{Self, Table};
use identipay::zk_verifier::{Self, VerificationKey};

// ============ Structs ============

/// The shielded pool. Holds token balances and tracks an incremental
/// Merkle tree of note commitments and spent nullifiers.
///
/// The Merkle tree uses a "filled subtrees" approach: we store one hash
/// per level representing the most recently completed subtree at that
/// depth. This allows O(depth) insertion without storing every node.
public struct ShieldedPool<phantom T> has key {
    id: UID,
    /// Pool balance
    balance: Balance<T>,
    /// Current Merkle root of the note commitment tree
    merkle_root: vector<u8>,
    /// Spent nullifiers (prevents double-spend)
    nullifiers: Table<vector<u8>, bool>,
    /// Next leaf index in the Merkle tree
    next_leaf_index: u64,
    /// Filled subtrees: one hash per level (index 0 = leaf level).
    /// When a subtree at level i is completed, its root is stored here
    /// and used as the left child when computing the next level up.
    filled_subtrees: vector<vector<u8>>,
    /// Merkle tree depth (determines max capacity: 2^depth notes)
    tree_depth: u8,
}

/// Emitted when a deposit is made.
public struct DepositEvent has copy, drop {
    note_commitment: vector<u8>,
    leaf_index: u64,
    new_merkle_root: vector<u8>,
}

/// Emitted when a withdrawal is made.
public struct WithdrawEvent has copy, drop {
    nullifier: vector<u8>,
    recipient: address,
    amount: u64,
    new_merkle_root: vector<u8>,
}

// ============ Constants ============

const ENullifierAlreadySpent: u64 = 0;
const EProofVerificationFailed: u64 = 1;
const EInsufficientPoolBalance: u64 = 2;
const EEmptyCommitment: u64 = 3;
const EPoolFull: u64 = 4;
const EInvalidAmount: u64 = 5;
const EEmptyNullifier: u64 = 6;

const DEFAULT_TREE_DEPTH: u8 = 20; // 2^20 = ~1M notes

// ============ Init ============

/// Create a new shielded pool for a given token type.
/// The pool starts with an empty Merkle tree.
entry fun create_pool<T>(ctx: &mut TxContext) {
    // Initialize filled_subtrees with zero hashes at each level.
    // zero_hash(0) = ZERO_LEAF, zero_hash(i) = hash(zero_hash(i-1) || zero_hash(i-1))
    let mut filled = vector[];
    let mut current_zero = zero_leaf();
    let mut i = 0;
    while (i < (DEFAULT_TREE_DEPTH as u64)) {
        filled.push_back(current_zero);
        current_zero = hash_pair(current_zero, current_zero);
        i = i + 1;
    };

    // The initial root is the zero hash at the top level
    let pool = ShieldedPool<T> {
        id: object::new(ctx),
        balance: balance::zero<T>(),
        merkle_root: current_zero,
        nullifiers: table::new(ctx),
        next_leaf_index: 0,
        filled_subtrees: filled,
        tree_depth: DEFAULT_TREE_DEPTH,
    };

    transfer::share_object(pool);
}

#[test_only]
public fun create_pool_for_testing<T>(ctx: &mut TxContext) {
    create_pool<T>(ctx);
}

// ============ Core Functions ============

/// Deposit tokens into the shielded pool with a note commitment.
/// The note_commitment = Poseidon(amount, owner_pubkey, salt) is computed
/// off-chain by the wallet. The contract stores it in the Merkle tree.
entry fun deposit<T>(
    pool: &mut ShieldedPool<T>,
    coin: Coin<T>,
    note_commitment: vector<u8>,
    _ctx: &mut TxContext,
) {
    assert!(!note_commitment.is_empty(), EEmptyCommitment);
    let capacity = 1u64 << pool.tree_depth;
    assert!(pool.next_leaf_index < capacity, EPoolFull);
    assert!(coin.value() > 0, EInvalidAmount);

    // Add coin to pool balance
    let coin_balance = coin::into_balance(coin);
    balance::join(&mut pool.balance, coin_balance);

    // Insert leaf into the incremental Merkle tree and recompute root
    let leaf_index = pool.next_leaf_index;
    let new_root = insert_leaf(pool, note_commitment);
    pool.merkle_root = new_root;
    pool.next_leaf_index = leaf_index + 1;

    event::emit(DepositEvent {
        note_commitment,
        leaf_index,
        new_merkle_root: pool.merkle_root,
    });
}

/// Withdraw tokens from the shielded pool.
/// Requires a Groth16 proof attesting to note ownership without revealing
/// which note is being spent. The nullifier prevents double-spending.
///
/// The proof verifies:
/// 1. A note with commitment = Poseidon(amount, owner_key, salt) exists in the Merkle tree
/// 2. The nullifier = Poseidon(commitment, owner_key) is correctly derived
/// 3. withdraw_amount <= note_amount
/// 4. If change > 0, a valid change commitment is provided
entry fun withdraw<T>(
    pool: &mut ShieldedPool<T>,
    vk: &VerificationKey,
    proof: vector<u8>,
    public_inputs: vector<u8>,
    nullifier: vector<u8>,
    recipient: address,
    amount: u64,
    change_commitment: vector<u8>,
    ctx: &mut TxContext,
) {
    assert!(amount > 0, EInvalidAmount);
    assert!(!nullifier.is_empty(), EEmptyNullifier);
    assert!(pool.balance.value() >= amount, EInsufficientPoolBalance);

    // Check nullifier hasn't been spent
    assert!(!pool.nullifiers.contains(nullifier), ENullifierAlreadySpent);

    // Verify ZK proof
    let proof_valid = zk_verifier::verify_proof(vk, &proof, &public_inputs);
    assert!(proof_valid, EProofVerificationFailed);

    // Mark nullifier as spent
    pool.nullifiers.add(nullifier, true);

    // If there's change, insert the change commitment into the Merkle tree
    if (!change_commitment.is_empty()) {
        let new_root = insert_leaf(pool, change_commitment);
        pool.merkle_root = new_root;
        pool.next_leaf_index = pool.next_leaf_index + 1;
    };

    // Transfer tokens to recipient
    let withdraw_balance = balance::split(&mut pool.balance, amount);
    let withdraw_coin = coin::from_balance(withdraw_balance, ctx);
    transfer::public_transfer(withdraw_coin, recipient);

    event::emit(WithdrawEvent {
        nullifier,
        recipient,
        amount,
        new_merkle_root: pool.merkle_root,
    });
}

// ============ Read Functions ============

public fun pool_balance<T>(pool: &ShieldedPool<T>): u64 { pool.balance.value() }
public fun merkle_root<T>(pool: &ShieldedPool<T>): vector<u8> { pool.merkle_root }
public fun next_leaf_index<T>(pool: &ShieldedPool<T>): u64 { pool.next_leaf_index }
public fun is_nullifier_spent<T>(pool: &ShieldedPool<T>, nullifier: vector<u8>): bool {
    pool.nullifiers.contains(nullifier)
}

// ============ Internal: Incremental Merkle Tree ============

/// The zero leaf value (32 zero bytes). Represents an empty leaf slot.
fun zero_leaf(): vector<u8> {
    vector[
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    ]
}

/// Hash two 32-byte nodes together: BLAKE2b-256(left || right).
fun hash_pair(left: vector<u8>, right: vector<u8>): vector<u8> {
    let mut preimage = left;
    let mut i = 0;
    while (i < right.length()) {
        preimage.push_back(*right.borrow(i));
        i = i + 1;
    };
    hash::blake2b256(&preimage)
}

/// Insert a leaf into the incremental Merkle tree and return the new root.
///
/// Algorithm: Starting from the leaf level, walk up the tree. At each level,
/// if the current index is even (left child), the sibling is the zero hash
/// at that level; if odd (right child), the sibling is the stored filled
/// subtree at that level. When the current index is even, we update the
/// filled_subtrees entry for that level because we just completed a left subtree.
fun insert_leaf<T>(pool: &mut ShieldedPool<T>, leaf: vector<u8>): vector<u8> {
    let mut current_index = pool.next_leaf_index;
    let mut current_hash = leaf;
    let depth = pool.tree_depth as u64;

    // Precompute zero hashes for each level
    let mut zero_hashes = vector[];
    let mut zh = zero_leaf();
    let mut i = 0;
    while (i < depth) {
        zero_hashes.push_back(zh);
        zh = hash_pair(zh, zh);
        i = i + 1;
    };

    i = 0;
    while (i < depth) {
        if (current_index % 2 == 0) {
            // Left child: sibling is the zero hash at this level.
            // Update filled_subtrees since this left subtree is now the latest.
            *pool.filled_subtrees.borrow_mut(i) = current_hash;
            current_hash = hash_pair(current_hash, *zero_hashes.borrow(i));
        } else {
            // Right child: sibling is the stored filled subtree (left neighbor).
            let left = *pool.filled_subtrees.borrow(i);
            current_hash = hash_pair(left, current_hash);
        };
        current_index = current_index / 2;
        i = i + 1;
    };

    current_hash
}
