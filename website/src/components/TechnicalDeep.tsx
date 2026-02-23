"use client";

import { motion, useInView, AnimatePresence } from "framer-motion";
import { useRef, useState, useEffect } from "react";

const ease = [0.25, 0.46, 0.45, 0.94] as const;

const tabs = [
  {
    id: "identity",
    label: "Stealth Identity",
    content: {
      title: "Stealth meta-addresses: names, not addresses",
      description:
        "A human-readable name (e.g., @krum.idpay) resolves to a pair of cryptographic public keys \u2014 never to a blockchain address. Every incoming payment is delivered to a fresh, one-time stealth address derived via ECDH. No two transactions share an address. No on-chain address is ever linked to the name. Value that accumulates across stealth addresses is merged through a shielded pool using ZK proofs, breaking on-chain linkage.",
      code: `// Stealth address derivation (sender-side)

// 1. Resolve name from on-chain registry
(K_spend, K_view) = Registry.resolve("@alice.idpay")

// 2. Generate ephemeral keypair
r = random_scalar()
R = r * G

// 3. ECDH shared secret
shared = r * K_view

// 4. Derive stealth scalar
s = SHA256(shared || "identipay-stealth-v1")

// 5. Stealth public key
K_stealth = K_spend + s * G

// 6. Stealth Sui address
addr = BLAKE2b-256(0x00 || K_stealth)

// 7. Emit announcement (R, view_tag, addr)`,
      filename: "stealth-protocol.ts",
      details: [
        { label: "Name Registry", value: "On-chain, enforces one credential per name (anti-Sybil)" },
        { label: "Meta-Address", value: "Ed25519 spend key + X25519 view key" },
        { label: "Stealth Protocol", value: "ECDH \u2192 one-time address derivation per payment" },
        { label: "Detection", value: "View tag fast-filter (256\u00d7 speedup) + ECDH scan" },
        { label: "Coin Management", value: "Shielded pool merges via ZK proofs \u2014 no on-chain linkage" },
      ],
    },
  },
  {
    id: "intent",
    label: "Intent Engine",
    content: {
      title: "Semantic binding from intent to execution",
      description:
        "The Push-Only Intent Engine ensures merchants can never charge you directly. They publish typed proposals; your wallet translates them into human-readable statements. Your signature binds to the canonicalized proposal \u2014 not to opaque bytes or raw calldata.",
      code: `// Proposal \u2192 Canonical form
tx \u2190 BCS(
  CanonicalProposal,
  Context,
  Nonce
)

// Intent message construction
m \u2190 Intent \u2225 H(tx)

// Payer signs semantic intent
\u03C3 \u2190 Sign_sk(m)

// On-chain verification
verify(\u03C3, m) \u2192 execute PTB(tx)

// Revocation: delete delegate key
// \u2192 all future proposals fail on-chain`,
      filename: "intent-engine.ts",
      details: [
        { label: "Proposal Format", value: "Canonicalized JSON-LD typed data" },
        { label: "Binding", value: "Domain-separated intent hash" },
        { label: "Revocation", value: "Deterministic \u2014 delete key or policy" },
        { label: "UI Contract", value: "Wallet renders typed proposal, not raw calldata" },
        { label: "Fields", value: "sku, amount, merchant_did, deliverable, expiry, constraints" },
      ],
    },
  },
  {
    id: "settlement",
    label: "Atomic Settlement",
    content: {
      title: "Indivisible commerce on Sui",
      description:
        "Using Sui\u2019s Programmable Transaction Blocks, identiPay executes multi-object atomic swaps. Payment, warranty, receipt, and compliance artifacts move together \u2014 or not at all. Artifacts are delivered to the buyer\u2019s one-time stealth address and encrypted so only buyer and merchant can read them.",
      code: `public entry fun execute_commerce(
  payment: Coin<USDC>,
  merchant: address,
  buyer_stealth_addr: address,
  zk_proof: vector<u8>,
  intent_sig: vector<u8>,
  intent_hash: vector<u8>,
  encrypted_payload: vector<u8>,
  payload_nonce: vector<u8>,
  ephemeral_pubkey: vector<u8>,
  has_warranty: bool,
  ctx: &mut TxContext,
) {
  verify_proof(zk_proof, intent_hash);
  verify_intent_signature(
    intent_sig, intent_hash
  );

  transfer::public_transfer(payment, merchant);

  let receipt = mint_receipt(
    merchant, intent_hash,
    encrypted_payload,
    payload_nonce,
    ephemeral_pubkey, ctx
  );
  transfer::transfer(
    receipt, buyer_stealth_addr
  );

  if (has_warranty) {
    let warranty = mint_warranty(
      merchant, intent_hash,
      ephemeral_pubkey, ctx
    );
    transfer::transfer(
      warranty, buyer_stealth_addr
    );
  };

  emit_settlement_event(intent_hash);
}`,
      filename: "commerce.move",
      details: [
        { label: "Settlement Layer", value: "Sui PTBs (Programmable Transaction Blocks)" },
        { label: "Artifact Delivery", value: "To buyer\u2019s one-time stealth address \u2014 unlinkable" },
        { label: "Artifact Encryption", value: "AES-256-GCM, ECDH-derived key \u2014 buyer & merchant only" },
        { label: "Failure Modes", value: "Expired proposal, invalid sig, bad ZK proof, missing artifacts" },
        { label: "Ownership", value: "Soulbound or transferable per object capability" },
      ],
    },
  },
];

