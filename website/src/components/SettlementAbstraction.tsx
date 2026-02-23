"use client";

import { motion, useInView } from "framer-motion";
import { useRef, useState } from "react";

const ease = [0.25, 0.46, 0.45, 0.94] as const;

const phases = [
  {
    phase: "Phase 1",
    title: "Native Sui Settlement",
    subtitle: "USDC-first",
    description:
      "The signed Universal Intent Object is wrapped into a Programmable Transaction Block and executed on Sui. USDC transfer and commerce artifact issuance occur in the same state transition — strict cryptographic atomicity with deterministic reconciliation keyed by intent hash.",
    badge: "Live",
    badgeColor: "#30d158",
    details: [
      "Coin<USDC> → ReceiptObject in one PTB",
      "Full cryptographic atomicity",
      "Deterministic reconciliation",
      "No third-party solvers required",
    ],
  },
  {
    phase: "Phase 2",
    title: "Solver Network",
    subtitle: "Cross-rail fulfillment",
    description:
      "A decentralized network of Solvers fulfills signed intents even when payer funds live elsewhere. The payer signs an intent with clear limits — amount, expiry, recipient, nonce. A Solver fronts on the merchant\u2019s preferred rail immediately, then settles back by claiming payer-authorized funds on the external rail.",
    badge: "Planned",
    badgeColor: "#86868b",
    details: [
      "Rail-agnostic intent fulfillment",
      "Economic atomicity via solver fronting",
      "Merchant sees one integration, one asset",
      "Solver absorbs cross-rail latency",
    ],
  },
];

function PhaseCard({
  phase,
  index,
}: {
  phase: (typeof phases)[0];
  index: number;
}) {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-60px" });
  const [hovered, setHovered] = useState(false);

  return (
    <motion.div
      ref={ref}
      initial={{ opacity: 0, y: 40 }}
      animate={isInView ? { opacity: 1, y: 0 } : {}}
      transition={{ duration: 0.8, delay: index * 0.15, ease }}
      className="group"
    >
      <div
        className="h-full p-8 lg:p-10 rounded-3xl transition-all duration-500"
        style={{
          backgroundColor: hovered ? "#fff" : "rgba(255,255,255,0.5)",
          border: hovered
            ? "1px solid rgba(0,0,0,0.08)"
            : "1px solid rgba(0,0,0,0.04)",
          boxShadow: hovered
            ? "0 20px 60px rgba(0,0,0,0.06), 0 1px 3px rgba(0,0,0,0.03)"
            : "none",
          transform: hovered ? "translateY(-4px)" : "translateY(0)",
        }}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      >
        <div className="flex items-center gap-3 mb-6">
          <span
            className="text-[11px] font-mono font-medium tracking-[0.15em] uppercase"
            style={{ color: "#aeaeb2" }}
          >
            {phase.phase}
          </span>
          <span
            className="text-[10px] font-medium tracking-[0.1em] uppercase px-2.5 py-1 rounded-full"
            style={{
              color: phase.badgeColor,
              backgroundColor:
                phase.badgeColor === "#30d158"
                  ? "rgba(48,209,88,0.08)"
                  : "rgba(134,134,139,0.08)",
            }}
          >
            {phase.badge}
          </span>
        </div>

        <h3
          className="text-[22px] font-semibold tracking-[-0.025em] leading-[1.2]"
          style={{ color: "#1d1d1f" }}
        >
          {phase.title}
        </h3>
        <p
          className="text-[13px] font-medium mt-1"
          style={{ color: "#aeaeb2" }}
        >
          {phase.subtitle}
        </p>

        <p
          className="mt-4 text-[14px] leading-[1.7]"
          style={{ color: "#86868b" }}
        >
          {phase.description}
        </p>

        <ul className="mt-6 space-y-3">
          {phase.details.map((detail, i) => (
            <motion.li
              key={i}
              className="flex items-start gap-3"
              initial={{ opacity: 0.5 }}
              animate={{ opacity: hovered ? 1 : 0.7 }}
              transition={{ duration: 0.3, delay: i * 0.03 }}
            >
              <div
                className="w-[3px] h-[3px] rounded-full mt-2 flex-shrink-0 transition-colors duration-300"
                style={{
                  backgroundColor: hovered ? "#1d1d1f" : "#d2d2d7",
                }}
              />
              <span
                className="text-[13px] leading-[1.5]"
                style={{ color: "#86868b" }}
              >
                {detail}
              </span>
            </motion.li>
          ))}
        </ul>
      </div>
    </motion.div>
  );
}

