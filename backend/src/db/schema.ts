import {
  pgTable,
  uuid,
  varchar,
  char,
  boolean,
  timestamp,
  jsonb,
  text,
  integer,
  pgEnum,
  index,
} from "drizzle-orm/pg-core";

export const proposalStatusEnum = pgEnum("proposal_status", [
  "pending",
  "settled",
  "expired",
  "cancelled",
]);

export const payRequestStatusEnum = pgEnum("pay_request_status", [
  "pending",
  "paid",
  "expired",
  "cancelled",
]);

export const merchants = pgTable("merchants", {
  id: uuid("id").primaryKey().defaultRandom(),
  name: varchar("name", { length: 255 }).notNull(),
  suiAddress: varchar("sui_address", { length: 66 }).notNull(),
  hostname: varchar("hostname", { length: 255 }).unique().notNull(),
  did: varchar("did", { length: 512 }).unique().notNull(),
  publicKey: char("public_key", { length: 64 }).notNull(),
  apiKeyHash: char("api_key_hash", { length: 64 }).notNull(),
  active: boolean("active").default(true).notNull(),
  createdAt: timestamp("created_at").defaultNow().notNull(),
  updatedAt: timestamp("updated_at").defaultNow().notNull(),
});

export const proposals = pgTable(
  "proposals",
  {
    transactionId: uuid("transaction_id").primaryKey().defaultRandom(),
    merchantId: uuid("merchant_id")
      .references(() => merchants.id)
      .notNull(),
    proposalJson: jsonb("proposal_json").notNull(),
    intentHash: char("intent_hash", { length: 64 }).unique().notNull(),
    status: proposalStatusEnum("status").default("pending").notNull(),
    expiresAt: timestamp("expires_at").notNull(),
    suiTxDigest: varchar("sui_tx_digest", { length: 66 }),
    createdAt: timestamp("created_at").defaultNow().notNull(),
  },
  (table) => [
    index("proposals_intent_hash_idx").on(table.intentHash),
    index("proposals_status_idx").on(table.status),
    index("proposals_merchant_id_idx").on(table.merchantId),
  ],
);

// Cache only -- no buyer address stored. No FK to announcements (privacy).
export const names = pgTable("names", {
  name: varchar("name", { length: 20 }).primaryKey(),
  spendPubkey: char("spend_pubkey", { length: 64 }).notNull(),
  viewPubkey: char("view_pubkey", { length: 64 }).notNull(),
  identityCommitment: char("identity_commitment", { length: 64 }).notNull(),
  onChainObjectId: varchar("on_chain_object_id", { length: 66 }),
  createdAt: timestamp("created_at").defaultNow().notNull(),
  updatedAt: timestamp("updated_at").defaultNow().notNull(),
});

// No FK to names table -- privacy invariant: announcements never link to identities
export const announcements = pgTable(
  "announcements",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    ephemeralPubkey: char("ephemeral_pubkey", { length: 64 }).notNull(),
    viewTag: integer("view_tag").notNull(),
    stealthAddress: varchar("stealth_address", { length: 66 }).notNull(),
    metadata: text("metadata"),
    txDigest: varchar("tx_digest", { length: 66 }).notNull(),
    timestamp: timestamp("timestamp").defaultNow().notNull(),
  },
  (table) => [
    index("announcements_view_tag_idx").on(table.viewTag),
    index("announcements_timestamp_idx").on(table.timestamp),
    index("announcements_stealth_address_idx").on(table.stealthAddress),
  ],
);

export const payRequests = pgTable("pay_requests", {
  requestId: uuid("request_id").primaryKey().defaultRandom(),
  recipientName: varchar("recipient_name", { length: 20 }).notNull(),
  amount: varchar("amount", { length: 78 }).notNull(),
  currency: varchar("currency", { length: 10 }).notNull(),
  memo: text("memo"),
  expiresAt: timestamp("expires_at").notNull(),
  status: payRequestStatusEnum("status").default("pending").notNull(),
  createdAt: timestamp("created_at").defaultNow().notNull(),
});
