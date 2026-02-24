import { assertEquals } from "@std/assert";
import {
  buildProposalUri,
  buildPayRequestUri,
  generateQrDataUrl,
} from "../../../src/services/qr.service.ts";

Deno.test("buildProposalUri format: did:identipay:<host>:<txId>", () => {
  const uri = buildProposalUri("shop.example.com", "550e8400-e29b-41d4-a716-446655440000");
  assertEquals(uri, "did:identipay:shop.example.com:550e8400-e29b-41d4-a716-446655440000");
});

Deno.test("buildPayRequestUri format without memo", () => {
  const uri = buildPayRequestUri("alice", "1000000000", "SUI");
  assertEquals(uri, "identipay://pay/@alice.idpay?amount=1000000000&currency=SUI");
});

Deno.test("buildPayRequestUri format with memo", () => {
  const uri = buildPayRequestUri("alice", "1000000000", "SUI", "Coffee payment");
  assertEquals(
    uri,
    "identipay://pay/@alice.idpay?amount=1000000000&currency=SUI&memo=Coffee%20payment",
  );
});

Deno.test("buildPayRequestUri encodes special characters in memo", () => {
  const uri = buildPayRequestUri("bob", "500", "USDC", "Order #123 & more");
  assertEquals(uri.includes("Order%20%23123%20%26%20more"), true);
});

Deno.test("generateQrDataUrl returns data URL", async () => {
  const dataUrl = await generateQrDataUrl("did:identipay:test:123");
  assertEquals(dataUrl.startsWith("data:image/png;base64,"), true);
  assertEquals(dataUrl.length > 100, true);
});
