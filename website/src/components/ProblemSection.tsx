"use client";

import { motion, useInView, useMotionValue, useSpring } from "framer-motion";
import { useRef, useCallback } from "react";

const ease = [0.25, 0.46, 0.45, 0.94] as const;

const problems = [
  {
    number: "01",
    title: "Pull payments are a liability",
    description:
      "In pull systems — cards, direct debits, tokenized card vaulting — the payer provides a long-lived capability that can be replayed, stolen, or used beyond the intended context. You hand over withdrawal authority and hope for the best.",
    accent: "Fraud, subscription traps, credential compromise.",
  },
  {
    number: "02",
    title: "Users sign what they can't read",
    description:
      "Wallets require users to approve transactions described in function selectors, byte arrays, and object IDs. The mapping to user intent is not always clear or verifiable — you authorize actions you cannot reason about.",
    accent: "A usability failure that becomes a security failure.",
  },
  {
    number: "03",
    title: "Payments lose context",
    description:
      "Receipts, warranties, invoices, tax metadata, and proof of ownership are generated off-ledger and stored on disconnected systems. Value transfer is recorded — the semantics of the exchange are not.",
    accent: "Your bank statement encodes \u2212$100 but not the verifiable who/what/why.",
  },
];

function ProblemCard({
  problem,
  index,
}: {
  problem: (typeof problems)[0];
  index: number;
}) {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-100px" });

  const mouseX = useMotionValue(0);
  const mouseY = useMotionValue(0);
  const springX = useSpring(mouseX, { stiffness: 300, damping: 30 });
  const springY = useSpring(mouseY, { stiffness: 300, damping: 30 });

  const handleMouseMove = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      const rect = e.currentTarget.getBoundingClientRect();
      mouseX.set(e.clientX - rect.left);
      mouseY.set(e.clientY - rect.top);
    },
    [mouseX, mouseY]
  );

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
        className="relative h-full flex flex-col p-8 lg:p-10 rounded-3xl card-hover overflow-hidden"
        style={{
          backgroundColor: "#fff",
          border: "1px solid rgba(0,0,0,0.06)",
        }}
        onMouseMove={handleMouseMove}
      >
        {/* Spotlight effect on hover */}
        <motion.div
          className="absolute inset-0 opacity-0 group-hover:opacity-100 transition-opacity duration-500 pointer-events-none"
          style={{
            background: `radial-gradient(300px circle at var(--mouse-x, 50%) var(--mouse-y, 50%), rgba(0,0,0,0.03), transparent 60%)`,
          }}
        />

        <span
          className="text-[12px] font-mono font-medium tracking-widest"
          style={{ color: "#d2d2d7" }}
        >
          {problem.number}
        </span>

        <h3
          className="mt-5 text-[22px] font-semibold tracking-[-0.025em] leading-[1.2]"
          style={{ color: "#1d1d1f" }}
        >
          {problem.title}
        </h3>

        <p
          className="mt-4 text-[15px] leading-[1.7]"
          style={{ color: "#86868b" }}
        >
          {problem.description}
        </p>

        <div
          className="mt-auto pt-6"
          style={{ borderTop: "1px solid rgba(0,0,0,0.04)" }}
        >
          <p
            className="text-[13px] font-medium leading-[1.5]"
            style={{ color: "#aeaeb2" }}
          >
            {problem.accent}
          </p>
        </div>
      </div>
    </motion.div>
  );
}

export default function ProblemSection() {
  const titleRef = useRef(null);
  const isInView = useInView(titleRef, { once: true, margin: "-100px" });

  return (
    <section className="relative py-36 px-6" style={{ backgroundColor: "#f5f5f7" }}>
      <div className="max-w-[1120px] mx-auto">
        <motion.div
          ref={titleRef}
          initial={{ opacity: 0, y: 30 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.8, ease }}
          className="text-center max-w-2xl mx-auto mb-20"
        >
          <motion.span
            initial={{ opacity: 0, y: 10 }}
            animate={isInView ? { opacity: 1, y: 0 } : {}}
            transition={{ duration: 0.6, ease }}
            className="text-[12px] font-medium tracking-[0.2em] uppercase"
            style={{ color: "#86868b" }}
          >
            The Problem
          </motion.span>
          <h2
            className="mt-4 text-[clamp(2rem,4vw,3.2rem)] font-semibold tracking-[-0.035em] leading-[1.08]"
            style={{ color: "#1d1d1f" }}
          >
            Payments weren&apos;t designed
            <br />
            <span style={{ color: "#86868b" }}>for the internet.</span>
          </h2>
        </motion.div>

        <div className="grid md:grid-cols-3 gap-5">
          {problems.map((problem, i) => (
            <ProblemCard key={i} problem={problem} index={i} />
          ))}
        </div>
      </div>
    </section>
  );
}
