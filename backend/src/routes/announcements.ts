import { Hono } from "hono";
import { and, eq, gt, lt, desc } from "drizzle-orm";
import { AnnouncementQuery } from "../types/announcement.ts";
import { ValidationError } from "../errors/index.ts";
import { announcements } from "../db/schema.ts";
import type { Db } from "../db/connection.ts";

export function announcementRoutes(deps: { db: Db }) {
  const app = new Hono();

  // GET / -- query stealth announcements (public endpoint)
  // viewTag filter is OPTIONAL -- wallets SHOULD fetch all and filter locally
  // to avoid revealing their view_tag set to the backend (privacy invariant #5)
  app.get("/", async (c) => {
    const query = AnnouncementQuery.safeParse(c.req.query());
    if (!query.success) {
      throw new ValidationError("Invalid query parameters", query.error.flatten());
    }

    const { since, viewTag, limit, cursor } = query.data;

    const conditions = [];

    if (since) {
      conditions.push(gt(announcements.timestamp, new Date(since)));
    }

    if (viewTag !== undefined) {
      conditions.push(eq(announcements.viewTag, viewTag));
    }

    if (cursor) {
      conditions.push(lt(announcements.id, cursor));
    }

    const results = await deps.db
      .select()
      .from(announcements)
      .where(conditions.length > 0 ? and(...conditions) : undefined)
      .orderBy(desc(announcements.timestamp))
      .limit(limit);

    const nextCursor =
      results.length === limit ? results[results.length - 1].id : null;

    return c.json({
      announcements: results.map((a) => ({
        id: a.id,
        ephemeralPubkey: a.ephemeralPubkey,
        viewTag: a.viewTag,
        stealthAddress: a.stealthAddress,
        metadata: a.metadata,
        txDigest: a.txDigest,
        timestamp: a.timestamp.toISOString(),
      })),
      nextCursor,
    });
  });

  return app;
}
