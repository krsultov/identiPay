import { assertEquals } from "@std/assert";
import { Hono } from "hono";
import { apiKeyAuth } from "../../../src/middleware/api-key.ts";
import { errorHandler } from "../../../src/middleware/error-handler.ts";
import { hashApiKey } from "../../../src/services/crypto.service.ts";

// deno-lint-ignore no-explicit-any
function createTestApp(mockDb: any) {
  const app = new Hono();
  app.onError(errorHandler);
  app.use("*", apiKeyAuth(mockDb));
  app.get("/protected", (c) => {
    const merchant = c.get("merchant" as never) as Record<string, unknown>;
    return c.json({ merchantId: merchant.id });
  });
  return app;
}

function createEmptyMockDb() {
  return {
    select: () => ({
      from: () => ({
        where: () => ({
          limit: () => Promise.resolve([]),
        }),
      }),
    }),
  };
}

Deno.test("api-key middleware rejects missing Authorization header", async () => {
  const app = createTestApp(createEmptyMockDb());
  const res = await app.request("/protected");
  assertEquals(res.status, 401);
  const body = await res.json();
  assertEquals(body.error.code, "UNAUTHORIZED");
});

Deno.test("api-key middleware rejects non-Bearer auth", async () => {
  const app = createTestApp(createEmptyMockDb());
  const res = await app.request("/protected", {
    headers: { Authorization: "Basic abc123" },
  });
  assertEquals(res.status, 401);
});

Deno.test("api-key middleware rejects invalid API key", async () => {
  const app = createTestApp(createEmptyMockDb());
  const res = await app.request("/protected", {
    headers: { Authorization: "Bearer invalid-key" },
  });
  assertEquals(res.status, 401);
});

Deno.test("api-key middleware rejects deactivated merchant", async () => {
  const apiKey = "valid-api-key-for-testing";
  const keyHash = hashApiKey(apiKey);

  const mockDb = {
    select: () => ({
      from: () => ({
        where: () => ({
          limit: () =>
            Promise.resolve([
              { id: "m1", name: "Deactivated", apiKeyHash: keyHash, active: false },
            ]),
        }),
      }),
    }),
  };

  const app = createTestApp(mockDb);
  const res = await app.request("/protected", {
    headers: { Authorization: `Bearer ${apiKey}` },
  });
  assertEquals(res.status, 401);
  const body = await res.json();
  assertEquals(body.error.message, "Merchant account is deactivated");
});

Deno.test("api-key middleware passes valid key and sets merchant context", async () => {
  const apiKey = "valid-api-key-123";
  const keyHash = hashApiKey(apiKey);

  const mockDb = {
    select: () => ({
      from: () => ({
        where: () => ({
          limit: () =>
            Promise.resolve([
              { id: "merchant-1", name: "Valid Merchant", apiKeyHash: keyHash, active: true },
            ]),
        }),
      }),
    }),
  };

  const app = createTestApp(mockDb);
  const res = await app.request("/protected", {
    headers: { Authorization: `Bearer ${apiKey}` },
  });
  assertEquals(res.status, 200);
  const body = await res.json();
  assertEquals(body.merchantId, "merchant-1");
});
