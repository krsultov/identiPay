"use client";

import { motion, useInView } from "framer-motion";
import { useRef } from "react";

const ease = [0.25, 0.46, 0.45, 0.94] as const;

const flowSteps = [
    {
        step: "01",
        label: "Scan",
        description: "Customer scans a QR code or taps NFC at checkout. The code encodes a compact URI pointer — not the full proposal.",
        mono: "did:identipay:<host>:<tx-id>",
    },
    {
        step: "02",
        label: "Resolve",
        description: "The wallet performs a deterministic HTTPS GET to fetch the full commercial proposal from the merchant's endpoint.",
        mono: "GET /api/identipay/v1/intents/<tx-id>",
    },
    {
        step: "03",
        label: "Verify",
        description: "Before signing, the wallet cross-references the resolved hostname against a decentralized Trust Registry maintained on the Sui blockchain.",
        mono: "TrustRegistry::verify(hostname) → ✓",
    },
];

function FlowStep({
    step,
    index,
}: {
    step: (typeof flowSteps)[0];
    index: number;
}) {
    const ref = useRef(null);
    const isInView = useInView(ref, { once: true, margin: "-40px" });

    return (
        <motion.div
            ref={ref}
            initial={{ opacity: 0, y: 30 }}
            animate={isInView ? { opacity: 1, y: 0 } : {}}
            transition={{ duration: 0.7, delay: index * 0.12, ease }}
        >
            <div className="relative">
                <div className="flex items-center gap-3 mb-4">
                    <span
                        className="text-[11px] font-mono font-medium tracking-[0.15em]"
                        style={{ color: "#d2d2d7" }}
                    >
                        {step.step}
                    </span>
                    <motion.div
                        className="h-px flex-1"
                        initial={{ scaleX: 0 }}
                        animate={isInView ? { scaleX: 1 } : {}}
                        transition={{
                            duration: 0.8,
                            delay: 0.3 + index * 0.12,
                            ease,
                        }}
                        style={{
                            background:
                                "linear-gradient(to right, rgba(0,0,0,0.08), transparent)",
                            transformOrigin: "left",
                        }}
                    />
                </div>

                <h3
                    className="text-[18px] font-semibold tracking-[-0.02em] leading-[1.2]"
                    style={{ color: "#1d1d1f" }}
                >
                    {step.label}
                </h3>
                <p
                    className="mt-3 text-[14px] leading-[1.65] mb-5"
                    style={{ color: "#86868b" }}
                >
                    {step.description}
                </p>

                <div
                    className="rounded-xl px-4 py-3"
                    style={{
                        backgroundColor: "#0a0a0a",
                        border: "1px solid rgba(255,255,255,0.06)",
                    }}
                >
                    <code
                        className="text-[11px] font-mono break-all leading-[1.6]"
                        style={{ color: "rgba(255,255,255,0.45)" }}
                    >
                        {step.mono}
                    </code>
                </div>
            </div>
        </motion.div>
    );
}

export default function FederatedRouting() {
    const titleRef = useRef(null);
    const isInView = useInView(titleRef, { once: true, margin: "-80px" });

    return (
        <section className="py-36 px-6" style={{ backgroundColor: "#fff" }}>
            <div className="max-w-[1120px] mx-auto">
                <motion.div
                    ref={titleRef}
                    initial={{ opacity: 0, y: 30 }}
                    animate={isInView ? { opacity: 1, y: 0 } : {}}
                    transition={{ duration: 0.8, ease }}
                    className="max-w-lg mb-20"
                >
                    <span
                        className="text-[12px] font-medium tracking-[0.2em] uppercase"
                        style={{ color: "#86868b" }}
                    >
                        Routing & Resolution
                    </span>
                    <h2
                        className="mt-4 text-[clamp(2rem,4vw,3.2rem)] font-semibold tracking-[-0.035em] leading-[1.08]"
                        style={{ color: "#1d1d1f" }}
                    >
                        One scan.
                        <br />
                        <span style={{ color: "#86868b" }}>Verified before you sign.</span>
                    </h2>
                    <p
                        className="mt-5 text-[15px] leading-[1.7]"
                        style={{ color: "#86868b" }}
                    >
                        Commercial proposals are too large for QR codes. identiPay uses a
                        federated URI scheme as a routing pointer — resolved over HTTPS
                        and verified against an on-chain Trust Registry before any intent
                        is signed.
                    </p>
                </motion.div>

                <div className="grid md:grid-cols-3 gap-10 lg:gap-8">
                    {flowSteps.map((step, i) => (
                        <FlowStep key={i} step={step} index={i} />
                    ))}
                </div>

                {/* URI schema callout */}
                <motion.div
                    initial={{ opacity: 0, y: 20 }}
                    whileInView={{ opacity: 1, y: 0 }}
                    viewport={{ once: true, margin: "-40px" }}
                    transition={{ duration: 0.7, delay: 0.2, ease }}
                    className="mt-16 max-w-2xl mx-auto"
                >
                    <div
                        className="rounded-2xl p-6"
                        style={{
                            backgroundColor: "#f5f5f7",
                            border: "1px solid rgba(0,0,0,0.04)",
                        }}
                    >
                        <div className="flex items-center gap-2 mb-3">
                            <svg
                                width="14"
                                height="14"
                                viewBox="0 0 14 14"
                                fill="none"
                                stroke="#aeaeb2"
                                strokeWidth="1.5"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                            >
                                <path d="M12 5V2H9" />
                                <path d="M2 9v3h3" />
                                <path d="M12 2L8 6" />
                                <path d="M6 8l-4 4" />
                            </svg>
                            <span
                                className="text-[11px] font-mono font-medium tracking-[0.1em] uppercase"
                                style={{ color: "#aeaeb2" }}
                            >
                                Anti-spoofing
                            </span>
                        </div>
                        <p
                            className="text-[14px] leading-[1.7]"
                            style={{ color: "#86868b" }}
                        >
                            To prevent DNS spoofing, wallets cross-reference the resolved
                            hostname against a{" "}
                            <span style={{ color: "#1d1d1f" }} className="font-medium">
                                decentralized Trust Registry
                            </span>{" "}
                            maintained on the Sui blockchain. No intent is signed until the
                            merchant&apos;s identity is verified on-chain.
                        </p>
                    </div>
                </motion.div>
            </div>
        </section>
    );
}
