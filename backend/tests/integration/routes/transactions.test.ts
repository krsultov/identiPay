import { assertEquals } from "@std/assert";
import { Hono } from "hono";
import { errorHandler } from "../../../src/middleware/error-handler.ts";
import { transactionRoutes } from "../../../src/routes/transactions.ts";
import { hashApiKey } from "../../../src/services/crypto.service.ts";

const API_KEY = "test-api-key-for-transactions";
const API_KEY_HASH = hashApiKey(API_KEY);

const MOCK_MERCHANT = {
  id: "merchant-1",
  name: "Test Shop",
  apiKeyHash: API_KEY_HASH,
  active: true,
};

function createApp(proposals: unknown[] = []) {
  // The mock needs to return the merchant for auth, then the proposal for the query
  let callCount = 0;
  const mockDb = {
    select: () => ({
      from: () => ({
        where: () => ({
          limit: () => {
            callCount++;
            if (callCount === 1) return Promise.resolve([MOCK_MERCHANT]); // Auth
            return Promise.resolve(proposals); // Query
          },
        }),
      }),
    }),
  };

  const app = new Hono();
  app.onError(errorHandler);
  // deno-lint-ignore no-explicit-any
  app.route("/transactions", transactionRoutes({ db: mockDb as any }));
  return app;
}

Deno.test("GET /transactions/:txId/status requires auth", async () => {
  const app = createApp();
  const res = await app.request("/transactions/tx-1/status");

  assertEquals(res.status, 401);
});

Deno.test("GET /transactions/:txId/status returns pending status", async () => {
  const proposal = {
    transactionId: "tx-1",
    merchantId: "merchant-1",
    status: "pending",
    suiTxDigest: null,
    createdAt: new Date(),
  };

  const app = createApp([proposal]);
  const res = await app.request("/transactions/tx-1/status", {
    headers: { Authorization: `Bearer ${API_KEY}` },
  });

  assertEquals(res.status, 200);
  const body = await res.json();
  assertEquals(body.transactionId, "tx-1");
  assertEquals(body.status, "pending");
  assertEquals(body.suiTxDigest, null);
});

Deno.test("GET /transactions/:txId/status returns settled status with digest", async () => {
  const proposal = {
    transactionId: "tx-2",
    merchantId: "merchant-1",
    status: "settled",
    suiTxDigest: "0x" + "ab".repeat(32),
    createdAt: new Date(),
  };

  const app = createApp([proposal]);
  const res = await app.request("/transactions/tx-2/status", {
    headers: { Authorization: `Bearer ${API_KEY}` },
  });

  assertEquals(res.status, 200);
  const body = await res.json();
  assertEquals(body.status, "settled");
  assertEquals(body.suiTxDigest, "0x" + "ab".repeat(32));
});

Deno.test("GET /transactions/:txId/status returns 404 for wrong merchant", async () => {
  const proposal = {
    transactionId: "tx-3",
    merchantId: "other-merchant",
    status: "pending",
  };

  const app = createApp([proposal]);
  const res = await app.request("/transactions/tx-3/status", {
    headers: { Authorization: `Bearer ${API_KEY}` },
  });

  assertEquals(res.status, 404);
});
