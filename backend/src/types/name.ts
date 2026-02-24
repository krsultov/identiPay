import { z } from "zod";

// Resolution returns ONLY public keys -- NEVER a Sui address
export interface NameResolution {
  name: string;
  spendPubkey: string;
  viewPubkey: string;
}

export const SponsoredRegistrationInput = z.object({
  signedTxBytes: z.string(), // base64-encoded wallet-signed transaction
});
export type SponsoredRegistrationInput = z.infer<typeof SponsoredRegistrationInput>;
