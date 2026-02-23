"use client";

import { motion, useInView } from "framer-motion";
import { useRef, useState } from "react";

const ease = [0.25, 0.46, 0.45, 0.94] as const;

/* ─── Step data ──────────────────────────────────────────────── */

const steps = [
  {
    step: "01",
    title: "Merchant proposes",
    description:
      "A typed, canonicalized proposal — item, price, terms, expiry. Every field is explicit.",
    accentColor: "#60a5fa",
    visual: (
      <div
        className="h-full font-mono text-[11px] leading-[1.9] rounded-xl p-5"
        style={{
          backgroundColor: "#0c0c0c",
          border: "1px solid rgba(255,255,255,0.06)",
        }}
      >
        <div style={{ color: "rgba(255,255,255,0.15)" }}>{"{"}</div>
        {[
          { key: "sku", val: '"laptop-pro-16"', color: "#34d399" },
          { key: "amount", val: '"1,299 USDC"', color: "#60a5fa" },
          { key: "warranty", val: '"24 months"', color: "#34d399" },
          { key: "merchant", val: '"did:idpay:..."', color: "#60a5fa" },
          { key: "expiry", val: '"10 min"', color: "#f59e0b" },
        ].map((line) => (
          <div key={line.key} className="pl-4">
            <span style={{ color: "rgba(255,255,255,0.3)" }}>
              &quot;{line.key}&quot;
            </span>
            <span style={{ color: "rgba(255,255,255,0.1)" }}>: </span>
            <span style={{ color: line.color }}>{line.val}</span>
            <span style={{ color: "rgba(255,255,255,0.08)" }}>,</span>
          </div>
        ))}
        <div style={{ color: "rgba(255,255,255,0.15)" }}>{"}"}</div>
      </div>
    ),
  },
  {
    step: "02",
    title: "Wallet translates",
    description:
      "Your wallet renders the proposal in plain language. You see exactly what you approve.",
    accentColor: "#a78bfa",
    visual: (
      <div
        className="h-full rounded-xl p-5"
        style={{
          backgroundColor: "#fafafa",
          border: "1px solid rgba(0,0,0,0.06)",
        }}
      >
        <div className="flex items-center gap-2 mb-3.5">
          <div className="relative flex h-2 w-2">
            <span
              className="absolute inline-flex h-full w-full rounded-full opacity-60 animate-ping"
              style={{ backgroundColor: "#22c55e" }}
            />
            <span
              className="relative inline-flex rounded-full h-2 w-2"
              style={{ backgroundColor: "#22c55e" }}
            />
          </div>
          <span
            className="text-[10px] font-semibold tracking-[0.12em] uppercase"
            style={{ color: "#22c55e" }}
          >
            Verified
          </span>
        </div>
        <p className="text-[13px] leading-[1.7]" style={{ color: "#48484a" }}>
          Send{" "}
          <span className="font-bold" style={{ color: "#1d1d1f" }}>
            1,299 USDC
          </span>{" "}
          to{" "}
          <span className="font-bold" style={{ color: "#1d1d1f" }}>
            TechStore
          </span>
          .
          <br />
          Receive warranty (24 mo) + receipt.
        </p>
        <div
          className="mt-3 pt-3 flex items-center justify-between text-[11px]"
          style={{
            borderTop: "1px solid rgba(0,0,0,0.05)",
            color: "#aeaeb2",
          }}
        >
          <span>Expires in 10 min</span>
          <span className="font-mono">⏱</span>
        </div>
      </div>
    ),
  },
  {
    step: "03",
    title: "You sign intent",
    description:
      "A domain-separated signature bound to the proposal hash and context — not opaque bytes.",
    accentColor: "#34d399",
    visual: (
      <div
        className="h-full rounded-xl p-5"
        style={{
          backgroundColor: "#0c0c0c",
          border: "1px solid rgba(255,255,255,0.06)",
        }}
      >
        <div className="flex items-center justify-between mb-3.5">
          <span
            className="text-[10px] font-mono font-semibold tracking-[0.1em] uppercase"
            style={{ color: "rgba(255,255,255,0.25)" }}
          >
            Intent Signature
          </span>
          <div className="flex gap-1">
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className="w-1.5 h-1.5 rounded-full"
                style={{ backgroundColor: "#34d399" }}
              />
            ))}
          </div>
        </div>
        <div
          className="font-mono text-[10px] break-all leading-[2]"
          style={{ color: "rgba(255,255,255,0.35)" }}
        >
          <span style={{ color: "rgba(255,255,255,0.55)" }}>0x7a2f...e4b1</span>{" "}
          → BCS(Proposal, Ctx, Nonce)
        </div>
        <div
          className="mt-2.5 pt-2.5 font-mono text-[10px]"
          style={{
            borderTop: "1px solid rgba(255,255,255,0.05)",
            color: "rgba(255,255,255,0.25)",
          }}
        >
          σ = Sign<sub>sk</sub>(Intent ‖ H(tx))
        </div>
      </div>
    ),
  },
  {
    step: "04",
    title: "Atomic settlement",
    description:
      "Value, encrypted artifacts, and compliance data settle in one indivisible transaction.",
    accentColor: "#f59e0b",
    visual: (
      <div
        className="h-full rounded-xl overflow-hidden"
        style={{
          backgroundColor: "#fff",
          border: "1px solid rgba(0,0,0,0.06)",
        }}
      >
        <div className="space-y-0">
          {[
            { label: "Payment", status: "SETTLED", color: "#34d399" },
            { label: "Warranty", status: "ISSUED", color: "#34d399" },
            { label: "Receipt", status: "ENCRYPTED", color: "#a78bfa" },
            { label: "ZK Proof", status: "VERIFIED", color: "#34d399" },
            { label: "Stealth Addr", status: "DERIVED", color: "#60a5fa" },
          ].map((item, i) => (
            <div
              key={item.label}
              className="flex items-center justify-between px-5 py-2.5"
              style={{
                backgroundColor: i % 2 === 0 ? "#fafafa" : "#fff",
              }}
            >
              <div className="flex items-center gap-2.5">
                <div
                  className="w-1.5 h-1.5 rounded-full"
                  style={{ backgroundColor: item.color }}
                />
                <span
                  className="text-[12px] font-medium"
                  style={{ color: "#48484a" }}
                >
                  {item.label}
                </span>
              </div>
              <span
                className="text-[9px] font-mono font-bold tracking-[0.1em]"
                style={{ color: item.color }}
              >
                {item.status}
              </span>
            </div>
          ))}
        </div>
      </div>
    ),
  },
];

