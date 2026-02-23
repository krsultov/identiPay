/// Stealth payment announcement events for identiPay.
/// When a sender pays to a stealth address, they emit an announcement
/// containing the ephemeral public key R and a view tag. The recipient's
/// wallet scans these announcements to detect incoming payments.
/// Per whitepaper section 4.4.
module identipay::announcements;

use sui::event;

// ============ Structs ============

/// Emitted when a payment is sent to a stealth address.
/// Recipients scan these events to detect incoming payments.
public struct StealthAnnouncement has copy, drop {
    /// Ephemeral public key R = r*G (32 bytes).
    /// The recipient uses this with their viewing private key to derive
    /// the shared secret and detect if this payment is for them.
    ephemeral_pubkey: vector<u8>,
    /// First byte of the ECDH shared secret (fast 1-byte filter).
    /// Reduces full ECDH computations by ~256x during scanning.
    view_tag: u8,
    /// The derived one-time stealth address where the payment was sent.
    stealth_address: address,
    /// Optional encrypted memo (e.g., payment reason).
    metadata: vector<u8>,
}

// ============ Constants ============

const EInvalidEphemeralPubkey: u64 = 0;

const PUBKEY_LENGTH: u64 = 32;

// ============ Public Functions ============

/// Emit a stealth payment announcement. Called by anyone sending to
/// a stealth address (P2P payments, commerce settlements).
entry fun announce(
    ephemeral_pubkey: vector<u8>,
    view_tag: u8,
    stealth_address: address,
    metadata: vector<u8>,
    _ctx: &mut TxContext,
) {
    assert!(ephemeral_pubkey.length() == PUBKEY_LENGTH, EInvalidEphemeralPubkey);

    event::emit(StealthAnnouncement {
        ephemeral_pubkey,
        view_tag,
        stealth_address,
        metadata,
    });
}
