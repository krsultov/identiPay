import { z } from "zod";

// Resolution returns ONLY public keys -- NEVER a Sui address
export interface NameResolution {
  name: string;
  spendPubkey: string;
  viewPubkey: string;
}

const hexString = z.string().regex(/^[0-9a-fA-F]+$/);

export const RegistrationInput = z.object({
  name: z.string().min(3).max(20),
  spendPubkey: hexString, // 32 bytes hex
  viewPubkey: hexString, // 32 bytes hex
  identityCommitment: hexString, // 32 bytes hex
  zkProof: hexString, // 256 bytes hex (Groth16 proof)
  zkPublicInputs: hexString, // N * 32 bytes hex
});
export type RegistrationInput = z.infer<typeof RegistrationInput>;
