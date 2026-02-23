"use client";

import { motion, useInView, AnimatePresence } from "framer-motion";
import { useRef, useState } from "react";

const ease = [0.25, 0.46, 0.45, 0.94] as const;

/* ─── Flow data ────────────────────────────────────────────────── */

const flows = [
    {
        id: "p2p",
        tab: "Peer-to-Peer",
        headline: "Send to a name, land at a stealth address.",
        lead: "Bob sends 50 USDC to @alice.idpay. His wallet resolves the name from the on-chain registry — two public keys, no address. An ephemeral key exchange produces a one-time stealth address. The money arrives at an address only Alice can detect.",
        steps: [
            {
                num: "01",
                title: "Resolve name",
                detail: "Bob's wallet queries the on-chain MetaAddressRegistry and obtains Alice's meta-address — a pair of public keys (K_spend, K_view). No Sui address is stored or returned.",
                accent: "#60a5fa",
            },
            {
                num: "02",
                title: "Derive stealth address",
                detail: "The wallet generates a fresh random scalar r, performs ECDH with Alice's viewing key, derives a one-time stealth public key, and computes the corresponding Sui address. This address has never existed before.",
                accent: "#a78bfa",
            },
            {
                num: "03",
                title: "Transfer & announce",
                detail: "50 USDC is sent to the stealth address. A StealthAnnouncement event is emitted containing the ephemeral public point R and a one-byte view tag — enough for Alice to detect the payment without scanning every transaction.",
                accent: "#34d399",
            },
            {
                num: "04",
                title: "Alice detects & spends",
                detail: "Alice's wallet filters announcements by view tag (eliminates ~255 of 256 candidates), recomputes the shared secret with her private viewing key, confirms the address match, and derives the stealth private key. She can now spend the 50 USDC whenever she chooses.",
                accent: "#f59e0b",
            },
        ],
        visual: {
            from: "@bob.idpay",
            to: "@alice.idpay",
            amount: "50 USDC",
            artifacts: ["StealthAnnouncement"],
            privacyNote: "An observer sees two unconnected addresses with no on-chain link to @alice.idpay or to each other.",
        },
    },
    {
        id: "merchant",
        tab: "Merchant Checkout",
        headline: "Scan. Confirm. Settle atomically.",
        lead: "Alice visits a coffee shop and scans a QR code. Her wallet resolves the URI, retrieves the commerce proposal, and renders it in plain language. She confirms, and a single PTB atomically transfers payment and mints an encrypted receipt to her self-derived stealth address.",
        steps: [
            {
                num: "01",
                title: "Scan QR / Tap NFC",
                detail: "The merchant's POS displays a QR encoding a did:identipay URI. Alice's wallet resolves the URI via HTTPS, retrieves the typed proposal (items, price, warranty terms), and cross-checks the merchant against the on-chain Trust Registry.",
                accent: "#60a5fa",
            },
            {
                num: "02",
                title: "Review proposal",
                detail: "The wallet renders the proposal in plain language: \"Send 5 USDC to CoffeeShop. Receive: receipt.\" No raw calldata, no opaque bytes — the UI contract is driven entirely by the typed proposal.",
                accent: "#a78bfa",
            },
            {
                num: "03",
                title: "Self-derive stealth address",
                detail: "Since Alice controls both keys, her wallet self-derives a fresh stealth address for receiving the receipt. No external announcement or lookup is needed — the derivation parameters stay local.",
                accent: "#34d399",
            },
            {
                num: "04",
                title: "Atomic settlement",
                detail: "Alice signs the intent hash and submits a PTB that atomically: transfers 5 USDC to the merchant, mints an encrypted ReceiptObject to her stealth address, and (if applicable) mints a WarrantyObject. All or nothing.",
                accent: "#f59e0b",
            },
        ],
        visual: {
            from: "@alice.idpay",
            to: "CoffeeShop",
            amount: "5 USDC",
            artifacts: ["ReceiptObject", "WarrantyObject"],
            privacyNote: "The receipt lands at a one-time address that cannot be linked to @alice.idpay by any on-chain observer.",
        },
    },
];

/* ─── Step dot + line ──────────────────────────────────────────── */

