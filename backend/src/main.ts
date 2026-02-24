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
import { proposals, announcements } from "./db/schema.ts";
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
api.route("/transactions", transactionRoutes({ db }));
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

// --- Background tasks ---

// Settlement event subscriber
async function startSettlementSubscriber() {
  try {
    await suiService.subscribeToSettlementEvents(async (event) => {
      // Find proposal by intent hash
      const [proposal] = await db
        .select()
        .from(proposals)
        .where(eq(proposals.intentHash, event.intentHash))
        .limit(1);

      if (proposal && proposal.status === "pending") {
        // Update proposal status
        await db
          .update(proposals)
          .set({ status: "settled", suiTxDigest: event.merchant })
          .where(eq(proposals.transactionId, proposal.transactionId));

        // Push to WebSocket clients (buyer_stealth_address NOT forwarded)
        pushSettlementUpdate(
          proposal.transactionId,
          "settled",
          event.merchant,
        );
      }
    });
    console.log("Settlement event subscriber started");
  } catch (error) {
    console.error("Failed to start settlement subscriber:", error);
    // Retry after delay
    setTimeout(startSettlementSubscriber, 5000);
  }
}

// Announcement event indexer
async function startAnnouncementIndexer() {
  try {
    await suiService.subscribeToAnnouncementEvents(async (event) => {
      await db.insert(announcements).values({
        ephemeralPubkey: event.ephemeralPubkey,
        viewTag: event.viewTag,
        stealthAddress: event.stealthAddress,
        metadata: event.metadata,
        txDigest: event.txDigest,
        timestamp: new Date(parseInt(event.timestamp)),
      });
    });
    console.log("Announcement indexer started");
  } catch (error) {
    console.error("Failed to start announcement indexer:", error);
    setTimeout(startAnnouncementIndexer, 5000);
  }
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
startSettlementSubscriber();
startAnnouncementIndexer();
startExpiryChecker();

// Start server
console.log(`identiPay backend starting on ${config.host}:${config.port}`);
Deno.serve({ port: config.port, hostname: config.host }, app.fetch);

export { app, db };
