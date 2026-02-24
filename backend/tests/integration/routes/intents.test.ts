import { assertEquals } from "@std/assert";
import { Hono } from "hono";
import { errorHandler } from "../../../src/middleware/error-handler.ts";
import { intentRoutes } from "../../../src/routes/intents.ts";
import { SAMPLE_PROPOSAL } from "../../fixtures/test-vectors.ts";

function createApp(proposals: unknown[] = []) {
  const mockDb = {
    select: () => ({
      from: () => ({
        where: () => ({
          limit: () => Promise.resolve(proposals),
        }),
      }),
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
  app.route("/intents", intentRoutes({ db: mockDb as any }));
  return app;
}

Deno.test("GET /intents/:txId returns proposal for valid ID", async () => {
  const proposal = {
    transactionId: "550e8400-e29b-41d4-a716-446655440000",
    proposalJson: SAMPLE_PROPOSAL,
    status: "pending",
    expiresAt: new Date(Date.now() + 3600000), // 1 hour from now
  };

  const app = createApp([proposal]);
  const res = await app.request("/intents/550e8400-e29b-41d4-a716-446655440000");

  assertEquals(res.status, 200);
  const body = await res.json();
  assertEquals(body["@type"], "CommerceProposal");
});

Deno.test("GET /intents/:txId returns 404 for missing proposal", async () => {
  const app = createApp([]);
  const res = await app.request("/intents/nonexistent-id");

  assertEquals(res.status, 404);
  const body = await res.json();
  assertEquals(body.error.code, "NOT_FOUND");
});

Deno.test("GET /intents/:txId returns error for expired proposal", async () => {
  const proposal = {
    transactionId: "550e8400-e29b-41d4-a716-446655440000",
    proposalJson: SAMPLE_PROPOSAL,
    status: "pending",
    expiresAt: new Date(Date.now() - 1000), // Already expired
  };

  const app = createApp([proposal]);
  const res = await app.request("/intents/550e8400-e29b-41d4-a716-446655440000");

  assertEquals(res.status, 400);
  const body = await res.json();
  assertEquals(body.error.message, "Proposal has expired");
});

Deno.test("GET /intents/:txId returns error for cancelled proposal", async () => {
  const proposal = {
    transactionId: "550e8400-e29b-41d4-a716-446655440000",
    proposalJson: SAMPLE_PROPOSAL,
    status: "cancelled",
    expiresAt: new Date(Date.now() + 3600000),
  };

  const app = createApp([proposal]);
  const res = await app.request("/intents/550e8400-e29b-41d4-a716-446655440000");

  assertEquals(res.status, 400);
  const body = await res.json();
  assertEquals(body.error.message, "Proposal has been cancelled");
});
