import { assertEquals } from "@std/assert";
import { Hono } from "hono";
import { errorHandler } from "../../../src/middleware/error-handler.ts";
import { merchantRoutes } from "../../../src/routes/merchants.ts";
import { TEST_MERCHANT } from "../../fixtures/test-vectors.ts";

function createApp(existingMerchants: unknown[] = []) {
  const inserted: unknown[] = [];
  const suiService = {
    registerMerchantOnChain: () => Promise.resolve("mock-digest"),
  };

  const mockDb = {
    select: () => ({
      from: () => ({
        where: () => ({
          limit: () => Promise.resolve(existingMerchants),
        }),
      }),
    }),
    insert: () => ({
      values: (data: unknown) => {
        inserted.push(data);
        return Promise.resolve();
      },
    }),
  };

  const app = new Hono();
  app.onError(errorHandler);
  // deno-lint-ignore no-explicit-any
  app.route("/merchants", merchantRoutes({ db: mockDb as any, suiService: suiService as any }));
  return { app, inserted };
}

Deno.test("POST /merchants/register succeeds with valid input", async () => {
  const { app } = createApp();

  const res = await app.request("/merchants/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(TEST_MERCHANT),
  });

  assertEquals(res.status, 201);
  const body = await res.json();
  assertEquals(typeof body.id, "string");
  assertEquals(typeof body.did, "string");
  assertEquals(typeof body.apiKey, "string");
  assertEquals(body.did.startsWith("did:identipay:"), true);
  assertEquals(body.apiKey.length, 64);
});

Deno.test("POST /merchants/register DID format is did:identipay:<hostname>:<uuid>", async () => {
  const { app } = createApp();

  const res = await app.request("/merchants/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(TEST_MERCHANT),
  });

  const body = await res.json();
  const parts = body.did.split(":");
  assertEquals(parts[0], "did");
  assertEquals(parts[1], "identipay");
  assertEquals(parts[2], TEST_MERCHANT.hostname);
  assertEquals(parts[3].length > 0, true);
});

Deno.test("POST /merchants/register rejects invalid input", async () => {
  const { app } = createApp();

  const res = await app.request("/merchants/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name: "Test" }),
  });

  assertEquals(res.status, 400);
  const body = await res.json();
  assertEquals(body.error.code, "VALIDATION_ERROR");
});

Deno.test("POST /merchants/register rejects invalid suiAddress format", async () => {
  const { app } = createApp();

  const res = await app.request("/merchants/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      ...TEST_MERCHANT,
      suiAddress: "invalid-address",
    }),
  });

  assertEquals(res.status, 400);
});

Deno.test("POST /merchants/register rejects duplicate hostname", async () => {
  const { app } = createApp([{ hostname: TEST_MERCHANT.hostname }]);

  const res = await app.request("/merchants/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(TEST_MERCHANT),
  });

  assertEquals(res.status, 409);
  const body = await res.json();
  assertEquals(body.error.code, "CONFLICT");
});
