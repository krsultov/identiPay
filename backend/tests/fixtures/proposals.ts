import type { CreateProposalInput } from "../../src/types/proposal.ts";

export const VALID_PROPOSAL_INPUT: CreateProposalInput = {
  items: [
    {
      name: "Espresso",
      quantity: 2,
      unitPrice: "500000000",
    },
    {
      name: "Croissant",
      quantity: 1,
      unitPrice: "300000000",
    },
  ],
  amount: {
    value: "1300000000",
    currency: "SUI",
  },
  deliverables: {
    receipt: true,
    warranty: {
      durationDays: 30,
      transferable: false,
    },
  },
  constraints: {
    ageGate: 18,
  },
  expiresInSeconds: 900,
};

export const MINIMAL_PROPOSAL_INPUT: CreateProposalInput = {
  items: [
    {
      name: "Coffee",
      quantity: 1,
      unitPrice: "100000000",
    },
  ],
  amount: {
    value: "100000000",
    currency: "SUI",
  },
  deliverables: {
    receipt: true,
  },
  expiresInSeconds: 300,
};