function StepNode({
    step,
    index,
    total,
    isInView,
}: {
    step: (typeof flows)[0]["steps"][0];
    index: number;
    total: number;
    isInView: boolean;
}) {
    return (
        <motion.div
            initial={{ opacity: 0, y: 24 }}
            animate={isInView ? { opacity: 1, y: 0 } : {}}
            transition={{ duration: 0.5, delay: 0.12 * index, ease }}
            className="relative flex gap-5"
        >
            {/* Timeline */}
            <div className="flex flex-col items-center">
                <motion.div
                    className="w-8 h-8 rounded-full flex items-center justify-center text-[11px] font-mono font-semibold flex-shrink-0"
                    style={{
                        backgroundColor: `${step.accent}14`,
                        color: step.accent,
                        border: `1.5px solid ${step.accent}30`,
                    }}
                    initial={{ scale: 0 }}
                    animate={isInView ? { scale: 1 } : {}}
                    transition={{ duration: 0.4, delay: 0.12 * index + 0.1, ease }}
                >
                    {step.num}
                </motion.div>
                {index < total - 1 && (
                    <motion.div
                        className="w-px flex-1 my-1"
                        style={{ backgroundColor: `${step.accent}20` }}
                        initial={{ scaleY: 0 }}
                        animate={isInView ? { scaleY: 1 } : {}}
                        transition={{ duration: 0.5, delay: 0.12 * index + 0.2, ease }}
                    />
                )}
            </div>

            {/* Content */}
            <div className="pb-8">
                <h4
                    className="text-[15px] font-semibold tracking-[-0.01em]"
                    style={{ color: "#1d1d1f" }}
                >
                    {step.title}
                </h4>
                <p
                    className="mt-1.5 text-[13px] leading-[1.7]"
                    style={{ color: "#86868b" }}
                >
                    {step.detail}
                </p>
            </div>
        </motion.div>
    );
}

/* ─── Flow diagram visual ──────────────────────────────────────── */

function FlowVisual({
    visual,
    isInView,
}: {
    visual: (typeof flows)[0]["visual"];
    isInView: boolean;
}) {
    return (
        <motion.div
            initial={{ opacity: 0, scale: 0.96 }}
            animate={isInView ? { opacity: 1, scale: 1 } : {}}
            transition={{ duration: 0.6, delay: 0.3, ease }}
            className="rounded-2xl p-6 h-full flex flex-col justify-between"
            style={{
                backgroundColor: "#0a0a0a",
                border: "1px solid rgba(255,255,255,0.06)",
                boxShadow: "0 25px 60px -12px rgba(0,0,0,0.25)",
            }}
        >
            {/* Flow arrows */}
            <div>
                <div className="flex items-center gap-3 mb-6">
                    <span
                        className="text-[11px] font-mono tracking-wider uppercase"
                        style={{ color: "rgba(255,255,255,0.25)" }}
                    >
                        Flow Diagram
                    </span>
                </div>

                {/* From → To */}
                <div className="flex items-center gap-4 mb-6">
                    <div
                        className="px-3.5 py-2 rounded-lg text-[13px] font-mono font-medium"
                        style={{
                            backgroundColor: "rgba(96,165,250,0.1)",
                            color: "#60a5fa",
                            border: "1px solid rgba(96,165,250,0.15)",
                        }}
                    >
                        {visual.from}
                    </div>
                    <div className="flex-1 flex items-center gap-1.5">
                        <motion.div
                            className="flex-1 h-px"
                            style={{
                                background:
                                    "linear-gradient(to right, rgba(96,165,250,0.3), rgba(52,211,153,0.3))",
                            }}
                            initial={{ scaleX: 0 }}
                            animate={isInView ? { scaleX: 1 } : {}}
                            transition={{ duration: 0.8, delay: 0.5, ease }}
                        />
                        <span style={{ color: "rgba(52,211,153,0.5)" }}>→</span>
                    </div>
                    <div
                        className="px-3.5 py-2 rounded-lg text-[13px] font-mono font-medium"
                        style={{
                            backgroundColor: "rgba(52,211,153,0.1)",
                            color: "#34d399",
                            border: "1px solid rgba(52,211,153,0.15)",
                        }}
                    >
                        {visual.to}
                    </div>
                </div>

                {/* Amount */}
                <div className="mb-6">
                    <span
                        className="text-[10px] font-mono tracking-[0.15em] uppercase"
                        style={{ color: "rgba(255,255,255,0.2)" }}
                    >
                        Amount
                    </span>
                    <div
                        className="mt-1 text-[22px] font-semibold tracking-[-0.02em]"
                        style={{ color: "rgba(255,255,255,0.85)" }}
                    >
                        {visual.amount}
                    </div>
                </div>

                {/* Artifacts */}
                <div className="mb-6">
                    <span
                        className="text-[10px] font-mono tracking-[0.15em] uppercase"
                        style={{ color: "rgba(255,255,255,0.2)" }}
                    >
                        Artifacts
                    </span>
                    <div className="mt-2 flex flex-wrap gap-2">
                        {visual.artifacts.map((a) => (
                            <span
                                key={a}
                                className="px-3 py-1.5 rounded-md text-[11px] font-mono"
                                style={{
                                    backgroundColor: "rgba(245,158,11,0.1)",
                                    color: "#f59e0b",
                                    border: "1px solid rgba(245,158,11,0.12)",
                                }}
                            >
                                {a}
                            </span>
                        ))}
                    </div>
                </div>
            </div>

            {/* Privacy note */}
            <div
                className="rounded-xl p-4 mt-auto"
                style={{
                    backgroundColor: "rgba(255,255,255,0.03)",
                    border: "1px solid rgba(255,255,255,0.04)",
                }}
            >
                <div className="flex items-start gap-2.5">
                    <span className="text-[12px] mt-0.5" style={{ color: "#34d399" }}>
                        ◆
                    </span>
                    <p
                        className="text-[12px] leading-[1.6] italic"
                        style={{ color: "rgba(255,255,255,0.3)" }}
                    >
                        {visual.privacyNote}
                    </p>
                </div>
            </div>
        </motion.div>
    );
}

