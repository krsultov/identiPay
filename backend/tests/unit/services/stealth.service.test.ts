import { assertEquals } from "@std/assert";
import {
  deriveStealthAddress,
  scanAnnouncement,
  ecdhSharedSecret,
  deriveStealthScalar,
  pubkeyToSuiAddress,
  extractViewTag,
  hexToBytes,
  bytesToHex,
} from "../../../src/services/stealth.service.ts";
import {
  TEST_SPEND_PUBKEY,
  TEST_VIEW_PRIVKEY,
  TEST_VIEW_PUBKEY,
  TEST_EPHEMERAL_PRIVKEY,
} from "../../fixtures/test-vectors.ts";

Deno.test("deriveStealthAddress produces valid output", () => {
  const spendPubkey = hexToBytes(TEST_SPEND_PUBKEY);
  const viewPubkey = hexToBytes(TEST_VIEW_PUBKEY);
  const ephPriv = hexToBytes(TEST_EPHEMERAL_PRIVKEY);

  const result = deriveStealthAddress(spendPubkey, viewPubkey, ephPriv);

  // Ephemeral pubkey is 32 bytes
  assertEquals(result.ephemeralPubkey.length, 32);
  // Stealth address is 66 chars (0x + 64 hex)
  assertEquals(result.stealthAddress.length, 66);
  assertEquals(result.stealthAddress.startsWith("0x"), true);
  // View tag is 0-255
  assertEquals(result.viewTag >= 0 && result.viewTag <= 255, true);
  // Stealth pubkey is 32 bytes
  assertEquals(result.stealthPubkey.length, 32);
});

Deno.test("deriveStealthAddress is deterministic with same ephemeral key", () => {
  const spendPubkey = hexToBytes(TEST_SPEND_PUBKEY);
  const viewPubkey = hexToBytes(TEST_VIEW_PUBKEY);
  const ephPriv = hexToBytes(TEST_EPHEMERAL_PRIVKEY);

  const a = deriveStealthAddress(spendPubkey, viewPubkey, ephPriv);
  const b = deriveStealthAddress(spendPubkey, viewPubkey, ephPriv);

  assertEquals(a.stealthAddress, b.stealthAddress);
  assertEquals(a.viewTag, b.viewTag);
  assertEquals(bytesToHex(a.ephemeralPubkey), bytesToHex(b.ephemeralPubkey));
});

Deno.test("scanAnnouncement detects own announcements", () => {
  const spendPubkey = hexToBytes(TEST_SPEND_PUBKEY);
  const viewPubkey = hexToBytes(TEST_VIEW_PUBKEY);
  const viewPrivkey = hexToBytes(TEST_VIEW_PRIVKEY);
  const ephPriv = hexToBytes(TEST_EPHEMERAL_PRIVKEY);

  // Sender derives stealth address
  const output = deriveStealthAddress(spendPubkey, viewPubkey, ephPriv);

  // Receiver scans announcement
  const isOurs = scanAnnouncement(
    viewPrivkey,
    spendPubkey,
    output.ephemeralPubkey,
    output.viewTag,
    output.stealthAddress,
  );

  assertEquals(isOurs, true);
});

Deno.test("scanAnnouncement rejects wrong view tag", () => {
  const spendPubkey = hexToBytes(TEST_SPEND_PUBKEY);
  const viewPubkey = hexToBytes(TEST_VIEW_PUBKEY);
  const viewPrivkey = hexToBytes(TEST_VIEW_PRIVKEY);
  const ephPriv = hexToBytes(TEST_EPHEMERAL_PRIVKEY);

  const output = deriveStealthAddress(spendPubkey, viewPubkey, ephPriv);

  // Wrong view tag
  const wrongTag = (output.viewTag + 1) % 256;
  const isOurs = scanAnnouncement(
    viewPrivkey,
    spendPubkey,
    output.ephemeralPubkey,
    wrongTag,
    output.stealthAddress,
  );

  assertEquals(isOurs, false);
});

Deno.test("extractViewTag returns first byte of shared secret", () => {
  const shared = new Uint8Array([0x42, 0xff, 0x00, 0x01]);
  assertEquals(extractViewTag(shared), 0x42);
});

Deno.test("pubkeyToSuiAddress produces 66-char 0x-prefixed hex", () => {
  const pubkey = new Uint8Array(32).fill(0xab);
  const addr = pubkeyToSuiAddress(pubkey);
  assertEquals(addr.length, 66);
  assertEquals(addr.startsWith("0x"), true);
  assertEquals(/^0x[0-9a-f]{64}$/.test(addr), true);
});

Deno.test("ECDH shared secret is consistent sender/receiver", async () => {
  const ephPriv = hexToBytes(TEST_EPHEMERAL_PRIVKEY);
  const viewPub = hexToBytes(TEST_VIEW_PUBKEY);
  const viewPriv = hexToBytes(TEST_VIEW_PRIVKEY);

  const { x25519 } = await import("@noble/curves/ed25519");
  const ephPub = x25519.getPublicKey(ephPriv);

  // Sender: ECDH(ephPriv, viewPub)
  const senderShared = ecdhSharedSecret(ephPriv, viewPub);
  // Receiver: ECDH(viewPriv, ephPub)
  const receiverShared = ecdhSharedSecret(viewPriv, ephPub);

  assertEquals(bytesToHex(senderShared), bytesToHex(receiverShared));
});

Deno.test("deriveStealthScalar produces 32 bytes", () => {
  const shared = new Uint8Array(32).fill(0x01);
  const scalar = deriveStealthScalar(shared);
  assertEquals(scalar.length, 32);
});
