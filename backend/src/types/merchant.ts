import { z } from "zod";
import { HexString64 } from "./common.ts";

export const RegisterMerchantInput = z.object({
  name: z.string().min(1).max(255),
  suiAddress: z.string().regex(/^0x[0-9a-f]{64}$/),
  hostname: z.string().min(1).max(255),
  publicKey: HexString64,
});
export type RegisterMerchantInput = z.infer<typeof RegisterMerchantInput>;

export interface MerchantResponse {
  id: string;
  did: string;
  name: string;
  suiAddress: string;
  hostname: string;
  publicKey: string;
  active: boolean;
  createdAt: string;
}

export interface MerchantRegistrationResponse {
  id: string;
  did: string;
  apiKey: string;
}