/* ─── Main component ───────────────────────────────────────────── */

export default function PaymentFlows() {
    const [activeFlow, setActiveFlow] = useState("p2p");
    const titleRef = useRef(null);
    const isInView = useInView(titleRef, { once: true, margin: "-80px" });

    const flow = flows.find((f) => f.id === activeFlow)!;

    return (
        <section className="py-36 px-6" style={{ backgroundColor: "#fff" }}>
            <div className="max-w-[1120px] mx-auto">
                {/* Header */}
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
                        Payment Flows
                    </span>
                    <h2
                        className="mt-4 text-[clamp(2rem,4vw,3.2rem)] font-semibold tracking-[-0.035em] leading-[1.08]"
                        style={{ color: "#1d1d1f" }}
                    >
                        Two flows.
                        <br />
                        <span style={{ color: "#86868b" }}>Same privacy.</span>
                    </h2>
                    <p
                        className="mt-4 text-[15px] leading-[1.65]"
                        style={{ color: "#aeaeb2" }}
                    >
                        Whether you&apos;re paying a friend or buying a coffee, every payment lands
                        at a fresh stealth address. The flows differ in ceremony — the
                        privacy guarantees are identical.
                    </p>
                </motion.div>

                {/* Tab switcher */}
                <div className="flex justify-center mb-14">
                    <div
                        className="inline-flex rounded-full p-[3px]"
                        style={{
                            backgroundColor: "rgba(0,0,0,0.03)",
                            border: "1px solid rgba(0,0,0,0.04)",
                            boxShadow: "0 1px 3px rgba(0,0,0,0.04)",
                        }}
                    >
                        {flows.map((f) => (
                            <button
                                key={f.id}
                                onClick={() => setActiveFlow(f.id)}
                                className="relative px-6 py-2.5 rounded-full text-[13px] font-medium transition-colors duration-300"
                                style={{
                                    color: activeFlow === f.id ? "#fff" : "#86868b",
                                }}
                            >
                                {activeFlow === f.id && (
                                    <motion.div
                                        layoutId="activeFlowTab"
                                        className="absolute inset-0 rounded-full"
                                        style={{ backgroundColor: "#1d1d1f" }}
                                        transition={{ type: "spring", bounce: 0.12, duration: 0.5 }}
                                    />
                                )}
                                <span className="relative z-10">{f.tab}</span>
                            </button>
                        ))}
                    </div>
                </div>

                {/* Content */}
                <AnimatePresence mode="wait">
                    <motion.div
                        key={activeFlow}
                        initial={{ opacity: 0, y: 16 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -8 }}
                        transition={{ duration: 0.35, ease }}
                    >
                        {/* Headline + lead */}
                        <div className="text-center max-w-2xl mx-auto mb-12">
                            <h3
                                className="text-[22px] font-semibold tracking-[-0.02em]"
                                style={{ color: "#1d1d1f" }}
                            >
                                {flow.headline}
                            </h3>
                            <p
                                className="mt-3 text-[14px] leading-[1.7]"
                                style={{ color: "#86868b" }}
                            >
                                {flow.lead}
                            </p>
                        </div>

                        {/* Two-column: steps + diagram */}
                        <div className="grid lg:grid-cols-2 gap-10">
                            {/* Left: step-by-step */}
                            <div>
                                {flow.steps.map((step, i) => (
                                    <StepNode
                                        key={step.num}
                                        step={step}
                                        index={i}
                                        total={flow.steps.length}
                                        isInView={isInView}
                                    />
                                ))}
                            </div>

                            {/* Right: visual */}
                            <FlowVisual visual={flow.visual} isInView={isInView} />
                        </div>
                    </motion.div>
                </AnimatePresence>
            </div>
        </section>
    );
}
