"use client";

import { motion, useInView } from "framer-motion";
import { useRef, useState } from "react";

const ease = [0.25, 0.46, 0.45, 0.94] as const;

const useCases = [
  {
    title: "Subscription Kill Switch",
    legacy: "Cancellation is a human process. Pull authority remains until a merchant updates its internal state. You hope they comply.",
    identipay: "Subscriptions are modeled as scoped delegate capabilities or recurring proposals. Revoke the delegate key or remove an allow-list policy — subsequent proposals fail on-chain deterministically.",
    icon: (
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="11" width="18" height="10" rx="2" />
        <circle cx="12" cy="16" r="1" />
        <path d="M7 11V7a5 5 0 0110 0v4" />
      </svg>
    ),
  },
  {
    title: "Self-Enforcing Warranty",
    legacy: "Paper receipts. Warranty eligibility is discretionary and error-prone. Receipts get lost. Proof of purchase lives in a drawer.",
    identipay: "At a service desk, the wallet proves ownership of the warranty object and eligibility predicates. Repair authorization is a function of object ownership and terms — not paper receipts.",
    icon: (
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
        <path d="M9 12l2 2 4-4" />
      </svg>
    ),
  },
  {
    title: "Corporate Compliance",
    legacy: "Manually typed receipt data. Disconnected payment and ERP systems. Error-prone reconciliation across departments.",
    identipay: "Transactions carry structured metadata — VAT category, merchant identifier, cost center — as commitments referenced by on-chain events. Indexers bridge into ERP with deterministic reconciliation keyed by intent hash.",
    icon: (
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
        <polyline points="14 2 14 8 20 8" />
        <line x1="16" y1="13" x2="8" y2="13" />
        <line x1="16" y1="17" x2="8" y2="17" />
      </svg>
    ),
  },
];

function UseCaseCard({
  useCase,
  index,
}: {
  useCase: (typeof useCases)[0];
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
      className="group"
    >
      <div
        className="h-full flex flex-col p-8 lg:p-10 rounded-3xl transition-all duration-500"
        style={{
          backgroundColor: "#fff",
          border: hovered ? "1px solid rgba(0,0,0,0.08)" : "1px solid rgba(0,0,0,0.04)",
          boxShadow: hovered
            ? "0 20px 60px rgba(0,0,0,0.06), 0 1px 3px rgba(0,0,0,0.03)"
            : "none",
          transform: hovered ? "translateY(-4px)" : "translateY(0)",
        }}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      >
        <div
          className="transition-colors duration-300 mb-6"
          style={{ color: hovered ? "#1d1d1f" : "#aeaeb2" }}
        >
          {useCase.icon}
        </div>

        <h3
          className="text-[20px] font-semibold tracking-[-0.02em] mb-6 leading-[1.2]"
          style={{ color: "#1d1d1f" }}
        >
          {useCase.title}
        </h3>

        <div className="mt-auto pt-6 space-y-5">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: "#ff453a" }} />
              <span className="text-[11px] font-medium tracking-[0.15em] uppercase" style={{ color: "#aeaeb2" }}>
                Legacy
              </span>
            </div>
            <p className="text-[13px] leading-[1.65] pl-3.5" style={{ color: "#aeaeb2" }}>
              {useCase.legacy}
            </p>
          </div>

          <div
            className="pt-5"
            style={{ borderTop: "1px solid rgba(0,0,0,0.04)" }}
          >
            <div className="flex items-center gap-2 mb-2">
              <div className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: "#30d158" }} />
              <span className="text-[11px] font-medium tracking-[0.15em] uppercase" style={{ color: "#aeaeb2" }}>
                identiPay
              </span>
            </div>
            <p className="text-[13px] leading-[1.65] pl-3.5" style={{ color: "#86868b" }}>
              {useCase.identipay}
            </p>
          </div>
        </div>
      </div>
    </motion.div>
  );
}

export default function UseCases() {
  const titleRef = useRef(null);
  const isInView = useInView(titleRef, { once: true, margin: "-80px" });

  return (
    <section id="usecases" className="py-36 px-6" style={{ backgroundColor: "#fff" }}>
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
            Use Cases
          </span>
          <h2
            className="mt-4 text-[clamp(2rem,4vw,3.2rem)] font-semibold tracking-[-0.035em] leading-[1.08]"
            style={{ color: "#1d1d1f" }}
          >
            Real problems.
            <br />
            <span style={{ color: "#86868b" }}>Protocol-level solutions.</span>
          </h2>
        </motion.div>

        <div className="grid md:grid-cols-3 gap-5">
          {useCases.map((useCase, i) => (
            <UseCaseCard key={i} useCase={useCase} index={i} />
          ))}
        </div>
      </div>
    </section>
  );
}
