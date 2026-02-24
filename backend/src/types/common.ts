import { z } from "zod";

export const ProposalStatus = z.enum(["pending", "settled", "expired", "cancelled"]);
export type ProposalStatus = z.infer<typeof ProposalStatus>;

export const PayRequestStatus = z.enum(["pending", "paid", "expired", "cancelled"]);
export type PayRequestStatus = z.infer<typeof PayRequestStatus>;

export const PaginationParams = z.object({
  limit: z.coerce.number().int().min(1).max(1000).default(100),
  cursor: z.string().uuid().optional(),
});
export type PaginationParams = z.infer<typeof PaginationParams>;

export const HexString64 = z.string().regex(/^[0-9a-f]{64}$/, "Must be 64-char lowercase hex");
export const HexString66 = z.string().regex(/^0x[0-9a-f]{64}$/, "Must be 66-char hex with 0x prefix");
