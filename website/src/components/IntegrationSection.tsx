"use client";

import { motion, useInView } from "framer-motion";
import { useRef, useState } from "react";

const ease = [0.25, 0.46, 0.45, 0.94] as const;

const integrationSteps = [
  {
    step: "1",
    title: "Frontend Plugin",
    description:
      "Intercepts checkout. Generates a typed proposal. Displays QR or NFC handoff to the customer\u2019s wallet.",
  },
  {
    step: "2",
    title: "Backend Listener",
    description:
      "Monitors Sui for atomic settlement events keyed by intent hash. Confirms fulfillment in real-time.",
  },
  {
    step: "3",
    title: "Settlement",
    description:
      "Receives USDC/EURC on-chain. Optional automatic off-ramp via regulated partners \u2014 outside protocol scope.",
  },
];

function IntegrationStep({
  item,
  index,
}: {
  item: (typeof integrationSteps)[0];
  index: number;
}) {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-40px" });
  const [hovered, setHovered] = useState(false);

  return (
    <motion.div
      ref={ref}
      initial={{ opacity: 0, y: 30 }}
      animate={isInView ? { opacity: 1, y: 0 } : {}}
      transition={{ duration: 0.7, delay: index * 0.12, ease }}
      className="text-center"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <motion.div
        className="w-11 h-11 rounded-full flex items-center justify-center mx-auto mb-5 transition-all duration-300"
        style={{
          backgroundColor: hovered ? "#1d1d1f" : "rgba(0,0,0,0.04)",
        }}
        animate={{ scale: hovered ? 1.1 : 1 }}
        transition={{ duration: 0.3 }}
      >
        <span
          className="text-[13px] font-semibold transition-colors duration-300"
          style={{ color: hovered ? "#fff" : "#86868b" }}
        >
          {item.step}
        </span>
      </motion.div>
      <h3
        className="text-[16px] font-semibold tracking-[-0.01em] mb-2 leading-[1.3]"
        style={{ color: "#1d1d1f" }}
      >
        {item.title}
      </h3>
      <p className="text-[13px] leading-[1.7]" style={{ color: "#86868b" }}>
        {item.description}
      </p>
    </motion.div>
  );
}

export default function IntegrationSection() {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-80px" });

  return (
    <section className="py-36 px-6" style={{ backgroundColor: "#f5f5f7" }}>
      <div className="max-w-[1120px] mx-auto">
        <motion.div
          ref={ref}
          initial={{ opacity: 0, y: 30 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.8, ease }}
          className="max-w-3xl mx-auto text-center"
        >
          <span
            className="text-[12px] font-medium tracking-[0.2em] uppercase"
            style={{ color: "#86868b" }}
          >
            Integration
          </span>
          <h2
            className="mt-4 text-[clamp(2rem,4vw,3.2rem)] font-semibold tracking-[-0.035em] leading-[1.08]"
            style={{ color: "#1d1d1f" }}
          >
            Web2 in the front.
            <br />
            <span style={{ color: "#86868b" }}>Web3 in the back.</span>
          </h2>
          <p
            className="mt-5 text-[16px] leading-[1.7] max-w-lg mx-auto"
            style={{ color: "#86868b" }}
          >
            Adoption shouldn&apos;t require merchants to rewrite their commerce
            stack. identiPay integrates at the boundary.
          </p>
        </motion.div>

        <div className="mt-16 grid md:grid-cols-3 gap-8 max-w-3xl mx-auto">
          {integrationSteps.map((item, i) => (
            <IntegrationStep key={i} item={item} index={i} />
          ))}
        </div>

        {/* Sui-First MVP callout */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-40px" }}
          transition={{ duration: 0.7, delay: 0.2, ease }}
          className="mt-12 max-w-3xl mx-auto"
        >
          <div
            className="rounded-2xl p-6 lg:p-8"
            style={{
              backgroundColor: "rgba(255,255,255,0.8)",
              border: "1px solid rgba(0,0,0,0.06)",
              boxShadow: "0 1px 3px rgba(0,0,0,0.02)",
            }}
          >
            <div className="flex items-center gap-2 mb-3">
              <span
                className="text-[10px] font-medium tracking-[0.1em] uppercase px-2.5 py-1 rounded-full"
                style={{
                  color: "#30d158",
                  backgroundColor: "rgba(48,209,88,0.08)",
                }}
              >
                MVP
              </span>
              <span
                className="text-[12px] font-semibold tracking-[-0.01em]"
                style={{ color: "#1d1d1f" }}
              >
                Sui-First Settlement
              </span>
            </div>
            <p
              className="text-[14px] leading-[1.7]"
              style={{ color: "#86868b" }}
            >
              The Minimum Viable Product is natively anchored on the Sui Blockchain
              utilizing the USDC Corridor. A minimal{" "}
              <code className="text-[12px] font-mono px-1.5 py-0.5 rounded" style={{ backgroundColor: "rgba(0,0,0,0.04)", color: "#48484a" }}>
                identipay::settlement
              </code>{" "}
              Move module accepts a{" "}
              <code className="text-[12px] font-mono px-1.5 py-0.5 rounded" style={{ backgroundColor: "rgba(0,0,0,0.04)", color: "#48484a" }}>
                Coin&lt;USDC&gt;
              </code>
              , transfers it to the merchant, and atomically mints a{" "}
              <code className="text-[12px] font-mono px-1.5 py-0.5 rounded" style={{ backgroundColor: "rgba(0,0,0,0.04)", color: "#48484a" }}>
                ReceiptObject
              </code>{" "}
              delivered to the buyer&apos;s one-time stealth address &mdash; unlinkable to their registered name.
            </p>
          </div>
        </motion.div>
      </div>
    </section>
  );
}
