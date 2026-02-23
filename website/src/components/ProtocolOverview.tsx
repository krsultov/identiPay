"use client";

import { motion, useInView } from "framer-motion";
import { useRef, useState } from "react";

const ease = [0.25, 0.46, 0.45, 0.94] as const;

const pillars = [
  {
    icon: (hovered: boolean) => (
      <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
        <motion.circle
          cx="18" cy="13" r="5.5"
          stroke="currentColor" strokeWidth="1.5"
          initial={{ pathLength: 0 }}
          animate={{ pathLength: hovered ? 1 : 0.7 }}
          transition={{ duration: 0.6 }}
        />
        <motion.path
          d="M9 32c0-5 4-9 9-9s9 4 9 9"
          stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"
          initial={{ pathLength: 0 }}
          animate={{ pathLength: hovered ? 1 : 0.7 }}
          transition={{ duration: 0.6, delay: 0.1 }}
        />
        <motion.path
          d="M25 9l2-2M11 9L9 7M18 4V2"
          stroke="currentColor" strokeWidth="1.2" strokeLinecap="round"
          animate={{ opacity: hovered ? 1 : 0.3 }}
          transition={{ duration: 0.3 }}
        />
      </svg>
    ),
    label: "Stealth Identity",
    title: "Invisible by design.",
    description:
      "Register a human-readable name that resolves to a stealth meta-address. Every payment derives a fresh one-time address only you can detect — no address reuse, no on-chain identity leaks.",
    features: [
      "Human-readable names → stealth meta-addresses",
      "One-time stealth address per payment (ECDH)",
      "ZK proofs for eligibility — no PII on-chain",
      "Anti-Sybil: one credential = one name",
    ],
  },
  {
    icon: (hovered: boolean) => (
      <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
        <motion.path
          d="M6 18h24"
          stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"
          animate={{ pathLength: hovered ? 1 : 0.6 }}
          transition={{ duration: 0.5 }}
        />
        <motion.path
          d="M24 12l6 6-6 6"
          stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"
          animate={{ x: hovered ? 2 : 0, opacity: hovered ? 1 : 0.7 }}
          transition={{ duration: 0.3 }}
        />
        <motion.rect
          x="4" y="8" width="9" height="9" rx="2"
          stroke="currentColor" strokeWidth="1.2"
          animate={{ opacity: hovered ? 0.8 : 0.25 }}
          transition={{ duration: 0.3 }}
        />
      </svg>
    ),
    label: "Push-Only Intent",
    title: "You approve. Always.",
    description:
      "A typed proposal/acceptance model where merchants propose, wallets accept, and on-chain execution is constrained by the signed intent. No one obtains standing withdrawal authority.",
    features: [
      "Typed, human-auditable proposals",
      "Semantic intent binding",
      "Unilateral revocation / kill switch",
      "No standing withdrawal authority",
    ],
  },
  {
    icon: (hovered: boolean) => (
      <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
        <motion.rect
          x="5" y="5" width="11" height="11" rx="2.5"
          stroke="currentColor" strokeWidth="1.5"
          animate={{ scale: hovered ? 1.05 : 1 }}
          transition={{ duration: 0.3 }}
          style={{ transformOrigin: "10.5px 10.5px" }}
        />
        <motion.rect
          x="20" y="5" width="11" height="11" rx="2.5"
          stroke="currentColor" strokeWidth="1.5"
          animate={{ scale: hovered ? 1.05 : 1 }}
          transition={{ duration: 0.3, delay: 0.05 }}
          style={{ transformOrigin: "25.5px 10.5px" }}
        />
        <motion.rect
          x="5" y="20" width="11" height="11" rx="2.5"
          stroke="currentColor" strokeWidth="1.5"
          animate={{ scale: hovered ? 1.05 : 1 }}
          transition={{ duration: 0.3, delay: 0.1 }}
          style={{ transformOrigin: "10.5px 25.5px" }}
        />
        <motion.rect
          x="20" y="20" width="11" height="11" rx="2.5"
          stroke="currentColor" strokeWidth="1.2"
          animate={{ opacity: hovered ? 0.6 : 0.2 }}
          transition={{ duration: 0.3 }}
        />
        <motion.path
          d="M16 10.5h4M10.5 16v4"
          stroke="currentColor" strokeWidth="1.2" strokeLinecap="round"
          animate={{ opacity: hovered ? 0.8 : 0.3 }}
          transition={{ duration: 0.3 }}
        />
      </svg>
    ),
    label: "Atomic Settlement",
    title: "All or nothing.",
    description:
      "PTB-based execution on Sui where value transfer and commerce artifacts exchange atomically. Payment, receipts, warranties, and compliance data settle together — or nothing moves.",
    features: [
      "Sui Programmable Transaction Blocks",
      "Commerce artifacts as first-class objects",
      "Deterministic failure modes",
      "Composable compliance",
    ],
  },
];

