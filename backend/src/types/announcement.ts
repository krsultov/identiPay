import { z } from "zod";

export const AnnouncementQuery = z.object({
  since: z.string().datetime().optional(),
  viewTag: z.coerce.number().int().min(0).max(255).optional(),
  limit: z.coerce.number().int().min(1).max(1000).default(100),
  cursor: z.string().uuid().optional(),
});
export type AnnouncementQuery = z.infer<typeof AnnouncementQuery>;

export interface AnnouncementResponse {
  id: string;
  ephemeralPubkey: string;
  viewTag: number;
  stealthAddress: string;
  metadata: string | null;
  txDigest: string;
  timestamp: string;
}
