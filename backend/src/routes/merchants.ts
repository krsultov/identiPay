import { Hono } from "hono";
import { RegisterMerchantInput } from "../types/merchant.ts";
import { ValidationError, ConflictError } from "../errors/index.ts";
import { generateApiKey, hashApiKey } from "../services/crypto.service.ts";
import { merchants } from "../db/schema.ts";
import { eq } from "drizzle-orm";
import type { Db } from "../db/connection.ts";
import type { SuiService } from "../services/sui.service.ts";

export function merchantRoutes(deps: { db: Db; suiService: SuiService }) {
  const app = new Hono();

  // GET /by-address/:suiAddress -- public lookup of merchant info by Sui address
  app.get("/by-address/:suiAddress", async (c) => {
    const suiAddress = c.req.param("suiAddress");
    const [merchant] = await deps.db
      .select({
        name: merchants.name,
        publicKey: merchants.publicKey,
        suiAddress: merchants.suiAddress,
        did: merchants.did,
      })
      .from(merchants)
      .where(eq(merchants.suiAddress, suiAddress))
      .limit(1);

    if (!merchant) {
      return c.json({ error: "Merchant not found" }, 404);
    }

    return c.json(merchant);
  });

  // POST /register -- register a new merchant
  app.post("/register", async (c) => {
    const body = await c.req.json();
    const parsed = RegisterMerchantInput.safeParse(body);
    if (!parsed.success) {
      throw new ValidationError("Invalid merchant registration input", parsed.error.flatten());
    }

    const input = parsed.data;

    // Check for duplicate hostname
    const [existing] = await deps.db
      .select()
      .from(merchants)
      .where(eq(merchants.hostname, input.hostname))
      .limit(1);

    if (existing) {
      throw new ConflictError("A merchant with this hostname already exists");
    }

    // Generate DID and API key
    const id = crypto.randomUUID();
    const did = `did:identipay:${input.hostname}:${id}`;
    const apiKey = generateApiKey();
    const apiKeyHash = hashApiKey(apiKey);

    // Register on-chain via admin key
    await deps.suiService.registerMerchantOnChain({
      did,
      name: input.name,
      suiAddress: input.suiAddress,
      publicKey: input.publicKey,
      hostname: input.hostname,
    });

    // Store in database
    await deps.db.insert(merchants).values({
      id,
      name: input.name,
      suiAddress: input.suiAddress,
      hostname: input.hostname,
      did,
      publicKey: input.publicKey,
      apiKeyHash,
      active: true,
    });

    return c.json({ id, did, apiKey }, 201);
  });

  return app;
}
