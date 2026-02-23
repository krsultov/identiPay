"use client";

import { motion, useInView } from "framer-motion";
import { useRef, useState } from "react";

const ease = [0.25, 0.46, 0.45, 0.94] as const;

const sections = [
  "Introduction",
  "What Breaks in Today\u2019s Payment Flows",
  "Protocol Overview",
  "Settlement Abstraction Layer",
  "Federated Routing & Resolution",
  "Stealth Identity & Privacy",
  "Push-Only Intent Engine",
  "Atomic Contextual Settlement on Sui",
  "Data Model: Commerce Artifacts",
  "Use Cases",
  "Integration Strategy",
  "Threat Model & Security",
  "Conclusion",
];

export default function WhitepaperSection() {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-80px" });
  const [hoveredIndex, setHoveredIndex] = useState(-1);

  return (
    <section id="whitepaper" className="relative py-36 px-6 overflow-hidden" style={{ backgroundColor: "#fff" }}>
      {/* Subtle gradient background */}
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          background: "radial-gradient(ellipse at 30% 50%, rgba(0,0,0,0.015), transparent 60%)",
        }}
      />

      <div className="relative z-10 max-w-[1120px] mx-auto">
        <div className="grid lg:grid-cols-2 gap-16 lg:gap-20 items-center">
          {/* Left */}
          <motion.div
            ref={ref}
            initial={{ opacity: 0, y: 30 }}
            animate={isInView ? { opacity: 1, y: 0 } : {}}
            transition={{ duration: 0.8, ease }}
          >
            <span
              className="text-[12px] font-medium tracking-[0.2em] uppercase"
              style={{ color: "#86868b" }}
            >
              Whitepaper
            </span>
            <h2
              className="mt-4 text-[clamp(2rem,4vw,3.2rem)] font-semibold tracking-[-0.035em] leading-[1.08]"
              style={{ color: "#1d1d1f" }}
            >
              The full
              <br />
              <span style={{ color: "#86868b" }}>technical specification.</span>
            </h2>
            <p
              className="mt-5 text-[15px] leading-[1.7] max-w-md"
              style={{ color: "#86868b" }}
            >
              identiPay &mdash; The Intent-Based Commerce Protocol. From pull
              payments to contextual, intent-driven atomic settlement.
            </p>
            <p className="mt-2 text-[13px]" style={{ color: "#aeaeb2" }}>
              By Krum Sultov &middot; February 2026
            </p>

            <motion.div
              className="mt-8"
              initial={{ opacity: 0, y: 10 }}
              animate={isInView ? { opacity: 1, y: 0 } : {}}
              transition={{ duration: 0.6, delay: 0.3, ease }}
            >
              <a
                href="/identipay-whitepaper.pdf"
                className="inline-flex items-center gap-2.5 text-[14px] font-medium px-8 py-4 rounded-full text-white transition-all duration-300 hover:shadow-xl hover:shadow-black/10"
                style={{ backgroundColor: "#1d1d1f" }}
              >
                <svg
                  width="16" height="16" viewBox="0 0 16 16" fill="none"
                  stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"
                >
                  <path d="M14 10v3a1 1 0 01-1 1H3a1 1 0 01-1-1v-3" />
                  <polyline points="5 7 8 10 11 7" />
                  <line x1="8" y1="2" x2="8" y2="10" />
                </svg>
                Download PDF
              </a>
            </motion.div>
          </motion.div>

          {/* Right: TOC */}
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, margin: "-60px" }}
            transition={{ duration: 0.8, delay: 0.15, ease }}
          >
            <div
              className="rounded-3xl p-8 lg:p-10"
              style={{
                backgroundColor: "#f5f5f7",
                border: "1px solid rgba(0,0,0,0.04)",
              }}
            >
              <h3
                className="text-[12px] font-semibold tracking-[0.2em] uppercase mb-6"
                style={{ color: "#aeaeb2" }}
              >
                Contents
              </h3>
              <ol className="space-y-0">
                {sections.map((section, i) => (
                  <motion.li
                    key={i}
                    initial={{ opacity: 0, x: -10 }}
                    whileInView={{ opacity: 1, x: 0 }}
                    viewport={{ once: true }}
                    transition={{ delay: 0.3 + i * 0.04, duration: 0.4, ease }}
                    className="flex items-baseline gap-4 py-2.5 rounded-xl px-3 -mx-3 transition-all duration-200 cursor-default"
                    style={{
                      backgroundColor: hoveredIndex === i ? "rgba(255,255,255,0.8)" : "transparent",
                    }}
                    onMouseEnter={() => setHoveredIndex(i)}
                    onMouseLeave={() => setHoveredIndex(-1)}
                  >
                    <span
                      className="text-[11px] font-mono w-5 text-right flex-shrink-0 transition-colors duration-200"
                      style={{ color: hoveredIndex === i ? "#1d1d1f" : "#d2d2d7" }}
                    >
                      {i + 1}
                    </span>
                    <span
                      className="text-[14px] transition-colors duration-200"
                      style={{ color: hoveredIndex === i ? "#1d1d1f" : "#86868b" }}
                    >
                      {section}
                    </span>
                  </motion.li>
                ))}
              </ol>
            </div>
          </motion.div>
        </div>
      </div>
    </section>
  );
}
