"use client";

import { motion, useTransform, useScroll } from "framer-motion";
import { useRef } from "react";
import TiledBackground from "./TiledBackground";

const ease = [0.25, 0.46, 0.45, 0.94] as const;

function AnimatedWord({ word, index }: { word: string; index: number }) {
  return (
    <motion.span
      className="inline-block"
      initial={{ opacity: 0, y: 40, filter: "blur(8px)" }}
      animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
      transition={{
        duration: 0.8,
        delay: 0.3 + index * 0.08,
        ease,
      }}
    >
      {word}
    </motion.span>
  );
}

export default function Hero() {
  const sectionRef = useRef(null);
  const { scrollYProgress } = useScroll({
    target: sectionRef,
    offset: ["start start", "end start"],
  });

  const heroY = useTransform(scrollYProgress, [0, 1], [0, 150]);
  const heroOpacity = useTransform(scrollYProgress, [0, 0.5], [1, 0]);

  return (
    <section ref={sectionRef} className="relative min-h-screen flex items-center justify-center overflow-hidden">
      {/* Animated identipay dot grid background */}
      <TiledBackground circleColor="rgba(0,0,0,0.045)" />

      <motion.div
        className="relative z-10 max-w-[1120px] mx-auto px-6 pt-32 pb-24 w-full text-center"
        style={{ y: heroY, opacity: heroOpacity }}
      >
        {/* Main headline */}
        <h1 className="text-[clamp(3rem,7vw,6rem)] font-semibold leading-[1.0] tracking-[-0.04em]">
          <div className="overflow-hidden">
            <AnimatedWord word="The" index={0} />{" "}
            <AnimatedWord word="end" index={1} />{" "}
            <AnimatedWord word="of" index={2} />
          </div>
          <div className="overflow-hidden">
            <motion.span
              className="inline-block"
              initial={{ opacity: 0, y: 40, filter: "blur(8px)" }}
              animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
              transition={{ duration: 0.8, delay: 0.55, ease }}
              style={{ color: "#86868b" }}
            >
              pull payments.
            </motion.span>
          </div>
        </h1>

        {/* Subheading */}
        <motion.p
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.8, ease }}
          className="mt-6 text-[clamp(1rem,2vw,1.25rem)] leading-[1.6] max-w-2xl mx-auto"
          style={{ color: "#86868b" }}
        >
          Merchants propose. You sign intent. Money, receipts, and warranties
          settle atomically to a stealth address only you control.
        </motion.p>

        {/* CTA buttons */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 1.0, ease }}
          className="mt-10 flex flex-wrap gap-4 justify-center"
        >
          <a
            href="#protocol"
            className="inline-flex items-center gap-2.5 text-[14px] font-medium px-8 py-4 rounded-full text-white transition-all duration-300 hover:shadow-xl hover:shadow-black/10"
            style={{ backgroundColor: "#000" }}
          >
            Explore the Protocol
          </a>
          <a
            href="#whitepaper"
            className="inline-flex items-center gap-2.5 text-[14px] font-medium px-8 py-4 rounded-full transition-all duration-300 hover:border-black/15"
            style={{
              backgroundColor: "transparent",
              color: "#86868b",
              border: "1px solid rgba(0,0,0,0.08)",
            }}
          >
            Read Whitepaper
          </a>
        </motion.div>

        {/* Scroll hint */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ delay: 1.8, duration: 1 }}
          className="mt-24"
        >
          <motion.div
            animate={{ y: [0, 8, 0] }}
            transition={{ repeat: Infinity, duration: 2.5, ease: "easeInOut" }}
            className="inline-flex flex-col items-center gap-2"
          >
            <span className="text-[11px] font-medium tracking-widest uppercase" style={{ color: "#d2d2d7" }}>
              Scroll
            </span>
            <div
              className="w-[1px] h-8"
              style={{
                background: "linear-gradient(to bottom, #d2d2d7, transparent)",
              }}
            />
          </motion.div>
        </motion.div>
      </motion.div>
    </section>
  );
}
