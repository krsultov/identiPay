import { assertEquals, assertNotEquals } from "@std/assert";
import {
  sha256Hash,
  sha256Hex,
  hashApiKey,
  generateApiKey,
  randomBytes,
  bytesToHex,
  hexToBytes,
} from "../../../src/services/crypto.service.ts";

Deno.test("sha256Hash returns 32 bytes", () => {
  const result = sha256Hash(new Uint8Array([1, 2, 3]));
  assertEquals(result.length, 32);
});

Deno.test("sha256Hex returns 64-char hex string", () => {
  const result = sha256Hex(new Uint8Array([1, 2, 3]));
  assertEquals(result.length, 64);
  assertEquals(/^[0-9a-f]{64}$/.test(result), true);
});

Deno.test("sha256Hex is deterministic", () => {
  const input = new TextEncoder().encode("hello identipay");
  const a = sha256Hex(input);
  const b = sha256Hex(input);
  assertEquals(a, b);
});

Deno.test("hashApiKey returns 64-char hex", () => {
  const hash = hashApiKey("test-api-key");
  assertEquals(hash.length, 64);
  assertEquals(/^[0-9a-f]{64}$/.test(hash), true);
});

Deno.test("hashApiKey is deterministic", () => {
  const a = hashApiKey("same-key");
  const b = hashApiKey("same-key");
  assertEquals(a, b);
});

Deno.test("hashApiKey produces different hashes for different keys", () => {
  const a = hashApiKey("key-1");
  const b = hashApiKey("key-2");
  assertNotEquals(a, b);
});

Deno.test("generateApiKey returns 64-char hex string", () => {
  const key = generateApiKey();
  assertEquals(key.length, 64);
  assertEquals(/^[0-9a-f]{64}$/.test(key), true);
});

Deno.test("generateApiKey produces unique keys", () => {
  const a = generateApiKey();
  const b = generateApiKey();
  assertNotEquals(a, b);
});

Deno.test("randomBytes returns correct length", () => {
  assertEquals(randomBytes(16).length, 16);
  assertEquals(randomBytes(32).length, 32);
  assertEquals(randomBytes(64).length, 64);
});

Deno.test("bytesToHex and hexToBytes roundtrip", () => {
  const original = new Uint8Array([0xde, 0xad, 0xbe, 0xef]);
  const hex = bytesToHex(original);
  assertEquals(hex, "deadbeef");
  const roundtripped = hexToBytes(hex);
  assertEquals(roundtripped, original);
});
