import { z } from "zod";

export const CreatePayRequestInput = z.object({
  recipientName: z.string().min(3).max(20),
  amount: z.string().min(1),
  currency: z.string().min(1).max(10),
  memo: z.string().max(500).optional(),
  expiresInSeconds: z.number().int().positive().max(86400).default(3600),
});
export type CreatePayRequestInput = z.infer<typeof CreatePayRequestInput>;

export interface PayRequestResponse {
  requestId: string;
  recipientName: string;
  amount: string;
  currency: string;
  memo?: string;
  expiresAt: string;
  status: string;
  qrDataUrl: string;
  uri: string;
}
