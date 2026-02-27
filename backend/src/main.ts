import { Hono } from "hono";
import { cors } from "hono/cors";
import { config } from "./config.ts";
import { createDb } from "./db/connection.ts";
import { errorHandler } from "./middleware/error-handler.ts";
import { SuiService } from "./services/sui.service.ts";
import { merchantRoutes } from "./routes/merchants.ts";
import { proposalRoutes } from "./routes/proposals.ts";
import { intentRoutes } from "./routes/intents.ts";
import { transactionRoutes } from "./routes/transactions.ts";
import { nameRoutes } from "./routes/names.ts";
import { announcementRoutes } from "./routes/announcements.ts";
import { payRequestRoutes } from "./routes/pay-requests.ts";
import { handleWsConnection, pushSettlementUpdate } from "./ws/status.ts";
import { proposals, announcements, eventCursors } from "./db/schema.ts";
import { eq } from "drizzle-orm";
import { lt, and } from "drizzle-orm";

// Initialize database
const { db, client: _pgClient } = createDb(config.databaseUrl);

// Initialize Sui service
const suiService = new SuiService({
  rpcUrl: config.suiRpcUrl,
  packageId: config.packageId,
  trustRegistryId: config.trustRegistryId,
  metaRegistryId: config.metaRegistryId,
  settlementStateId: config.settlementStateId,
  adminSecretKey: config.adminSecretKey,
  verificationKeyId: config.verificationKeyId,
});

// Create Hono app
const app = new Hono();

// Global middleware
app.use("*", cors());
app.onError(errorHandler);

// Health check
app.get("/health", (c) => c.json({ status: "ok" }));

// Mount API routes
const api = new Hono();
api.route("/merchants", merchantRoutes({ db, suiService }));
api.route("/proposals", proposalRoutes({ db, packageId: config.packageId }));
api.route("/intents", intentRoutes({ db }));
api.route("/transactions", transactionRoutes({ db, suiService }));
api.route("/names", nameRoutes({ db, suiService }));
api.route("/announcements", announcementRoutes({ db }));
api.route("/pay-requests", payRequestRoutes({ db, suiService }));

app.route("/api/identipay/v1", api);

// WebSocket for transaction status
app.get("/ws/transactions/:txId", (c) => {
  const txId = c.req.param("txId");
  const { response, socket } = Deno.upgradeWebSocket(c.req.raw);

  socket.onopen = () => {
    const wsWrapper = {
      send: (data: string) => socket.send(data),
      close: () => socket.close(),
    };
    const cleanup = handleWsConnection(txId, wsWrapper, db);
    socket.onclose = () => cleanup();
  };

  return response;
});

// --- Background tasks: polling-based event indexers ---

const POLL_INTERVAL_MS = 3_000; // 3 seconds between polls
const SETTLEMENT_CURSOR_KEY = "settlement::SettlementEvent";
const ANNOUNCEMENT_CURSOR_KEY = "announcements::StealthAnnouncement";

/**
 * Load a persisted event cursor from the database.
 */
async function loadCursor(
  eventType: string,
): Promise<{ txDigest: string; eventSeq: string } | null> {
  const [row] = await db
    .select()
    .from(eventCursors)
    .where(eq(eventCursors.eventType, eventType))
    .limit(1);
  if (!row) return null;
  return { txDigest: row.txDigest, eventSeq: row.eventSeq };
}

/**
 * Save an event cursor to the database (upsert).
 */
async function saveCursor(
  eventType: string,
  cursor: { txDigest: string; eventSeq: string },
): Promise<void> {
  await db
    .insert(eventCursors)
    .values({
      eventType,
      txDigest: cursor.txDigest,
      eventSeq: cursor.eventSeq,
      updatedAt: new Date(),
    })
    .onConflictDoUpdate({
      target: eventCursors.eventType,
      set: {
        txDigest: cursor.txDigest,
        eventSeq: cursor.eventSeq,
        updatedAt: new Date(),
      },
    });
}

/**
 * Poll for settlement events, process them, and persist the cursor.
 * Drains all available pages before sleeping.
 */
async function pollSettlementEvents(): Promise<void> {
  let cursor = await loadCursor(SETTLEMENT_CURSOR_KEY);

  // Drain all available pages
  let hasMore = true;
  while (hasMore) {
    const result = await suiService.pollSettlementEvents(cursor);

    for (const event of result.events) {
      const [proposal] = await db
        .select()
        .from(proposals)
        .where(eq(proposals.intentHash, event.intentHash))
        .limit(1);

      if (proposal && proposal.status === "pending") {
        await db
          .update(proposals)
          .set({ status: "settled", suiTxDigest: event.txDigest })
          .where(eq(proposals.transactionId, proposal.transactionId));

        pushSettlementUpdate(
          proposal.transactionId,
          "settled",
          event.txDigest,
        );
      }
    }

    if (result.nextCursor) {
      cursor = result.nextCursor;
      await saveCursor(SETTLEMENT_CURSOR_KEY, cursor);
    }

    hasMore = result.hasNextPage;
  }
}

/**
 * Poll for announcement events, index them, and persist the cursor.
 * Drains all available pages before sleeping.
 */
async function pollAnnouncementEvents(): Promise<void> {
  let cursor = await loadCursor(ANNOUNCEMENT_CURSOR_KEY);

  let hasMore = true;
  while (hasMore) {
    const result = await suiService.pollAnnouncementEvents(cursor);

    for (const event of result.events) {
      await db.insert(announcements).values({
        ephemeralPubkey: event.ephemeralPubkey,
        viewTag: event.viewTag,
        stealthAddress: event.stealthAddress,
        metadata: event.metadata,
        txDigest: event.txDigest,
        timestamp: new Date(parseInt(event.timestamp)),
      });
    }

    if (result.nextCursor) {
      cursor = result.nextCursor;
      await saveCursor(ANNOUNCEMENT_CURSOR_KEY, cursor);
    }

    hasMore = result.hasNextPage;
  }
}

/**
 * Start a polling loop that runs a callback on a fixed interval.
 * On error, logs and continues polling (never exits).
 */
function startPollingLoop(name: string, fn: () => Promise<void>, intervalMs: number) {
  async function tick() {
    try {
      await fn();
    } catch (error) {
      console.error(`${name} poll error:`, error);
    }
    setTimeout(tick, intervalMs);
  }
  // Start immediately
  tick();
  console.log(`${name} polling started (every ${intervalMs}ms)`);
}

// Proposal expiry checker (every 30 seconds)
function startExpiryChecker() {
  setInterval(async () => {
    try {
      await db
        .update(proposals)
        .set({ status: "expired" })
        .where(
          and(
            eq(proposals.status, "pending"),
            lt(proposals.expiresAt, new Date()),
          ),
        );
    } catch (error) {
      console.error("Expiry checker error:", error);
    }
  }, 30_000);
}

// Start background tasks
startPollingLoop("Settlement indexer", pollSettlementEvents, POLL_INTERVAL_MS);
startPollingLoop("Announcement indexer", pollAnnouncementEvents, POLL_INTERVAL_MS);
startExpiryChecker();

// Start server
console.log(`identiPay backend starting on ${config.host}:${config.port}`);
Deno.serve({ port: config.port, hostname: config.host }, app.fetch);

export { app, db };
