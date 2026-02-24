import { assertEquals } from "@std/assert";
import { Hono } from "hono";
import { errorHandler } from "../../../src/middleware/error-handler.ts";
import { proposalRoutes } from "../../../src/routes/proposals.ts";
import { hashApiKey } from "../../../src/services/crypto.service.ts";
import { VALID_PROPOSAL_INPUT } from "../../fixtures/proposals.ts";

const API_KEY = "test-api-key-for-proposals";
const API_KEY_HASH = hashApiKey(API_KEY);

const MOCK_MERCHANT = {
  id: "merchant-1",
  name: "Test Shop",
  did: "did:identipay:shop.example.com:merchant-1",
  suiAddress: "0x" + "ab".repeat(32),
  publicKey: "cd".repeat(32),
  hostname: "shop.example.com",
  apiKeyHash: API_KEY_HASH,
  active: true,
};

function createApp() {
  const store: Record<string, unknown[]> = {
    merchants: [MOCK_MERCHANT],
  };

  const mockDb = {
    select: () => ({
      from: () => ({
        where: () => ({
          limit: (_n: number) => Promise.resolve(store.merchants ?? []),
        }),
      }),
    }),
    // deno-lint-ignore no-explicit-any
    insert: (_table: any) => ({
      values: (_data: unknown) => Promise.resolve(),
    }),
  };

  const app = new Hono();
  app.onError(errorHandler);
  app.route(
    "/proposals",
    // deno-lint-ignore no-explicit-any
    proposalRoutes({ db: mockDb as any, packageId: "0x1" }),
  );
  return app;
}

Deno.test("POST /proposals requires auth", async () => {
  const app = createApp();
  const res = await app.request("/proposals", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(VALID_PROPOSAL_INPUT),
  });
  assertEquals(res.status, 401);
});

Deno.test("POST /proposals creates proposal with valid input", async () => {
  const app = createApp();
  const res = await app.request("/proposals", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${API_KEY}`,
    },
    body: JSON.stringify(VALID_PROPOSAL_INPUT),
  });

  assertEquals(res.status, 201);
  const body = await res.json();
  assertEquals(typeof body.transactionId, "string");
  assertEquals(typeof body.intentHash, "string");
  assertEquals(body.intentHash.length, 64);
  assertEquals(typeof body.qrDataUrl, "string");
  assertEquals(body.qrDataUrl.startsWith("data:image/png;base64,"), true);
  assertEquals(typeof body.uri, "string");
  assertEquals(body.uri.startsWith("did:identipay:"), true);
  assertEquals(typeof body.expiresAt, "string");
});

Deno.test("POST /proposals proposal contains no buyer fields", async () => {
  const app = createApp();
  const res = await app.request("/proposals", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${API_KEY}`,
    },
    body: JSON.stringify(VALID_PROPOSAL_INPUT),
  });

  const body = await res.json();
  const proposalJson = JSON.stringify(body.proposal);

  // Privacy invariant: no buyer identity in proposal
  assertEquals(proposalJson.includes("buyer"), false);
  assertEquals(proposalJson.includes("stealthAddress"), false);
  assertEquals(proposalJson.includes("spendPubkey"), false);
  assertEquals(proposalJson.includes("viewPubkey"), false);
});

Deno.test("POST /proposals rejects invalid input", async () => {
  const app = createApp();
  const res = await app.request("/proposals", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${API_KEY}`,
    },
    body: JSON.stringify({ items: [] }), // Empty items
  });
  assertEquals(res.status, 400);
});
