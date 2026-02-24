import { assertEquals } from "@std/assert";
import { Hono } from "hono";
import { errorHandler } from "../../../src/middleware/error-handler.ts";
import { announcementRoutes } from "../../../src/routes/announcements.ts";

function createApp(results: unknown[] = []) {
  const mockDb = {
    select: () => ({
      from: () => ({
        where: () => ({
          orderBy: () => ({
            limit: () => Promise.resolve(results),
          }),
        }),
      }),
    }),
  };

  const app = new Hono();
  app.onError(errorHandler);
  // deno-lint-ignore no-explicit-any
  app.route("/announcements", announcementRoutes({ db: mockDb as any }));
  return app;
}

const SAMPLE_ANNOUNCEMENTS = [
  {
    id: "a1",
    ephemeralPubkey: "ab".repeat(32),
    viewTag: 42,
    stealthAddress: "0x" + "cd".repeat(32),
    metadata: null,
    txDigest: "0x" + "ef".repeat(32),
    timestamp: new Date("2025-01-01T00:00:00Z"),
  },
  {
    id: "a2",
    ephemeralPubkey: "12".repeat(32),
    viewTag: 100,
    stealthAddress: "0x" + "34".repeat(32),
    metadata: "encrypted-memo",
    txDigest: "0x" + "56".repeat(32),
    timestamp: new Date("2025-01-02T00:00:00Z"),
  },
];

Deno.test("GET /announcements returns announcements with no filters", async () => {
  const app = createApp(SAMPLE_ANNOUNCEMENTS);
  const res = await app.request("/announcements");

  assertEquals(res.status, 200);
  const body = await res.json();
  assertEquals(body.announcements.length, 2);
  assertEquals(body.announcements[0].viewTag, 42);
});

Deno.test("GET /announcements has NO linkage to names (privacy)", async () => {
  const app = createApp(SAMPLE_ANNOUNCEMENTS);
  const res = await app.request("/announcements");

  const body = await res.json();
  const json = JSON.stringify(body);

  // Privacy invariant: announcements never reference names
  assertEquals(json.includes("name"), false);
  assertEquals(json.includes("spendPubkey"), false);
  assertEquals(json.includes("viewPubkey"), false);
});

Deno.test("GET /announcements supports viewTag filter", async () => {
  const app = createApp(SAMPLE_ANNOUNCEMENTS);
  const res = await app.request("/announcements?viewTag=42");

  assertEquals(res.status, 200);
});

Deno.test("GET /announcements supports since filter", async () => {
  const app = createApp(SAMPLE_ANNOUNCEMENTS);
  const res = await app.request("/announcements?since=2025-01-01T00:00:00Z");

  assertEquals(res.status, 200);
});

Deno.test("GET /announcements supports limit", async () => {
  const app = createApp(SAMPLE_ANNOUNCEMENTS);
  const res = await app.request("/announcements?limit=10");

  assertEquals(res.status, 200);
});

Deno.test("GET /announcements rejects invalid viewTag", async () => {
  const app = createApp([]);
  const res = await app.request("/announcements?viewTag=300");

  assertEquals(res.status, 400);
});

Deno.test("GET /announcements returns nextCursor for pagination", async () => {
  const app = createApp(SAMPLE_ANNOUNCEMENTS);
  const res = await app.request("/announcements?limit=2");

  assertEquals(res.status, 200);
  const body = await res.json();
  // When results === limit, nextCursor is set
  assertEquals(body.nextCursor, "a2");
});