function PillarCard({
  pillar,
  index,
}: {
  pillar: (typeof pillars)[0];
  index: number;
}) {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-80px" });
  const [hovered, setHovered] = useState(false);

  return (
    <motion.div
      ref={ref}
      initial={{ opacity: 0, y: 50 }}
      animate={isInView ? { opacity: 1, y: 0 } : {}}
      transition={{
        duration: 0.8,
        delay: index * 0.15,
        ease,
      }}
      className="group relative"
    >
      <div
        className="h-full flex flex-col p-8 lg:p-10 rounded-3xl transition-all duration-500"
        style={{
          backgroundColor: hovered ? "#fff" : "rgba(255,255,255,0.5)",
          border: hovered ? "1px solid rgba(0,0,0,0.08)" : "1px solid rgba(0,0,0,0.04)",
          boxShadow: hovered
            ? "0 20px 60px rgba(0,0,0,0.06), 0 1px 3px rgba(0,0,0,0.03)"
            : "none",
          transform: hovered ? "translateY(-4px)" : "translateY(0)",
        }}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      >
        <div className="flex items-center gap-3 mb-7">
          <div
            className="transition-colors duration-300"
            style={{ color: hovered ? "#1d1d1f" : "#86868b" }}
          >
            {pillar.icon(hovered)}
          </div>
          <span
            className="text-[11px] font-mono font-medium tracking-[0.15em] uppercase"
            style={{ color: "#aeaeb2" }}
          >
            {pillar.label}
          </span>
        </div>

        <h3
          className="text-[24px] font-semibold tracking-[-0.03em] leading-[1.15]"
          style={{ color: "#1d1d1f" }}
        >
          {pillar.title}
        </h3>
        <p
          className="mt-4 text-[15px] leading-[1.7]"
          style={{ color: "#86868b" }}
        >
          {pillar.description}
        </p>

        <ul className="mt-auto pt-7 space-y-3">
          {pillar.features.map((feature, i) => (
            <motion.li
              key={i}
              className="flex items-start gap-3"
              initial={{ opacity: 0.5 }}
              animate={{ opacity: hovered ? 1 : 0.7 }}
              transition={{ duration: 0.3, delay: i * 0.03 }}
            >
              <div
                className="w-[3px] h-[3px] rounded-full mt-2 flex-shrink-0 transition-colors duration-300"
                style={{ backgroundColor: hovered ? "#1d1d1f" : "#d2d2d7" }}
              />
              <span
                className="text-[13px] leading-[1.5]"
                style={{ color: "#86868b" }}
              >
                {feature}
              </span>
            </motion.li>
          ))}
        </ul>
      </div>
    </motion.div>
  );
}

export default function ProtocolOverview() {
  const titleRef = useRef(null);
  const isInView = useInView(titleRef, { once: true, margin: "-80px" });

  return (
    <section id="protocol" className="py-36 px-6">
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
            Three Pillars
          </span>
          <h2
            className="mt-4 text-[clamp(2rem,4vw,3.2rem)] font-semibold tracking-[-0.035em] leading-[1.08]"
            style={{ color: "#1d1d1f" }}
          >
            A protocol designed from
            <br />
            <span style={{ color: "#86868b" }}>first principles.</span>
          </h2>
          <p
            className="mt-5 text-[16px] leading-[1.7] max-w-lg mx-auto"
            style={{ color: "#86868b" }}
          >
            Zero-knowledge identity. Push-only payments. Atomic settlement.
            Three layers that compose into a single, verifiable commerce primitive.
            The protocol is intentionally deterministic: the same intent and the same
            on-chain state yield the same execution.
          </p>
        </motion.div>

        <div className="grid lg:grid-cols-3 gap-5">
          {pillars.map((pillar, i) => (
            <PillarCard key={i} pillar={pillar} index={i} />
          ))}
        </div>
      </div>
    </section>
  );
}
