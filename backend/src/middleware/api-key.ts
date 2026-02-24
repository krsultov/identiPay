import type { Context, Next } from "hono";
import { eq } from "drizzle-orm";
import { AuthError } from "../errors/index.ts";
import { merchants } from "../db/schema.ts";
import { hashApiKey } from "../services/crypto.service.ts";
import type { Db } from "../db/connection.ts";

export function apiKeyAuth(db: Db) {
  return async (c: Context, next: Next) => {
    const authHeader = c.req.header("Authorization");
    if (!authHeader?.startsWith("Bearer ")) {
      throw new AuthError("Missing or invalid Authorization header");
    }

    const apiKey = authHeader.slice(7);
    if (!apiKey) {
      throw new AuthError("Missing API key");
    }

    const keyHash = hashApiKey(apiKey);

    const [merchant] = await db
      .select()
      .from(merchants)
      .where(eq(merchants.apiKeyHash, keyHash))
      .limit(1);

    if (!merchant) {
      throw new AuthError("Invalid API key");
    }

    if (!merchant.active) {
      throw new AuthError("Merchant account is deactivated");
    }

    c.set("merchant", merchant);
    await next();
  };
}