function TypewriterCode({ code, isActive }: { code: string; isActive: boolean }) {
  const [displayedLines, setDisplayedLines] = useState(0);
  const lines = code.split("\n");

  useEffect(() => {
    if (!isActive) {
      setDisplayedLines(0);
      return;
    }
    setDisplayedLines(0);
    let current = 0;
    const interval = setInterval(() => {
      current++;
      setDisplayedLines(current);
      if (current >= lines.length) clearInterval(interval);
    }, 40);
    return () => clearInterval(interval);
  }, [isActive, lines.length]);

  return (
    <pre className="p-6 overflow-x-auto">
      <code className="text-[12px] leading-[1.8] font-mono whitespace-pre">
        {lines.map((line, i) => (
          <motion.div
            key={i}
            initial={{ opacity: 0 }}
            animate={{ opacity: i < displayedLines ? 1 : 0 }}
            transition={{ duration: 0.15 }}
            style={{ color: "rgba(255,255,255,0.45)" }}
          >
            {line || "\u00A0"}
          </motion.div>
        ))}
      </code>
    </pre>
  );
}

export default function TechnicalDeep() {
  const [activeTab, setActiveTab] = useState("identity");
  const titleRef = useRef(null);
  const isInView = useInView(titleRef, { once: true, margin: "-80px" });

  const active = tabs.find((t) => t.id === activeTab)!;

  return (
    <section id="technology" className="py-36 px-6" style={{ backgroundColor: "#f5f5f7" }}>
      <div className="max-w-[1120px] mx-auto">
        <motion.div
          ref={titleRef}
          initial={{ opacity: 0, y: 30 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.8, ease }}
          className="text-center max-w-2xl mx-auto mb-16"
        >
          <span
            className="text-[12px] font-medium tracking-[0.2em] uppercase"
            style={{ color: "#86868b" }}
          >
            Under the Hood
          </span>
          <h2
            className="mt-4 text-[clamp(2rem,4vw,3.2rem)] font-semibold tracking-[-0.035em] leading-[1.08]"
            style={{ color: "#1d1d1f" }}
          >
            Built for engineers.
            <br />
            <span style={{ color: "#86868b" }}>Designed for everyone.</span>
          </h2>
        </motion.div>

        {/* Tab switcher */}
        <div className="flex justify-center mb-14">
          <div
            className="inline-flex rounded-full p-[3px]"
            style={{
              backgroundColor: "rgba(255,255,255,0.8)",
              border: "1px solid rgba(0,0,0,0.04)",
              boxShadow: "0 1px 3px rgba(0,0,0,0.04)",
            }}
          >
            {tabs.map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className="relative px-6 py-2.5 rounded-full text-[13px] font-medium transition-colors duration-300"
                style={{
                  color: activeTab === tab.id ? "#fff" : "#86868b",
                }}
              >
                {activeTab === tab.id && (
                  <motion.div
                    layoutId="activeTab"
                    className="absolute inset-0 rounded-full"
                    style={{ backgroundColor: "#1d1d1f" }}
                    transition={{ type: "spring", bounce: 0.12, duration: 0.5 }}
                  />
                )}
                <span className="relative z-10">{tab.label}</span>
              </button>
            ))}
          </div>
        </div>

        {/* Content */}
        <AnimatePresence mode="wait">
          <motion.div
            key={activeTab}
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.35, ease }}
            className="grid lg:grid-cols-2 gap-10"
          >
            {/* Left: description */}
            <div className="space-y-6">
              <h3
                className="text-[22px] font-semibold tracking-[-0.02em] leading-[1.2]"
                style={{ color: "#1d1d1f" }}
              >
                {active.content.title}
              </h3>
              <p
                className="text-[15px] leading-[1.75]"
                style={{ color: "#86868b" }}
              >
                {active.content.description}
              </p>

              <div className="space-y-4 pt-4">
                {active.content.details.map((detail, i) => (
                  <motion.div
                    key={i}
                    initial={{ opacity: 0, x: -10 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ duration: 0.4, delay: i * 0.06, ease }}
                    className="flex gap-5"
                  >
                    <span
                      className="text-[12px] font-medium w-32 flex-shrink-0 pt-0.5 tracking-wide"
                      style={{ color: "#aeaeb2" }}
                    >
                      {detail.label}
                    </span>
                    <span className="text-[14px] leading-[1.5]" style={{ color: "#86868b" }}>
                      {detail.value}
                    </span>
                  </motion.div>
                ))}
              </div>
            </div>

            {/* Right: code */}
            <div className="relative">
              <div className="sticky top-24">
                <div
                  className="rounded-2xl overflow-hidden"
                  style={{
                    backgroundColor: "#0a0a0a",
                    border: "1px solid rgba(255,255,255,0.06)",
                    boxShadow: "0 25px 60px -12px rgba(0,0,0,0.3)",
                  }}
                >
                  <div
                    className="flex items-center gap-2 px-5 py-3.5"
                    style={{ borderBottom: "1px solid rgba(255,255,255,0.06)" }}
                  >
                    <div className="flex gap-1.5">
                      <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: "rgba(255,255,255,0.08)" }} />
                      <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: "rgba(255,255,255,0.08)" }} />
                      <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: "rgba(255,255,255,0.08)" }} />
                    </div>
                    <span className="ml-3 text-[11px] font-mono" style={{ color: "rgba(255,255,255,0.18)" }}>
                      {active.content.filename}
                    </span>
                  </div>
                  <TypewriterCode code={active.content.code} isActive={true} />
                </div>
              </div>
            </div>
          </motion.div>
        </AnimatePresence>
      </div>
    </section>
  );
}
