import { ed25519 } from "@noble/curves/ed25519";
import { x25519 } from "@noble/curves/ed25519";
import { sha256 } from "@noble/hashes/sha256";
import { blake2b } from "@noble/hashes/blake2b";
import { bytesToHex, hexToBytes, concatBytes } from "@noble/hashes/utils";

const DOMAIN_SEPARATOR = new TextEncoder().encode("identipay-stealth-v1");

/**
 * Stealth address derivation per whitepaper section 4.5:
 * 1. Generate ephemeral keypair (r, R = r*G)
 * 2. ECDH: shared = x25519(r, K_view)
 * 3. Scalar: s = SHA-256(shared || domain_separator)
 * 4. Stealth pubkey: K_stealth = K_spend + s*G (Ed25519 point addition)
 * 5. Sui address: BLAKE2b-256(0x00 || K_stealth)
 * 6. View tag: first byte of shared secret
 */

export interface StealthOutput {
  ephemeralPubkey: Uint8Array; // R = r*G (32 bytes, X25519 public key)
  stealthAddress: string; // 0x-prefixed Sui address (66 chars)
  viewTag: number; // 0-255
  stealthPubkey: Uint8Array; // K_stealth (32 bytes, Ed25519 compressed)
}

/**
 * Compute ECDH shared secret between an X25519 private key and public key.
 */
export function ecdhSharedSecret(
  privateKey: Uint8Array,
  publicKey: Uint8Array,
): Uint8Array {
  return x25519.getSharedSecret(privateKey, publicKey);
}

/**
 * Derive the stealth scalar from a shared secret.
 * s = SHA-256(shared || "identipay-stealth-v1")
 */
export function deriveStealthScalar(sharedSecret: Uint8Array): Uint8Array {
  return sha256(concatBytes(sharedSecret, DOMAIN_SEPARATOR));
}

/**
 * Compute stealth public key: K_stealth = K_spend + s*G
 * Uses Ed25519 point arithmetic.
 */
export function computeStealthPubkey(
  spendPubkey: Uint8Array,
  scalar: Uint8Array,
): Uint8Array {
  const sG = ed25519.ExtendedPoint.BASE.multiply(
    bytesToBigInt(scalar),
  );
  const kSpend = ed25519.ExtendedPoint.fromHex(spendPubkey);
  const kStealth = kSpend.add(sG);
  return kStealth.toRawBytes();
}

/**
 * Derive Sui address from a public key: BLAKE2b-256(0x00 || pubkey)
 */
export function pubkeyToSuiAddress(pubkey: Uint8Array): string {
  const flagged = concatBytes(new Uint8Array([0x00]), pubkey);
  const hash = blake2b(flagged, { dkLen: 32 });
  return "0x" + bytesToHex(hash);
}

/**
 * Extract view tag: first byte of the shared secret.
 */
export function extractViewTag(sharedSecret: Uint8Array): number {
  return sharedSecret[0];
}

/**
 * Full stealth address derivation from sender side.
 * Sender knows: K_spend (Ed25519), K_view (X25519)
 * Sender generates ephemeral X25519 keypair.
 */
export function deriveStealthAddress(
  spendPubkey: Uint8Array,
  viewPubkey: Uint8Array,
  ephemeralPrivateKey?: Uint8Array,
): StealthOutput {
  // Generate or use provided ephemeral X25519 keypair
  const ephPriv = ephemeralPrivateKey ?? x25519.utils.randomPrivateKey();
  const ephPub = x25519.getPublicKey(ephPriv);

  // ECDH shared secret
  const shared = ecdhSharedSecret(ephPriv, viewPubkey);

  // View tag
  const viewTag = extractViewTag(shared);

  // Stealth scalar
  const scalar = deriveStealthScalar(shared);

  // Stealth pubkey = K_spend + s*G
  const stealthPubkey = computeStealthPubkey(spendPubkey, scalar);

  // Sui address
  const stealthAddress = pubkeyToSuiAddress(stealthPubkey);

  return {
    ephemeralPubkey: ephPub,
    stealthAddress,
    viewTag,
    stealthPubkey,
  };
}

/**
 * Receiver-side: check if an announcement is addressed to us.
 * Receiver knows: k_view (X25519 private), K_spend (Ed25519 public)
 */
export function scanAnnouncement(
  viewPrivateKey: Uint8Array,
  spendPubkey: Uint8Array,
  ephemeralPubkey: Uint8Array,
  announcedViewTag: number,
  announcedStealthAddress: string,
): boolean {
  const shared = ecdhSharedSecret(viewPrivateKey, ephemeralPubkey);
  const viewTag = extractViewTag(shared);

  // Fast filter: check view tag first (256x speedup)
  if (viewTag !== announcedViewTag) return false;

  // Full derivation to confirm
  const scalar = deriveStealthScalar(shared);
  const stealthPubkey = computeStealthPubkey(spendPubkey, scalar);
  const stealthAddress = pubkeyToSuiAddress(stealthPubkey);

  return stealthAddress === announcedStealthAddress;
}

/** Convert Uint8Array to BigInt (little-endian, mod curve order). */
function bytesToBigInt(bytes: Uint8Array): bigint {
  // Ed25519 scalar clamping/modular reduction
  let result = 0n;
  for (let i = bytes.length - 1; i >= 0; i--) {
    result = (result << 8n) | BigInt(bytes[i]);
  }
  // Reduce mod Ed25519 curve order
  const L = 2n ** 252n + 27742317777372353535851937790883648493n;
  return result % L;
}

export { bytesToHex, hexToBytes };
