"use client";

import { motion, useInView } from "framer-motion";
import { useRef } from "react";
import Logo from "./Logo";

const ease = [0.25, 0.46, 0.45, 0.94] as const;


function FooterLink({ link }: { link: string }) {
  return (
    <motion.a
      href={`#${link.toLowerCase().replace(/\s/g, "")}`}
      className="text-[12px] transition-colors duration-300"
      style={{ color: "rgba(255,255,255,0.2)" }}
      whileHover={{ color: "rgba(255,255,255,0.5)" }}
    >
      {link}
    </motion.a>
  );
}

export default function Footer() {
  const ref = useRef(null);
  const isInView = useInView(ref, { once: true, margin: "-40px" });

  return (
    <footer className="relative py-24 px-6 overflow-hidden" style={{ backgroundColor: "#0a0a0a" }}>
      {/* Subtle radial glow */}
      <div
        className="absolute top-0 left-1/2 -translate-x-1/2 w-[800px] h-[400px] pointer-events-none"
        style={{
          background: "radial-gradient(ellipse at center, rgba(255,255,255,0.02) 0%, transparent 70%)",
        }}
      />

      <div className="relative z-10 max-w-[1120px] mx-auto">
        {/* CTA */}
        <motion.div
          ref={ref}
          initial={{ opacity: 0, y: 30 }}
          animate={isInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.8, ease }}
          className="text-center mb-20"
        >
          <h2
            className="text-[clamp(1.8rem,3.5vw,2.8rem)] font-semibold tracking-[-0.035em] leading-[1.12]"
            style={{ color: "#fff" }}
          >
            Commerce is deterministic.
            <br />
            <span style={{ color: "rgba(255,255,255,0.2)" }}>
              It should act like it.
            </span>
          </h2>
          <motion.div
            initial={{ opacity: 0, y: 15 }}
            animate={isInView ? { opacity: 1, y: 0 } : {}}
            transition={{ duration: 0.6, delay: 0.2, ease }}
            className="mt-8 flex flex-wrap gap-4 justify-center"
          >
            <a
              href="#protocol"
              className="inline-flex items-center gap-2 text-[14px] font-medium px-7 py-3.5 rounded-full transition-all duration-300 hover:opacity-90"
              style={{ backgroundColor: "#fff", color: "#000" }}
            >
              Explore the Protocol
            </a>
            <a
              href="#whitepaper"
              className="inline-flex items-center gap-2 text-[14px] font-medium px-7 py-3.5 rounded-full transition-all duration-300 hover:border-white/20 hover:text-white/55"
              style={{
                backgroundColor: "transparent",
                color: "rgba(255,255,255,0.35)",
                border: "1px solid rgba(255,255,255,0.08)",
              }}
            >
              Read Whitepaper
            </a>
          </motion.div>
        </motion.div>

        {/* Divider */}
        <div className="h-px mb-12" style={{ backgroundColor: "rgba(255,255,255,0.06)" }} />

        {/* Bottom */}
        <div className="flex flex-col md:flex-row items-center justify-between gap-6">
          <div style={{ color: "rgba(255,255,255,0.5)" }}>
            <Logo className="h-5 w-auto" />
          </div>

          <div className="flex items-center gap-8">
            {["Protocol", "Technology", "Use Cases", "Security", "Whitepaper"].map((link) => (
              <FooterLink key={link} link={link} />
            ))}
          </div>

          <span className="text-[12px]" style={{ color: "rgba(255,255,255,0.12)" }}>
            &copy; {new Date().getFullYear()} identiPay
          </span>
        </div>
      </div>
    </footer>
  );
}