function UIODiagram() {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-40px" });

  return (
    <motion.div
      ref={ref}
      initial={{ opacity: 0, y: 30 }}
      animate={isInView ? { opacity: 1, y: 0 } : {}}
      transition={{ duration: 0.8, delay: 0.2, ease }}
      className="mt-16 max-w-2xl mx-auto"
    >
      <div
        className="rounded-3xl overflow-hidden"
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
            <div
              className="w-2.5 h-2.5 rounded-full"
              style={{ backgroundColor: "rgba(255,255,255,0.08)" }}
            />
            <div
              className="w-2.5 h-2.5 rounded-full"
              style={{ backgroundColor: "rgba(255,255,255,0.08)" }}
            />
            <div
              className="w-2.5 h-2.5 rounded-full"
              style={{ backgroundColor: "rgba(255,255,255,0.08)" }}
            />
          </div>
          <span
            className="ml-3 text-[11px] font-mono"
            style={{ color: "rgba(255,255,255,0.18)" }}
          >
            universal-intent-object.json
          </span>
        </div>
        <pre className="p-6 overflow-x-auto">
          <code
            className="text-[12px] leading-[1.8] font-mono whitespace-pre"
            style={{ color: "rgba(255,255,255,0.45)" }}
          >
            <div>
              <span style={{ color: "rgba(255,255,255,0.2)" }}>{"// "}</span>
              <span style={{ color: "rgba(255,255,255,0.25)" }}>
                Universal Intent Object (UIO)
              </span>
            </div>
            <div>
              <span style={{ color: "rgba(255,255,255,0.2)" }}>{"{"}</span>
            </div>
            <div className="pl-4">
              <span style={{ color: "rgba(255,255,255,0.35)" }}>
                &quot;@context&quot;
              </span>
              <span style={{ color: "rgba(255,255,255,0.15)" }}>: </span>
              <span style={{ color: "#60a5fa" }}>
                &quot;did:identipay&quot;
              </span>
              <span style={{ color: "rgba(255,255,255,0.15)" }}>,</span>
            </div>
            <div className="pl-4">
              <span style={{ color: "rgba(255,255,255,0.35)" }}>
                &quot;zk_proof&quot;
              </span>
              <span style={{ color: "rgba(255,255,255,0.15)" }}>: </span>
              <span style={{ color: "#34d399" }}>
                &quot;Groth16(age&gt;18 ∧ EU)&quot;
              </span>
              <span style={{ color: "rgba(255,255,255,0.15)" }}>,</span>
            </div>
            <div className="pl-4">
              <span style={{ color: "rgba(255,255,255,0.35)" }}>
                &quot;terms&quot;
              </span>
              <span style={{ color: "rgba(255,255,255,0.15)" }}>: </span>
              <span style={{ color: "#34d399" }}>
                &quot;50 EUR → Merchant DID&quot;
              </span>
              <span style={{ color: "rgba(255,255,255,0.15)" }}>,</span>
            </div>
            <div className="pl-4">
              <span style={{ color: "rgba(255,255,255,0.35)" }}>
                &quot;deliverable&quot;
              </span>
              <span style={{ color: "rgba(255,255,255,0.15)" }}>: </span>
              <span style={{ color: "#34d399" }}>
                &quot;Warranty #123&quot;
              </span>
              <span style={{ color: "rgba(255,255,255,0.15)" }}>,</span>
            </div>
            <div className="pl-4">
              <span style={{ color: "rgba(255,255,255,0.35)" }}>
                &quot;stealth_addr&quot;
              </span>
              <span style={{ color: "rgba(255,255,255,0.15)" }}>: </span>
              <span style={{ color: "#34d399" }}>
                &quot;0x7a2f...e4b1&quot;
              </span>
              <span style={{ color: "rgba(255,255,255,0.15)" }}>,</span>
            </div>
            <div className="pl-4">
              <span style={{ color: "rgba(255,255,255,0.35)" }}>
                &quot;rail&quot;
              </span>
              <span style={{ color: "rgba(255,255,255,0.15)" }}>: </span>
              <span style={{ color: "#f59e0b" }}>
                &quot;agnostic&quot;
              </span>
            </div>
            <div>
              <span style={{ color: "rgba(255,255,255,0.2)" }}>{"}"}</span>
            </div>
          </code>
        </pre>
      </div>
    </motion.div>
  );
}

export default function SettlementAbstraction() {
  const titleRef = useRef(null);
  const isInView = useInView(titleRef, { once: true, margin: "-80px" });

  return (
    <section
      id="abstraction"
      className="py-36 px-6"
      style={{ backgroundColor: "#f5f5f7" }}
    >
      <div className="max-w-[1120px] mx-auto">
        <motion.div
          ref={titleRef}
          initial={{ opacity: 0, y: 30 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.8, ease }}
          className="text-center max-w-2xl mx-auto mb-20"
        >
          <span
            className="text-[12px] font-medium tracking-[0.2em] uppercase"
            style={{ color: "#86868b" }}
          >
            Settlement Abstraction
          </span>
          <h2
            className="mt-4 text-[clamp(2rem,4vw,3.2rem)] font-semibold tracking-[-0.035em] leading-[1.08]"
            style={{ color: "#1d1d1f" }}
          >
            From pushing money
            <br />
            <span style={{ color: "#86868b" }}>to broadcasting intent.</span>
          </h2>
          <p
            className="mt-5 text-[16px] leading-[1.7] max-w-lg mx-auto"
            style={{ color: "#86868b" }}
          >
            Legacy payment networks tightly couple identity, authorization, and
            settlement. identiPay breaks this monolith with a Universal Intent
            Object that is rail-agnostic by design — the same signed intent can
            settle on any rail.
          </p>
        </motion.div>

        <div className="grid lg:grid-cols-2 gap-5">
          {phases.map((phase, i) => (
            <PhaseCard key={i} phase={phase} index={i} />
          ))}
        </div>

        <UIODiagram />

        {/* Atomicity spectrum note */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-40px" }}
          transition={{ duration: 0.7, delay: 0.3, ease }}
          className="mt-10 max-w-2xl mx-auto text-center"
        >
          <div
            className="rounded-2xl p-6"
            style={{
              backgroundColor: "rgba(255,255,255,0.6)",
              border: "1px solid rgba(0,0,0,0.04)",
            }}
          >
            <span
              className="text-[11px] font-mono font-medium tracking-[0.15em] uppercase"
              style={{ color: "#aeaeb2" }}
            >
              Atomicity Spectrum
            </span>
            <p
              className="mt-3 text-[14px] leading-[1.7]"
              style={{ color: "#86868b" }}
            >
              Strict cryptographic atomicity is guaranteed when both legs exist
              on the same ledger. When the protocol abstracts across
              asynchronous rails, Solvers provide{" "}
              <span style={{ color: "#1d1d1f" }} className="font-medium">
                economic atomicity
              </span>{" "}
              — fronting value instantly and absorbing cross-rail latency for a
              marginal spread.
            </p>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