/* ─── Step card ──────────────────────────────────────────────── */

function StepCard({
  step,
  index,
}: {
  step: (typeof steps)[0];
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
      transition={{
        duration: 0.8,
        delay: index * 0.12,
        ease,
      }}
      className="group relative grid rounded-2xl p-6 lg:p-7 transition-all duration-500"
      style={{
        gridRow: "span 3",
        gridTemplateRows: "subgrid",
        backgroundColor: hovered ? "#fff" : "rgba(255,255,255,0.6)",
        border: hovered
          ? "1px solid rgba(0,0,0,0.08)"
          : "1px solid rgba(0,0,0,0.04)",
        boxShadow: hovered
          ? "0 16px 48px rgba(0,0,0,0.06), 0 1px 3px rgba(0,0,0,0.02)"
          : "0 1px 2px rgba(0,0,0,0.02)",
        transform: hovered ? "translateY(-2px)" : "translateY(0)",
      }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      {/* Row 1: Step badge */}
      <div className="flex items-center gap-3 mb-5" style={{ gridRow: "1" }}>
        <div
          className="w-8 h-8 rounded-lg flex items-center justify-center text-[11px] font-mono font-bold transition-all duration-300"
          style={{
            backgroundColor: hovered
              ? `${step.accentColor}14`
              : "rgba(0,0,0,0.03)",
            color: hovered ? step.accentColor : "#aeaeb2",
            border: hovered
              ? `1px solid ${step.accentColor}25`
              : "1px solid rgba(0,0,0,0.04)",
          }}
        >
          {step.step}
        </div>
        <motion.div
          className="h-px flex-1"
          initial={{ scaleX: 0 }}
          animate={isInView ? { scaleX: 1 } : {}}
          transition={{ duration: 0.8, delay: 0.3 + index * 0.12, ease }}
          style={{
            background: hovered
              ? `linear-gradient(to right, ${step.accentColor}30, transparent)`
              : "linear-gradient(to right, rgba(0,0,0,0.06), transparent)",
            transformOrigin: "left",
          }}
        />
      </div>

      {/* Row 2: Title + description */}
      <div className="self-start" style={{ gridRow: "2" }}>
        <h3
          className="text-[17px] font-semibold tracking-[-0.02em] leading-[1.2]"
          style={{ color: "#1d1d1f" }}
        >
          {step.title}
        </h3>
        <p
          className="mt-2.5 text-[13px] leading-[1.65]"
          style={{ color: "#86868b" }}
        >
          {step.description}
        </p>
      </div>

      {/* Row 3: Visual */}
      <div
        className="h-full pt-5"
        style={{ gridRow: "3" }}
      >
        <motion.div
          className="h-full"
          initial={{ opacity: 0, y: 10 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.6, delay: 0.4 + index * 0.12, ease }}
        >
          {step.visual}
        </motion.div>
      </div>
    </motion.div>
  );
}

/* ─── Main component ─────────────────────────────────────────── */

export default function HowItWorks() {
  const titleRef = useRef(null);
  const isInView = useInView(titleRef, { once: true, margin: "-80px" });

  return (
    <section
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
            How It Works
          </span>
          <h2
            className="mt-4 text-[clamp(2rem,4vw,3.2rem)] font-semibold tracking-[-0.035em] leading-[1.08]"
            style={{ color: "#1d1d1f" }}
          >
            Four steps.
            <br />
            <span style={{ color: "#86868b" }}>Zero ambiguity.</span>
          </h2>
          <p
            className="mt-4 text-[15px] leading-[1.65]"
            style={{ color: "#aeaeb2" }}
          >
            From proposal to atomic settlement — every step is typed,
            human-auditable, and cryptographically bound.
          </p>
        </motion.div>

        <div
          className="grid md:grid-cols-2 lg:grid-cols-4 gap-4"
          style={{ gridTemplateRows: "auto 1fr auto" }}
        >
          {steps.map((step, i) => (
            <StepCard key={i} step={step} index={i} />
          ))}
        </div>
      </div>
    </section>
  );
}
