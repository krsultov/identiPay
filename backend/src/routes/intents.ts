import { Hono } from "hono";
import { eq } from "drizzle-orm";
import { NotFoundError, ValidationError } from "../errors/index.ts";
import { proposals } from "../db/schema.ts";
import type { Db } from "../db/connection.ts";

export function intentRoutes(deps: { db: Db }) {
  const app = new Hono();

  // GET /:txId -- resolve a proposal by transaction ID (public endpoint)
  // Per whitepaper section 6: https://<hostname>/api/identipay/v1/intents/<transaction-id>
  app.get("/:txId", async (c) => {
    const txId = c.req.param("txId");

    const [proposal] = await deps.db
      .select()
      .from(proposals)
      .where(eq(proposals.transactionId, txId))
      .limit(1);

    if (!proposal) {
      throw new NotFoundError("Proposal not found");
    }

    // Check expiry
    if (new Date(proposal.expiresAt) < new Date()) {
      // Update status if still pending
      if (proposal.status === "pending") {
        await deps.db
          .update(proposals)
          .set({ status: "expired" })
          .where(eq(proposals.transactionId, txId));
      }
      throw new ValidationError("Proposal has expired");
    }

    if (proposal.status === "cancelled") {
      throw new ValidationError("Proposal has been cancelled");
    }

    return c.json(proposal.proposalJson);
  });

  return app;
}
