import { migrate } from "drizzle-orm/postgres-js/migrator";
import { createDb } from "./connection.ts";

const databaseUrl = Deno.env.get("DATABASE_URL");
if (!databaseUrl) {
  console.error("DATABASE_URL is required");
  Deno.exit(1);
}

const { db, client } = createDb(databaseUrl);

console.log("Running migrations...");
await migrate(db, { migrationsFolder: "./drizzle" });
console.log("Migrations complete.");

await client.end();
