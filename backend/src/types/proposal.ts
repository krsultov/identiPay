import { z } from "zod";

export const MerchantInfo = z.object({
  did: z.string(),
  name: z.string(),
  suiAddress: z.string(),
  publicKey: z.string(),
});

export const LineItem = z.object({
  name: z.string(),
  quantity: z.number().int().positive(),
  unitPrice: z.string(),
  currency: z.string().optional(),
});

export const Amount = z.object({
  value: z.string(),
  currency: z.string(),
});

export const Deliverables = z.object({
  receipt: z.boolean(),
  warranty: z
    .object({
      durationDays: z.number().int().positive(),
      transferable: z.boolean(),
    })
    .optional(),
});

export const Constraints = z
  .object({
    ageGate: z.number().int().min(0).optional(),
    regionRestriction: z.array(z.string()).optional(),
  })
  .optional();

// Full JSON-LD CommerceProposal (as stored / returned)
export const CommerceProposal = z.object({
  "@context": z.literal("https://schema.identipay.net/v1"),
  "@type": z.literal("CommerceProposal"),
  transactionId: z.string().uuid(),
  merchant: MerchantInfo,
  items: z.array(LineItem).min(1),
  amount: Amount,
  deliverables: Deliverables,
  constraints: Constraints,
  expiresAt: z.string().datetime(),
  intentHash: z.string(),
  settlementChain: z.literal("sui"),
  settlementModule: z.string(),
});
export type CommerceProposal = z.infer<typeof CommerceProposal>;

// Input from merchant when creating a proposal (no buyer fields)
export const CreateProposalInput = z.object({
  items: z.array(LineItem).min(1),
  amount: Amount,
  deliverables: Deliverables,
  constraints: Constraints,
  expiresInSeconds: z.number().int().positive().max(86400).default(900),
});
export type CreateProposalInput = z.infer<typeof CreateProposalInput>;
