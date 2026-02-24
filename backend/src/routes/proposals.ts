import { Hono } from "hono";
import { CreateProposalInput } from "../types/proposal.ts";
import { ValidationError } from "../errors/index.ts";
import { createProposalWithHash } from "../services/proposal.service.ts";
import { buildProposalUri, generateQrDataUrl } from "../services/qr.service.ts";
import { proposals } from "../db/schema.ts";
import type { Db } from "../db/connection.ts";
import { apiKeyAuth } from "../middleware/api-key.ts";

export function proposalRoutes(deps: { db: Db; packageId: string }) {
  const app = new Hono();

  // All proposal routes require API key auth
  app.use("/*", apiKeyAuth(deps.db));

  // POST / -- create a new proposal
  app.post("/", async (c) => {
    const body = await c.req.json();
    const parsed = CreateProposalInput.safeParse(body);
    if (!parsed.success) {
      throw new ValidationError("Invalid proposal input", parsed.error.flatten());
    }

    const input = parsed.data;
    const merchant = c.get("merchant" as never) as Record<string, string>;

    const transactionId = crypto.randomUUID();
    const settlementModule = `${deps.packageId}::settlement`;

    // Build full proposal with intent hash (NO buyer fields)
    const proposal = await createProposalWithHash(
      transactionId,
      {
        did: merchant.did,
        name: merchant.name,
        suiAddress: merchant.suiAddress,
        publicKey: merchant.publicKey,
      },
      input,
      settlementModule,
    );

    // Generate QR code and URI
    const uri = buildProposalUri(merchant.hostname, transactionId);
    const qrDataUrl = await generateQrDataUrl(uri);

    // Store in database
    await deps.db.insert(proposals).values({
      transactionId,
      merchantId: merchant.id,
      proposalJson: proposal,
      intentHash: proposal.intentHash,
      status: "pending",
      expiresAt: new Date(proposal.expiresAt),
    });

    return c.json(
      {
        transactionId,
        intentHash: proposal.intentHash,
        qrDataUrl,
        uri,
        proposal,
        expiresAt: proposal.expiresAt,
      },
      201,
    );
  });

  return app;
}
