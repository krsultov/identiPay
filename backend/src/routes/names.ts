import { Hono } from "hono";
import { eq } from "drizzle-orm";
import { NotFoundError, ValidationError } from "../errors/index.ts";
import { SponsoredRegistrationInput } from "../types/name.ts";
import { names } from "../db/schema.ts";
import type { Db } from "../db/connection.ts";
import type { SuiService } from "../services/sui.service.ts";

export function nameRoutes(deps: { db: Db; suiService: SuiService }) {
  const app = new Hono();

  // GET /:name -- resolve a name to meta-address public keys
  // Returns ONLY (spendPubkey, viewPubkey) -- NEVER a Sui address
  app.get("/:name", async (c) => {
    const name = c.req.param("name");

    // Check DB cache first
    const [cached] = await deps.db
      .select()
      .from(names)
      .where(eq(names.name, name))
      .limit(1);

    if (cached) {
      return c.json({
        name: cached.name,
        spendPubkey: cached.spendPubkey,
        viewPubkey: cached.viewPubkey,
      });
    }

    // Fall back to on-chain resolution
    const resolved = await deps.suiService.resolveName(name);
    if (!resolved) {
      throw new NotFoundError(`Name "${name}" not found`);
    }

    // Cache the result (no Sui address stored)
    await deps.db
      .insert(names)
      .values({
        name,
        spendPubkey: resolved.spendPubkey,
        viewPubkey: resolved.viewPubkey,
        identityCommitment: "0".repeat(64), // Commitment not returned from resolve
      })
      .onConflictDoUpdate({
        target: names.name,
        set: {
          spendPubkey: resolved.spendPubkey,
          viewPubkey: resolved.viewPubkey,
          updatedAt: new Date(),
        },
      });

    return c.json({
      name,
      spendPubkey: resolved.spendPubkey,
      viewPubkey: resolved.viewPubkey,
    });
  });

  // POST /register -- sponsor a wallet-signed name registration transaction
  // The wallet builds + signs the PTB. We add gas sponsorship and submit.
  // ctx.sender() remains the wallet's address (preserves MetaAddressEntry ownership).
  app.post("/register", async (c) => {
    const body = await c.req.json();
    const parsed = SponsoredRegistrationInput.safeParse(body);
    if (!parsed.success) {
      throw new ValidationError("Invalid registration input", parsed.error.flatten());
    }

    const digest = await deps.suiService.sponsorAndSubmitTx(parsed.data.signedTxBytes);

    return c.json({ txDigest: digest }, 201);
  });

  return app;
}
