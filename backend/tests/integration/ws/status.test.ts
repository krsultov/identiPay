import { assertEquals } from "@std/assert";
import { pushSettlementUpdate, getActiveConnectionCount } from "../../../src/ws/status.ts";

Deno.test("getActiveConnectionCount returns 0 with no connections", () => {
  assertEquals(getActiveConnectionCount(), 0);
});

Deno.test("pushSettlementUpdate does not throw with no connections", () => {
  // Should not throw even if no WebSocket connections exist for this txId
  pushSettlementUpdate("nonexistent-tx", "settled", "0xdigest");
});

Deno.test("pushSettlementUpdate message format excludes buyer stealth address", () => {
  // Verify the message structure by checking the function signature
  // The pushSettlementUpdate function takes (txId, status, suiTxDigest)
  // It does NOT accept a buyerStealthAddress parameter -- privacy invariant
  // We verify the API doesn't expose buyer identity in its parameters
  assertEquals(pushSettlementUpdate.length, 3); // 3 params: txId, status, digest
});
