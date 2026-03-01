"use client";

import { useState, useEffect, useCallback } from "react";
import Image from "next/image";
import { motion, AnimatePresence } from "framer-motion";
import { QRCodeSVG } from "qrcode.react";
import { useCart } from "@/lib/cart";
import { MERCHANT } from "@/lib/products";
import Logo from "./Logo";

type CheckoutStep = "review" | "creating" | "pay" | "confirming" | "success";

interface ProposalData {
  transactionId: string;
  intentHash: string;
  qrDataUrl: string;
  uri: string;
  expiresAt: string;
}

export default function CheckoutPage({ onBack }: { onBack: () => void }) {
  const { items, total, updateQuantity, removeItem, clearCart, itemCount } =
    useCart();
  const [step, setStep] = useState<CheckoutStep>("review");
  const [countdown, setCountdown] = useState(900);
  const [txHash, setTxHash] = useState("");
  const [proposal, setProposal] = useState<ProposalData | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Determine if any cart item requires age gating
  const maxAgeGate = items.reduce(
    (max, i) => Math.max(max, i.product.ageGate ?? 0),
    0,
  );

  // Create proposal via backend
  const createProposal = useCallback(async () => {
    setStep("creating");
    setError(null);
    try {
      const res = await fetch("/api/proposal", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          items: items.map((i) => ({
            name: i.product.name,
            quantity: i.quantity,
            unitPrice: i.product.price.toFixed(2),
            currency: i.product.currency,
          })),
          amount: {
            value: total.toFixed(2),
            currency: "USDC",
          },
          deliverables: {
            receipt: true,
          },
          ...(maxAgeGate > 0 && {
            constraints: {
              ageGate: maxAgeGate,
            },
          }),
          expiresInSeconds: 900,
        }),
      });

      if (!res.ok) {
        const errData = await res.json().catch(() => ({}));
        throw new Error(errData.message || "Failed to create proposal");
      }

      const data = await res.json();
      setProposal(data);
      setCountdown(900);
      setStep("pay");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create proposal");
      setStep("review");
    }
  }, [items, total]);

  // Countdown timer
  useEffect(() => {
    if (step !== "pay") return;
    const interval = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [step]);

  // WebSocket listener for real-time settlement updates
  useEffect(() => {
    if (step !== "pay" || !proposal?.transactionId) return;

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const backendHost = process.env.NEXT_PUBLIC_BACKEND_URL
      ? new URL(process.env.NEXT_PUBLIC_BACKEND_URL).host
      : "localhost:3000";
    const wsUrl = `${protocol}//${backendHost}/ws/transactions/${proposal.transactionId}`;
    const ws = new WebSocket(wsUrl);

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === "settlement" || (data.type === "status" && data.status === "settled")) {
          setStep("confirming");
          setTxHash(data.suiTxDigest || "");
          // Brief confirming animation, then success
          setTimeout(() => setStep("success"), 2000);
        }
      } catch {
        // ignore parse errors
      }
    };

    ws.onerror = (err) => {
      console.error("WebSocket error:", err);
    };

    return () => {
      ws.close();
    };
  }, [step, proposal?.transactionId]);

  const formatTime = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, "0")}`;
  };

  const transactionId = proposal?.transactionId || "";

  // QR payload: use the backend-generated URI if available, otherwise build one
  const qrPayload = proposal?.uri || JSON.stringify({
    protocol: "identipay",
    version: "1",
    type: "CommerceProposal",
    merchant: {
      name: MERCHANT.name,
      address: MERCHANT.address,
    },
    amount: { value: total.toFixed(2), currency: "USDC" },
  });

  return (
    <div className="flex flex-col md:flex-row min-h-screen w-full bg-white text-[#1d1d1f]">
      
      {/* ── Left Column: Order Summary (Stripe Style) ── */}
      <div className="flex w-full flex-col border-r border-[#e6e6eb] bg-[#fbfbfd] md:w-1/2 lg:w-[45%] md:sticky md:top-0 md:h-[100dvh]">
        <div className="flex flex-col h-full px-6 py-8 md:px-12 md:py-12 w-full max-w-xl mx-auto md:ml-auto md:mr-0">
          
          <div className="flex-shrink-0 mb-8 flex items-center justify-between">
            <button
              onClick={onBack}
              className="group flex items-center gap-1.5 text-sm font-medium text-[#86868b] transition-colors hover:text-[#1d1d1f]"
            >
              <svg
                width="16" height="16" viewBox="0 0 24 24" fill="none"
                stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"
                className="transition-transform group-hover:-translate-x-0.5"
              >
                <line x1="19" y1="12" x2="5" y2="12" />
                <polyline points="12 19 5 12 12 5" />
              </svg>
              Back to store
            </button>
            <div className="flex items-center gap-2">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="#34d399" strokeWidth="3" strokeLinecap="round">
                <circle cx="12" cy="12" r="4" fill="#34d399" />
              </svg>
              <span className="text-xs font-medium text-[#86868b]">Secure session</span>
            </div>
          </div>

          <div className="flex-shrink-0 mb-8 flex items-center gap-4">
             <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-[#0a0a0a] shadow-[0_4px_12px_rgba(0,0,0,0.1)]">
              <span className="text-base font-bold text-white">TV</span>
            </div>
            <div>
               <h2 className="text-xl font-semibold tracking-tight text-[#1d1d1f]">
                 {MERCHANT.name}
               </h2>
               <p className="text-sm text-[#86868b]">
                 {itemCount} {itemCount === 1 ? "item" : "items"}
               </p>
            </div>
          </div>

          {/* Line items (Scrollable on desktop) */}
          <div className="md:flex-1 md:overflow-y-auto md:min-h-0 divide-y divide-black/[0.04] md:pr-4 md:-mr-4">
            {items.map((item) => (
              <div key={item.product.id} className="flex items-start gap-4 py-5">
                <div className="relative h-16 w-16 flex-shrink-0 overflow-hidden rounded-xl bg-white border border-black/[0.04] shadow-sm">
                  <Image src={item.product.image} alt={item.product.name} fill className="object-contain p-2" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium text-[#1d1d1f]">{item.product.name}</p>
                  <p className="text-xs text-[#86868b] mt-0.5">{item.product.price.toFixed(2)} USDC</p>
                  <div className="flex items-center gap-3 mt-3">
                    <div className="flex items-center bg-white border border-black/[0.08] rounded-full px-1 py-0.5">
                      <button onClick={() => updateQuantity(item.product.id, item.quantity - 1)} className="flex h-6 w-6 items-center justify-center rounded-full text-[#86868b] hover:text-[#1d1d1f] hover:bg-black/[0.04]">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="5" y1="12" x2="19" y2="12" /></svg>
                      </button>
                      <span className="w-6 text-center text-xs font-medium text-[#1d1d1f]">{item.quantity}</span>
                      <button onClick={() => updateQuantity(item.product.id, item.quantity + 1)} className="flex h-6 w-6 items-center justify-center rounded-full text-[#86868b] hover:text-[#1d1d1f] hover:bg-black/[0.04]">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
                      </button>
                    </div>
                    <button onClick={() => removeItem(item.product.id)} className="text-xs font-medium text-[#86868b] transition-colors hover:text-red-500">Remove</button>
                  </div>
                </div>
                <div className="text-right flex-shrink-0">
                  <p className="text-sm font-semibold text-[#1d1d1f]">{(item.product.price * item.quantity).toFixed(2)}</p>
                  <p className="text-[10px] text-[#86868b]">USDC</p>
                </div>
              </div>
            ))}
          </div>

          <div className="flex-shrink-0 mt-6 space-y-3 pt-4 border-t border-black/[0.04] md:border-none md:pt-0 md:mt-4">
            <div className="flex items-center justify-between text-sm">
              <span className="text-[#86868b]">Subtotal</span>
              <span className="font-medium text-[#1d1d1f]">{total.toFixed(2)} USDC</span>
            </div>
            <div className="flex items-center justify-between text-sm">
              <span className="text-[#86868b]">Network fee</span>
              <span className="font-medium text-[#34d399]">Free</span>
            </div>
          </div>

          <div className="flex-shrink-0 mt-4 border-t border-black/[0.04] pt-4">
            <div className="flex items-center justify-between">
              <span className="text-base font-semibold text-[#1d1d1f]">Total</span>
              <span className="text-2xl font-semibold tracking-tight text-[#1d1d1f]">
                {total.toFixed(2)} <span className="text-sm font-medium text-[#86868b] ml-1">USDC</span>
              </span>
            </div>
          </div>

          <div className="flex-shrink-0 mt-6 rounded-xl border border-[#34d399]/20 bg-[#34d399]/5 p-4">
            <div className="flex items-start gap-3">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#34d399" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="mt-0.5 flex-shrink-0">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                <path d="M7 11V7a5 5 0 0110 0v4" />
              </svg>
              <div>
                <p className="text-xs font-semibold text-[#1d1d1f]">Privacy guaranteed by identiPay</p>
                <p className="mt-1 text-[11px] leading-relaxed text-[#86868b]">Atomic stealth payment prevents address reuse and purchase history linkage. End-to-end encrypted receipts.</p>
              </div>
            </div>
          </div>

          {maxAgeGate > 0 && (
            <div className="flex-shrink-0 mt-3 rounded-xl border border-amber-400/30 bg-amber-50 p-4">
              <div className="flex items-start gap-3">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#d97706" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="mt-0.5 flex-shrink-0">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                </svg>
                <div>
                  <p className="text-xs font-semibold text-[#1d1d1f]">Age verification required ({maxAgeGate}+)</p>
                  <p className="mt-1 text-[11px] leading-relaxed text-[#86868b]">Your wallet will generate a zero-knowledge proof of age. Your birthdate stays private — only the fact that you meet the threshold is revealed.</p>
                </div>
              </div>
            </div>
          )}

          <div className="hidden md:block flex-shrink-0 flex-1" />
          
          <div className="flex-shrink-0 mt-10 md:mt-6 flex items-center gap-2 text-[#86868b]">
            <span className="text-xs font-medium">Powered by</span>
            <Logo className="h-4 text-[#1d1d1f]" />
          </div>

        </div>
      </div>

      {/* ── Right Column: Payment Flow ── */}
      <div className="flex w-full flex-col bg-white md:w-1/2 lg:w-[55%] items-center justify-center p-6 md:p-12 relative">
        <div className="w-full max-w-sm mx-auto">
          <AnimatePresence mode="wait">
            
            {/* Step 1: Review */}
            {step === "review" && (
              <motion.div key="review" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} transition={{ duration: 0.3 }} className="flex flex-col">
                <h2 className="text-2xl font-bold tracking-tight text-[#1d1d1f]">Complete your payment</h2>
                <p className="mt-2 text-sm text-[#86868b]">Fast, private, and secure checkout onto the Sui ecosystem.</p>

                {error && (
                  <div className="mt-6 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-600">
                    <p className="font-semibold">Error</p>
                    <p className="mt-1">{error}</p>
                  </div>
                )}

                <button
                  onClick={createProposal}
                  disabled={items.length === 0}
                  className="mt-8 flex w-full items-center justify-center gap-2 rounded-xl bg-black px-6 py-4 text-sm font-semibold text-white shadow-lg shadow-black/10 transition-all hover:bg-[#1d1d1f] active:scale-[0.98] disabled:opacity-40"
                >
                  Pay {total.toFixed(2)} USDC with identiPay
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <line x1="5" y1="12" x2="19" y2="12" />
                    <polyline points="12 5 19 12 12 19" />
                  </svg>
                </button>
                
                <div className="mt-8">
                  <p className="text-xs font-semibold text-[#86868b] uppercase tracking-wider mb-4">How it works</p>
                  <div className="space-y-4">
                    <div className="flex items-start gap-3">
                      <div className="flex h-6 w-6 items-center justify-center rounded-full bg-[#f5f5f7] text-xs font-bold text-[#1d1d1f]">1</div>
                      <p className="text-sm font-medium text-[#1d1d1f] pt-0.5">Generate a one-time proposal</p>
                    </div>
                    <div className="flex items-start gap-3">
                      <div className="flex h-6 w-6 items-center justify-center rounded-full bg-[#f5f5f7] text-xs font-bold text-[#1d1d1f]">2</div>
                      <p className="text-sm font-medium text-[#1d1d1f] pt-0.5">Scan via your identiPay app</p>
                    </div>
                    <div className="flex items-start gap-3">
                      <div className="flex h-6 w-6 items-center justify-center rounded-full bg-[#f5f5f7] text-xs font-bold text-[#1d1d1f]">3</div>
                      <p className="text-sm font-medium text-[#1d1d1f] pt-0.5">Atomic, identity-hidden settlement</p>
                    </div>
                  </div>
                </div>
              </motion.div>
            )}

            {/* Step 1.5: Creating proposal */}
            {step === "creating" && (
              <motion.div key="creating" initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }} transition={{ duration: 0.3 }} className="flex flex-col items-center py-20 text-center">
                <div className="relative mb-8">
                  <div className="h-16 w-16 rounded-full border border-[#e6e6eb] bg-[#fbfbfd]" />
                  <div className="absolute inset-0 animate-spin-slow rounded-full border-2 border-transparent border-t-black" />
                </div>
                <h2 className="text-xl font-bold text-[#1d1d1f]">Securing connection</h2>
                <p className="mt-2 text-sm text-[#86868b]">Generating cryptographic intent...</p>
              </motion.div>
            )}

            {/* Step 2: Pay (QR Code) */}
            {step === "pay" && (
              <motion.div key="pay" initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -20 }} transition={{ duration: 0.3 }} className="flex flex-col">
                <h2 className="text-2xl font-bold tracking-tight text-[#1d1d1f]">Scan to pay</h2>
                <p className="mt-2 text-sm text-[#86868b]">Open your identiPay wallet app and scan the QR code to approve the payment securely.</p>

                <div className="mt-8 relative overflow-hidden rounded-3xl border border-black/[0.04] bg-white p-8 shadow-[0_8px_40px_rgba(0,0,0,0.08)]">
                  <div className="flex flex-col items-center">
                    <div className="rounded-2xl border border-black/[0.04] bg-white p-4 shadow-sm">
                      <QRCodeSVG value={qrPayload} size={200} level="M" bgColor="#ffffff" fgColor="#0a0a0a" style={{ width: "100%", height: "auto", maxWidth: 220 }} />
                    </div>

                    <div className="mt-6 flex items-center justify-center gap-2 bg-[#f8f8fa] py-2 px-4 rounded-full">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={countdown < 60 ? "#ef4444" : "#86868b"} strokeWidth="2.5" strokeLinecap="round">
                        <circle cx="12" cy="12" r="10" />
                        <polyline points="12 6 12 12 16 14" />
                      </svg>
                      <span className={`text-xs font-bold tracking-wide ${countdown < 60 ? "text-red-500" : "text-[#86868b]"}`}>
                        EXPIRES IN {formatTime(countdown)}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="mt-6 flex items-center justify-center gap-2 text-sm text-[#86868b]">
                  <div className="h-3 w-3 animate-spin rounded-full border-2 border-[#e6e6eb] border-t-black" />
                  <span className="font-medium">Waiting for payment...</span>
                </div>

                <button onClick={() => setStep("review")} className="mt-4 w-full text-center text-sm font-medium text-[#86868b] transition-colors hover:text-[#1d1d1f]">
                  Cancel payment
                </button>
              </motion.div>
            )}

            {/* Step 2.5: Confirming */}
            {step === "confirming" && (
              <motion.div key="confirming" initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }} transition={{ duration: 0.3 }} className="flex flex-col text-center">
                <div className="flex justify-center mb-10">
                  <div className="relative">
                    <div className="h-20 w-20 rounded-full border border-[#e6e6eb] bg-[#fbfbfd]" />
                    <div className="absolute inset-0 animate-spin-slow rounded-full border-[3px] border-transparent border-t-black border-l-black" />
                    <div className="absolute inset-0 flex items-center justify-center">
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#1d1d1f" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                      </svg>
                    </div>
                  </div>
                </div>

                <h2 className="text-2xl font-bold text-[#1d1d1f]">Processing...</h2>
                <p className="mt-2 text-sm text-[#86868b]">Verifying cryptographic proofs on Sui.</p>

                <div className="mt-10 mx-auto w-full max-w-xs space-y-4">
                  <ConfirmStep label="Intent verified" done />
                  {maxAgeGate > 0 && <ConfirmStep label={`ZK age proof verified (${maxAgeGate}+)`} done />}
                  <ConfirmStep label="Stealth address derived" done />
                  <ConfirmStep label="Atomic settlement" active />
                  <ConfirmStep label="Minting payload receipt" />
                </div>
              </motion.div>
            )}

            {/* Step 3: Success */}
            {step === "success" && (
              <motion.div key="success" initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.5 }} className="flex flex-col text-center">
                <div className="flex justify-center mb-6">
                  <div className="relative">
                    <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: "spring", bounce: 0.5, duration: 0.6 }} className="flex h-20 w-20 items-center justify-center rounded-full bg-[#34d399]">
                      <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                        <polyline points="20 6 9 17 4 12" className="animate-check" />
                      </svg>
                    </motion.div>
                    <div className="absolute inset-0 rounded-full bg-[#34d399]/20 animate-pulse-ring" />
                  </div>
                </div>

                <h2 className="text-3xl font-bold tracking-tight text-[#1d1d1f]">Payment successful</h2>
                <p className="mt-3 text-base text-[#86868b]">Your order is confirmed. A private receipt has been delivered to your stealth address.</p>

                <div className="mt-8 text-left rounded-2xl border border-black/[0.04] bg-[#fbfbfd] p-6">
                  <h3 className="text-xs font-bold text-[#86868b] uppercase tracking-wider mb-4">Transaction Details</h3>
                  <div className="space-y-3">
                    <DetailRow label="Transaction ID" value={transactionId ? transactionId.slice(0, 8) + "..." : "—"} mono />
                    {proposal?.intentHash && <DetailRow label="Intent hash" value={proposal.intentHash.slice(0, 10) + "..." + proposal.intentHash.slice(-6)} mono />}
                    <DetailRow label="Tx hash" value={txHash.slice(0, 10) + "..." + txHash.slice(-6)} mono />
                    <DetailRow label="Settlement" value="Atomic" highlight />
                    {maxAgeGate > 0 && <DetailRow label="Age verification" value={`${maxAgeGate}+ (ZK proof)`} highlight />}
                  </div>
                </div>

                <button onClick={() => { clearCart(); onBack(); }} className="mt-8 w-full rounded-xl bg-black py-4 text-sm font-semibold text-white shadow-lg shadow-black/10 transition-all hover:bg-[#1d1d1f] active:scale-[0.98]">
                  Return to store
                </button>
              </motion.div>
            )}

          </AnimatePresence>
        </div>
      </div>
    </div>
  );
}

/* ── Sub-components ── */

function DetailRow({ label, value, mono, highlight }: { label: string; value: string; mono?: boolean; highlight?: boolean; }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-sm text-[#86868b]">{label}</span>
      <span className={`text-sm font-medium ${highlight ? "text-[#34d399]" : mono ? "font-mono tracking-tight text-[#1d1d1f]" : "text-[#1d1d1f]"}`}>
        {value}
      </span>
    </div>
  );
}

function ConfirmStep({ label, done, active }: { label: string; done?: boolean; active?: boolean; }) {
  return (
    <div className="flex items-center gap-4 py-2">
      {done ? (
        <div className="flex h-6 w-6 items-center justify-center rounded-full bg-[#34d399]">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
        </div>
      ) : active ? (
        <div className="flex h-6 w-6 items-center justify-center">
          <div className="h-5 w-5 animate-spin rounded-full border-[2.5px] border-[#e6e6eb] border-t-black" />
        </div>
      ) : (
        <div className="flex h-6 w-6 items-center justify-center">
          <div className="h-3 w-3 rounded-full bg-[#e6e6eb]" />
        </div>
      )}
      <span className={`text-sm font-medium ${done ? "text-[#1d1d1f]" : active ? "text-[#1d1d1f]" : "text-[#86868b]"}`}>
        {label}
      </span>
    </div>
  );
}
