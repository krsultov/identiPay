import { Hono } from "hono";
import { eq } from "drizzle-orm";
import { NotFoundError, ValidationError } from "../errors/index.ts";
import { CreatePayRequestInput } from "../types/pay-request.ts";
import { buildPayRequestUri, generateQrDataUrl } from "../services/qr.service.ts";
import { payRequests } from "../db/schema.ts";
import { names } from "../db/schema.ts";
import type { Db } from "../db/connection.ts";
import type { SuiService } from "../services/sui.service.ts";

export function payRequestRoutes(deps: { db: Db; suiService: SuiService }) {
  const app = new Hono();

  // POST / -- create a payment request
  app.post("/", async (c) => {
    const body = await c.req.json();
    const parsed = CreatePayRequestInput.safeParse(body);
    if (!parsed.success) {
      throw new ValidationError("Invalid pay request input", parsed.error.flatten());
    }

    const input = parsed.data;

    // Verify the recipient name exists before creating the pay request
    const [cachedName] = await deps.db
      .select()
      .from(names)
      .where(eq(names.name, input.recipientName))
      .limit(1);

    if (!cachedName) {
      // Fall back to on-chain resolution
      const resolved = await deps.suiService.resolveName(input.recipientName);
      if (!resolved) {
        throw new NotFoundError(
          `Recipient name "${input.recipientName}" is not registered`
        );
      }
    }

    const requestId = crypto.randomUUID();
    const expiresAt = new Date(Date.now() + input.expiresInSeconds * 1000);

    await deps.db.insert(payRequests).values({
      requestId,
      recipientName: input.recipientName,
      amount: input.amount,
      currency: input.currency,
      memo: input.memo,
      expiresAt,
      status: "pending",
    });

    const uri = buildPayRequestUri(
      input.recipientName,
      input.amount,
      input.currency,
      input.memo,
    );
    const qrDataUrl = await generateQrDataUrl(uri);

    return c.json(
      {
        requestId,
        recipientName: input.recipientName,
        amount: input.amount,
        currency: input.currency,
        memo: input.memo,
        expiresAt: expiresAt.toISOString(),
        status: "pending",
        qrDataUrl,
        uri,
      },
      201,
    );
  });

  // GET /:requestId -- resolve a payment request
  // Returns recipient meta-address (public keys only, NO Sui address)
  app.get("/:requestId", async (c) => {
    const requestId = c.req.param("requestId");

    const [request] = await deps.db
      .select()
      .from(payRequests)
      .where(eq(payRequests.requestId, requestId))
      .limit(1);

    if (!request) {
      throw new NotFoundError("Payment request not found");
    }

    // Check expiry
    if (new Date(request.expiresAt) < new Date() && request.status === "pending") {
      await deps.db
        .update(payRequests)
        .set({ status: "expired" })
        .where(eq(payRequests.requestId, requestId));
      throw new ValidationError("Payment request has expired");
    }

    if (request.status !== "pending") {
      throw new ValidationError(`Payment request is ${request.status}`);
    }

    // Resolve recipient meta-address (public keys only)
    let recipientKeys: { spendPubkey: string; viewPubkey: string } | null = null;

    const [cached] = await deps.db
      .select()
      .from(names)
      .where(eq(names.name, request.recipientName))
      .limit(1);

    if (cached) {
      recipientKeys = {
        spendPubkey: cached.spendPubkey,
        viewPubkey: cached.viewPubkey,
      };
    } else {
      const resolved = await deps.suiService.resolveName(request.recipientName);
      if (resolved) {
        recipientKeys = resolved;
      }
    }

    return c.json({
      requestId: request.requestId,
      recipientName: request.recipientName,
      amount: request.amount,
      currency: request.currency,
      memo: request.memo,
      expiresAt: request.expiresAt.toISOString(),
      status: request.status,
      recipient: recipientKeys
        ? {
            name: request.recipientName,
            spendPubkey: recipientKeys.spendPubkey,
            viewPubkey: recipientKeys.viewPubkey,
          }
        : null,
    });
  });

  return app;
}
