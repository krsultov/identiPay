import { assertEquals } from "@std/assert";
import { Hono } from "hono";
import { errorHandler } from "../../../src/middleware/error-handler.ts";
import { payRequestRoutes } from "../../../src/routes/pay-requests.ts";

function createApp(options: {
  payRequests?: unknown[];
  cachedNames?: unknown[];
  onChainResolve?: unknown;
} = {}) {
  const { payRequests = [], cachedNames = [], onChainResolve = null } = options;

  const suiService = {
    resolveName: () => Promise.resolve(onChainResolve),
  };

  const inserted: unknown[] = [];
  // Track which "table" is being queried based on call order
  let selectCallCount = 0;

  const mockDb = {
    select: () => ({
      from: () => {
        selectCallCount++;
        const currentCall = selectCallCount;
        return {
          where: () => ({
            limit: () => {
              // For pay-request GET, first select is pay_requests, second is names
              if (currentCall === 1) return Promise.resolve(payRequests);
              return Promise.resolve(cachedNames);
            },
          }),
        };
      },
    }),
    insert: () => ({
      values: (data: unknown) => {
        inserted.push(data);
        return Promise.resolve();
      },
    }),
    update: () => ({
      set: () => ({
        where: () => Promise.resolve(),
      }),
    }),
  };

  const app = new Hono();
  app.onError(errorHandler);
  // deno-lint-ignore no-explicit-any
  app.route("/pay-requests", payRequestRoutes({ db: mockDb as any, suiService: suiService as any }));
  return { app, inserted };
}

Deno.test("POST /pay-requests creates payment request", async () => {
  const { app } = createApp();
  const res = await app.request("/pay-requests", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      recipientName: "alice",
      amount: "1000000000",
      currency: "SUI",
      memo: "Coffee",
      expiresInSeconds: 3600,
    }),
  });

  assertEquals(res.status, 201);
  const body = await res.json();
  assertEquals(typeof body.requestId, "string");
  assertEquals(body.recipientName, "alice");
  assertEquals(body.amount, "1000000000");
  assertEquals(body.currency, "SUI");
  assertEquals(body.memo, "Coffee");
  assertEquals(body.status, "pending");
  assertEquals(body.uri.startsWith("identipay://pay/@alice.idpay"), true);
  assertEquals(body.qrDataUrl.startsWith("data:image/png;base64,"), true);
});

Deno.test("POST /pay-requests rejects invalid input", async () => {
  const { app } = createApp();
  const res = await app.request("/pay-requests", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ recipientName: "a" }),
  });

  assertEquals(res.status, 400);
});

Deno.test("GET /pay-requests/:id resolves with recipient meta-address", async () => {
  const request = {
    requestId: "req-1",
    recipientName: "alice",
    amount: "1000000000",
    currency: "SUI",
    memo: "Test",
    expiresAt: new Date(Date.now() + 3600000),
    status: "pending",
  };

  const cachedName = {
    name: "alice",
    spendPubkey: "ab".repeat(32),
    viewPubkey: "cd".repeat(32),
  };

  const { app } = createApp({
    payRequests: [request],
    cachedNames: [cachedName],
  });
  const res = await app.request("/pay-requests/req-1");

  assertEquals(res.status, 200);
  const body = await res.json();
  assertEquals(body.requestId, "req-1");
  assertEquals(body.recipient?.spendPubkey, "ab".repeat(32));
  assertEquals(body.recipient?.viewPubkey, "cd".repeat(32));

  // Privacy: no Sui address in response
  assertEquals("suiAddress" in (body.recipient ?? {}), false);
});

Deno.test("GET /pay-requests/:id returns 404 for non-existent", async () => {
  const { app } = createApp();
  const res = await app.request("/pay-requests/nonexistent");

  assertEquals(res.status, 404);
});
