import { Hono } from "hono";
import { errorHandler } from "../../src/middleware/error-handler.ts";

/**
 * Create a mock database for testing.
 * Returns an object with basic Drizzle-compatible methods.
 */
export function createMockDb() {
  const store: Record<string, unknown[]> = {};

  return {
    select: () => ({
      // deno-lint-ignore no-explicit-any
      from: (table: { _: { name: string } } | any) => ({
        where: (_condition?: unknown) => ({
          limit: (_n: number) => {
            const tableName = table?._.name ?? "unknown";
            return Promise.resolve(store[tableName] ?? []);
          },
        }),
        orderBy: (_order: unknown) => ({
          limit: (_n: number) => Promise.resolve([]),
        }),
      }),
    }),
    // deno-lint-ignore no-explicit-any
    insert: (table: any) => ({
      values: (data: unknown) => {
        const tableName = table?._.name ?? "unknown";
        if (!store[tableName]) store[tableName] = [];
        store[tableName].push(data);
        return {
          onConflictDoUpdate: () => Promise.resolve(),
          then: (resolve: () => void) => resolve(),
        };
      },
    }),
    // deno-lint-ignore no-explicit-any
    update: (_table: any) => ({
      set: (_data: unknown) => ({
        where: (_condition: unknown) => Promise.resolve(),
      }),
    }),
    _store: store,
    _seed: (tableName: string, data: unknown[]) => {
      store[tableName] = data;
    },
  };
}

/**
 * Create a test app with error handling middleware.
 */
export function createTestApp() {
  const app = new Hono();
  app.onError(errorHandler);
  return app;
}

/**
 * Create a mock SuiService for testing.
 */
export function createMockSuiService() {
  return {
    lookupMerchant: (_did: string) => Promise.resolve(null),
    resolveName: (_name: string) => Promise.resolve(null),
    registerMerchantOnChain: (_params: unknown) => Promise.resolve("mock-digest-" + crypto.randomUUID()),
    sponsorAndSubmitTx: (_bytes: string) => Promise.resolve("mock-digest-" + crypto.randomUUID()),
    pollSettlementEvents: () => Promise.resolve({ events: [], nextCursor: null, hasNextPage: false }),
    pollAnnouncementEvents: () => Promise.resolve({ events: [], nextCursor: null, hasNextPage: false }),
    getAdminAddress: () => "0x" + "00".repeat(32),
  };
}
