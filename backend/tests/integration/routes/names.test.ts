import { assertEquals } from "@std/assert";
import { Hono } from "hono";
import { errorHandler } from "../../../src/middleware/error-handler.ts";
import { nameRoutes } from "../../../src/routes/names.ts";
import { createMockSuiService } from "../../helpers/setup.ts";

function createApp(cachedNames: unknown[] = [], onChainResult: unknown = null) {
  const suiService = {
    ...createMockSuiService(),
    resolveName: (_name: string) => Promise.resolve(onChainResult),
  };

  const insertedValues: unknown[] = [];
  const mockDb = {
    select: () => ({
      from: () => ({
        where: () => ({
          limit: () => Promise.resolve(cachedNames),
        }),
      }),
    }),
    insert: () => ({
      values: (data: unknown) => {
        insertedValues.push(data);
        return {
          onConflictDoUpdate: () => Promise.resolve(),
        };
      },
    }),
  };

  const app = new Hono();
  app.onError(errorHandler);
  // deno-lint-ignore no-explicit-any
  app.route("/names", nameRoutes({ db: mockDb as any, suiService: suiService as any }));
  return { app, insertedValues };
}

Deno.test("GET /names/:name returns cached name resolution", async () => {
  const cached = [{
    name: "alice",
    spendPubkey: "ab".repeat(32),
    viewPubkey: "cd".repeat(32),
  }];

  const { app } = createApp(cached);
  const res = await app.request("/names/alice");

  assertEquals(res.status, 200);
  const body = await res.json();
  assertEquals(body.name, "alice");
  assertEquals(body.spendPubkey, "ab".repeat(32));
  assertEquals(body.viewPubkey, "cd".repeat(32));
});

Deno.test("GET /names/:name returns ONLY public keys, NO Sui address (privacy)", async () => {
  const cached = [{
    name: "bob",
    spendPubkey: "ab".repeat(32),
    viewPubkey: "cd".repeat(32),
  }];

  const { app } = createApp(cached);
  const res = await app.request("/names/bob");

  assertEquals(res.status, 200);
  const body = await res.json();

  // Privacy invariant: NEVER return a Sui address
  assertEquals("suiAddress" in body, false);
  assertEquals("address" in body, false);
  assertEquals("stealthAddress" in body, false);

  // ONLY these fields
  const keys = Object.keys(body);
  assertEquals(keys.sort(), ["name", "spendPubkey", "viewPubkey"]);
});

Deno.test("GET /names/:name falls back to on-chain when not cached", async () => {
  const onChain = {
    spendPubkey: "ef".repeat(32),
    viewPubkey: "01".repeat(32),
  };

  const { app, insertedValues } = createApp([], onChain);
  const res = await app.request("/names/charlie");

  assertEquals(res.status, 200);
  const body = await res.json();
  assertEquals(body.name, "charlie");
  assertEquals(body.spendPubkey, "ef".repeat(32));

  // Verify it was cached
  assertEquals(insertedValues.length, 1);
});

Deno.test("GET /names/:name returns 404 for non-existent name", async () => {
  const { app } = createApp([], null);
  const res = await app.request("/names/nonexistent");

  assertEquals(res.status, 404);
  const body = await res.json();
  assertEquals(body.error.code, "NOT_FOUND");
});
