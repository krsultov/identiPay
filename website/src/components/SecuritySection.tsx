"use client";

import { motion, useInView } from "framer-motion";
import { useRef, useState } from "react";

const ease = [0.25, 0.46, 0.45, 0.94] as const;

const mitigations = [
  {
    threat: "Malicious merchant",
    mitigation: "No pull authority \u2014 merchants cannot charge; only accepted intents execute. Overcharge and replay are structurally impossible.",
  },
  {
    threat: "Phisher / malware",
    mitigation: "Typed intent signing \u2014 signatures bind to canonicalized, human-readable proposal hashes. Blind signing is eliminated by design.",
  },
  {
    threat: "Missing deliverables",
    mitigation: "Atomic swap \u2014 delivery is enforced by PTB-level invariants. Payment without warranty/receipt is impossible.",
  },
  {
    threat: "Observer / correlator",
    mitigation: "Stealth addresses \u2014 names resolve to meta-addresses, not on-chain addresses; every transaction uses a fresh stealth address; predicates are proven without attribute disclosure.",
  },
  {
    threat: "Insider tampering",
    mitigation: "On-chain commerce artifacts are immutable post-settlement. Receipts, warranties, and accounting metadata cannot be altered.",
  },
  {
    threat: "Replay attacks",
    mitigation: "Expiry and nonce binding prevent reuse of signed intents across sessions or merchants.",
  },
];

function MitigationRow({
  item,
  index,
}: {
  item: (typeof mitigations)[0];
  index: number;
}) {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-40px" });
  const [hovered, setHovered] = useState(false);

  return (
    <motion.div
      ref={ref}
      initial={{ opacity: 0, x: -16 }}
      animate={isInView ? { opacity: 1, x: 0 } : {}}
      transition={{
        duration: 0.6,
        delay: index * 0.08,
        ease,
      }}
    >
      <div
        className="flex flex-col sm:flex-row gap-2 sm:gap-8 py-5 px-6 rounded-2xl transition-all duration-300"
        style={{
          backgroundColor: hovered ? "#fff" : "transparent",
          boxShadow: hovered ? "0 2px 12px rgba(0,0,0,0.04)" : "none",
        }}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      >
        <div className="sm:w-52 flex-shrink-0">
          <span className="text-[13px] font-medium" style={{ color: "#48484a" }}>
            {item.threat}
          </span>
        </div>
        <div className="flex items-start gap-3 flex-1">
          <motion.svg
            width="14" height="14" viewBox="0 0 14 14" fill="none"
            className="mt-[3px] flex-shrink-0"
            animate={{ scale: hovered ? 1.1 : 1 }}
            transition={{ duration: 0.2 }}
          >
            <motion.path
              d="M3 7l3 3 5-5"
              stroke="#30d158"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              initial={{ pathLength: 0 }}
              animate={isInView ? { pathLength: 1 } : {}}
              transition={{ delay: 0.3 + index * 0.1, duration: 0.4, ease }}
            />
          </motion.svg>
          <span className="text-[14px] leading-[1.65]" style={{ color: "#86868b" }}>
            {item.mitigation}
          </span>
        </div>
      </div>
    </motion.div>
  );
}

export default function SecuritySection() {
  const titleRef = useRef(null);
  const isInView = useInView(titleRef, { once: true, margin: "-80px" });

  return (
    <section id="security" className="py-36 px-6" style={{ backgroundColor: "#f5f5f7" }}>
      <div className="max-w-[900px] mx-auto">
        <motion.div
          ref={titleRef}
          initial={{ opacity: 0, y: 30 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.8, ease }}
          className="text-center mb-16"
        >
          <span
            className="text-[12px] font-medium tracking-[0.2em] uppercase"
            style={{ color: "#86868b" }}
          >
            Security
          </span>
          <h2
            className="mt-4 text-[clamp(2rem,4vw,3.2rem)] font-semibold tracking-[-0.035em] leading-[1.08]"
            style={{ color: "#1d1d1f" }}
          >
            Threat model.
            <br />
            <span style={{ color: "#86868b" }}>Protocol mitigations.</span>
          </h2>
        </motion.div>

        <div className="space-y-1">
          {mitigations.map((item, i) => (
            <MitigationRow key={i} item={item} index={i} />
          ))}
        </div>

        {/* Residual risks */}
        <motion.div
          initial={{ opacity: 0, y: 15 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-40px" }}
          transition={{ duration: 0.6, delay: 0.3, ease }}
          className="mt-10"
        >
          <div
            className="rounded-2xl px-6 py-5"
            style={{
              backgroundColor: "rgba(255,255,255,0.6)",
              border: "1px solid rgba(0,0,0,0.04)",
            }}
          >
            <span
              className="text-[11px] font-mono font-medium tracking-[0.1em] uppercase"
              style={{ color: "#aeaeb2" }}
            >
              Residual Risks
            </span>
            <p
              className="mt-2 text-[13px] leading-[1.7]"
              style={{ color: "#86868b" }}
            >
              Wallet compromise remains a critical risk &mdash; binding to
              EUDI-aligned credentials strengthens recovery but does not prevent
              an attacker who controls signing keys. Off-chain off-ramp processes
              introduce regulated counterparty risk and are intentionally out of
              protocol scope.
            </p>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
