import { Hono } from "hono";
import { eq } from "drizzle-orm";
import { NotFoundError } from "../errors/index.ts";
import { proposals } from "../db/schema.ts";
import type { Db } from "../db/connection.ts";
import { apiKeyAuth } from "../middleware/api-key.ts";

export function transactionRoutes(deps: { db: Db }) {
  const app = new Hono();

  app.use("/*", apiKeyAuth(deps.db));

  // GET /:txId/status -- check transaction/proposal status
  app.get("/:txId/status", async (c) => {
    const txId = c.req.param("txId");
    const merchant = c.get("merchant" as never) as Record<string, unknown>;

    const [proposal] = await deps.db
      .select()
      .from(proposals)
      .where(eq(proposals.transactionId, txId))
      .limit(1);

    if (!proposal) {
      throw new NotFoundError("Transaction not found");
    }

    // Ensure merchant owns this proposal
    if (proposal.merchantId !== merchant.id) {
      throw new NotFoundError("Transaction not found");
    }

    return c.json({
      transactionId: proposal.transactionId,
      status: proposal.status,
      suiTxDigest: proposal.suiTxDigest,
      settledAt: proposal.status === "settled" ? proposal.createdAt : null,
    });
  });

  return app;
}
