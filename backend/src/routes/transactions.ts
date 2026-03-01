import { Hono } from "hono";
import { eq } from "drizzle-orm";
import { NotFoundError, ValidationError } from "../errors/index.ts";
import { proposals } from "../db/schema.ts";
import type { Db } from "../db/connection.ts";
import type { SuiService } from "../services/sui.service.ts";
import { apiKeyAuth } from "../middleware/api-key.ts";

export function transactionRoutes(deps: { db: Db; suiService: SuiService }) {
  const app = new Hono();

  // --- Wallet gas sponsorship endpoints (no API key auth) ---

  // POST /gas-sponsor — wallet sends transaction params, backend builds + sponsors
  app.post("/gas-sponsor", async (c) => {
    const body = await c.req.json<{
      type?: string;
      senderAddress?: string;
      [key: string]: unknown;
    }>();

    if (!body.type || !body.senderAddress) {
      throw new ValidationError("type and senderAddress are required");
    }

    let txBytes: string;

    if (body.type === "send") {
      const { senderAddress, coinId, amount, recipient, coinType, ephemeralPubkey, viewTag } =
        body as Record<string, unknown>;
      if (!amount || !recipient || !coinType || !ephemeralPubkey) {
        throw new ValidationError("Missing required send parameters");
      }
      txBytes = await deps.suiService.buildSponsoredSend({
        senderAddress: senderAddress as string,
        coinId: coinId as string | undefined,
        amount: String(amount),
        recipient: recipient as string,
        coinType: coinType as string,
        ephemeralPubkey: ephemeralPubkey as number[],
        viewTag: Number(viewTag ?? 0),
      });
    } else if (body.type === "settlement" || body.type === "settlement_no_zk") {
      const params = body as Record<string, unknown>;
      if (!params.amount || !params.merchantAddress) {
        throw new ValidationError("Missing required settlement parameters");
      }
      txBytes = await deps.suiService.buildSponsoredSettlement({
        senderAddress: params.senderAddress as string,
        coinId: params.coinId as string | undefined,
        coinType: params.coinType as string,
        amount: String(params.amount),
        merchantAddress: params.merchantAddress as string,
        buyerStealthAddr: params.buyerStealthAddr as string,
        intentSig: params.intentSig as number[],
        intentHash: params.intentHash as number[],
        buyerPubkey: params.buyerPubkey as number[],
        proposalExpiry: String(params.proposalExpiry),
        encryptedPayload: params.encryptedPayload as number[],
        payloadNonce: params.payloadNonce as number[],
        ephemeralPubkey: params.ephemeralPubkey as number[],
        encryptedWarrantyTerms: params.encryptedWarrantyTerms as number[] ?? [],
        warrantyTermsNonce: params.warrantyTermsNonce as number[] ?? [],
        warrantyExpiry: String(params.warrantyExpiry ?? "0"),
        warrantyTransferable: Boolean(params.warrantyTransferable),
        stealthEphemeralPubkey: params.stealthEphemeralPubkey as number[] ?? [],
        stealthViewTag: Number(params.stealthViewTag ?? 0),
        zkProof: params.zkProof as number[] | undefined,
        zkPublicInputs: params.zkPublicInputs as number[] | undefined,
      });
    } else if (body.type === "pool_deposit") {
      const params = body as Record<string, unknown>;
      if (!params.amount || !params.coinType || !params.ephemeralPubkey) {
        throw new ValidationError("Missing required pool_deposit parameters");
      }
      txBytes = await deps.suiService.buildSponsoredPoolDeposit({
        senderAddress: params.senderAddress as string,
        coinType: params.coinType as string,
        amount: String(params.amount),
        noteCommitment: params.ephemeralPubkey as number[],
      });
    } else if (body.type === "pool_withdraw") {
      const params = body as Record<string, unknown>;
      if (!params.amount || !params.recipient || !params.coinType || !params.zkProof || !params.zkPublicInputs) {
        throw new ValidationError("Missing required pool_withdraw parameters");
      }
      txBytes = await deps.suiService.buildSponsoredPoolWithdraw({
        senderAddress: params.senderAddress as string,
        coinType: params.coinType as string,
        amount: String(params.amount),
        recipient: params.recipient as string,
        nullifier: params.nullifier as number[],
        changeCommitment: params.changeCommitment as number[],
        zkProof: params.zkProof as number[],
        zkPublicInputs: params.zkPublicInputs as number[],
      });
    } else {
      throw new ValidationError(`Unknown transaction type: ${body.type}`);
    }

    return c.json({ txBytes });
  });

  // POST /submit — wallet sends signed tx bytes, backend co-signs as gas owner
  app.post("/submit", async (c) => {
    const body = await c.req.json<{
      txBytes?: string;
      senderSignature?: string;
    }>();

    if (!body.txBytes || !body.senderSignature) {
      throw new ValidationError("txBytes and senderSignature are required");
    }

    const txDigest = await deps.suiService.submitSponsoredTx(
      body.txBytes,
      body.senderSignature,
    );

    return c.json({ txDigest });
  });

  // POST /submit-pool — backend signs as both sender and gas owner (pool withdrawals)
  app.post("/submit-pool", async (c) => {
    const body = await c.req.json<{
      txBytes?: string;
    }>();

    if (!body.txBytes) {
      throw new ValidationError("txBytes is required");
    }

    const txDigest = await deps.suiService.submitAdminOnlyTx(body.txBytes);

    return c.json({ txDigest });
  });

  // --- Merchant API-key-authenticated endpoints ---

  // GET /:txId/status -- check transaction/proposal status
  app.get("/:txId/status", apiKeyAuth(deps.db), async (c) => {
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
