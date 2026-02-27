"use client";

import Image from "next/image";
import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { PRODUCTS, MERCHANT } from "@/lib/products";
import { CartProvider, useCart } from "@/lib/cart";
import Logo from "@/components/Logo";
import CheckoutPage from "@/components/CheckoutPage";

function StorePage() {
  const { addItem, items, itemCount, total } = useCart();
  const [showCheckout, setShowCheckout] = useState(false);
  const [addedId, setAddedId] = useState<string | null>(null);

  const handleAdd = (product: (typeof PRODUCTS)[number]) => {
    addItem(product);
    setAddedId(product.id);
    setTimeout(() => setAddedId(null), 800);
  };

  if (showCheckout) {
    return <CheckoutPage onBack={() => setShowCheckout(false)} />;
  }

  return (
    <div className="min-h-screen bg-[#f8f8fa]">
      {/* Header */}
      <header className="sticky top-0 z-50 border-b border-black/[0.04] bg-white/80 backdrop-blur-xl">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-4">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-[#0a0a0a]">
              <span className="text-sm font-bold text-white">TV</span>
            </div>
            <div>
              <h1 className="text-base font-semibold text-[#1d1d1f]">
                {MERCHANT.name}
              </h1>
              <p className="text-xs text-[#86868b]">{MERCHANT.tagline}</p>
            </div>
          </div>

          <button
            onClick={() => itemCount > 0 && setShowCheckout(true)}
            className="relative flex items-center gap-2 rounded-full border border-black/[0.08] px-4 py-2 text-sm font-medium text-[#1d1d1f] transition-all hover:border-black/[0.16] hover:shadow-sm disabled:opacity-40 disabled:cursor-not-allowed"
            disabled={itemCount === 0}
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z" />
              <line x1="3" y1="6" x2="21" y2="6" />
              <path d="M16 10a4 4 0 01-8 0" />
            </svg>
            Checkout
            {itemCount > 0 && (
              <motion.span
                initial={{ scale: 0 }}
                animate={{ scale: 1 }}
                className="flex h-5 min-w-5 items-center justify-center rounded-full bg-[#0a0a0a] px-1.5 text-[11px] font-semibold text-white"
              >
                {itemCount}
              </motion.span>
            )}
          </button>
        </div>
      </header>

      {/* Hero */}
      <div className="mx-auto max-w-6xl px-6 pt-16 pb-10">
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5 }}
        >
          <p className="mb-3 text-sm font-medium text-[#86868b]">
            Demo Store
          </p>
          <h2 className="text-4xl font-semibold tracking-tight text-[#1d1d1f] sm:text-5xl">
            Premium Tech Essentials
          </h2>
          <p className="mt-4 max-w-xl text-lg text-[#86868b]">
            Checkout powered by identiPay — privacy-preserving atomic settlement
            on Sui. No address reuse, no data leaks.
          </p>
        </motion.div>
      </div>

      {/* Products Grid */}
      <div className="mx-auto max-w-6xl px-6 pb-24">
        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {PRODUCTS.map((product, i) => {
            const inCart = items.find((it) => it.product.id === product.id);
            const justAdded = addedId === product.id;

            return (
              <motion.div
                key={product.id}
                initial={{ opacity: 0, y: 16 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.4, delay: i * 0.06 }}
                className="group overflow-hidden rounded-2xl border border-black/[0.06] bg-white transition-all duration-500 hover:-translate-y-1 hover:shadow-[0_20px_60px_rgba(0,0,0,0.06)]"
              >
                <div className="relative aspect-square overflow-hidden bg-[#f5f5f7]">
                  <Image
                    src={product.image}
                    alt={product.name}
                    fill
                    className="object-contain p-8 transition-transform duration-500 group-hover:scale-105"
                  />
                  <span className="absolute top-4 left-4 rounded-full bg-white/80 px-3 py-1 text-[11px] font-medium text-[#86868b] backdrop-blur-sm">
                    {product.category}
                  </span>
                </div>

                <div className="p-5">
                  <h3 className="text-base font-semibold text-[#1d1d1f]">
                    {product.name}
                  </h3>
                  <p className="mt-1 text-sm leading-relaxed text-[#86868b]">
                    {product.description}
                  </p>

                  <div className="mt-4 flex items-center justify-between">
                    <div className="flex items-baseline gap-1">
                      <span className="text-lg font-semibold text-[#1d1d1f]">
                        {product.price.toFixed(2)}
                      </span>
                      <span className="text-xs font-medium text-[#86868b]">
                        {product.currency}
                      </span>
                    </div>

                    <button
                      onClick={() => handleAdd(product)}
                      className="relative flex items-center gap-1.5 rounded-full bg-[#0a0a0a] px-4 py-2 text-sm font-medium text-white transition-all hover:bg-[#1d1d1f] active:scale-95"
                    >
                      <AnimatePresence mode="wait">
                        {justAdded ? (
                          <motion.span
                            key="check"
                            initial={{ opacity: 0, scale: 0.5 }}
                            animate={{ opacity: 1, scale: 1 }}
                            exit={{ opacity: 0, scale: 0.5 }}
                          >
                            <svg
                              width="14"
                              height="14"
                              viewBox="0 0 24 24"
                              fill="none"
                              stroke="currentColor"
                              strokeWidth="2.5"
                              strokeLinecap="round"
                              strokeLinejoin="round"
                            >
                              <polyline points="20 6 9 17 4 12" />
                            </svg>
                          </motion.span>
                        ) : (
                          <motion.span
                            key="plus"
                            initial={{ opacity: 0, scale: 0.5 }}
                            animate={{ opacity: 1, scale: 1 }}
                            exit={{ opacity: 0, scale: 0.5 }}
                          >
                            <svg
                              width="14"
                              height="14"
                              viewBox="0 0 24 24"
                              fill="none"
                              stroke="currentColor"
                              strokeWidth="2.5"
                              strokeLinecap="round"
                            >
                              <line x1="12" y1="5" x2="12" y2="19" />
                              <line x1="5" y1="12" x2="19" y2="12" />
                            </svg>
                          </motion.span>
                        )}
                      </AnimatePresence>
                      {inCart ? `Add (${inCart.quantity})` : "Add"}
                    </button>
                  </div>
                </div>
              </motion.div>
            );
          })}
        </div>
      </div>

      {/* Sticky bottom bar when items in cart */}
      <AnimatePresence>
        {itemCount > 0 && (
          <motion.div
            initial={{ y: 100 }}
            animate={{ y: 0 }}
            exit={{ y: 100 }}
            transition={{ type: "spring", bounce: 0.15 }}
            className="fixed bottom-0 left-0 right-0 z-50 border-t border-black/[0.06] bg-white/90 backdrop-blur-xl"
          >
            <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-4">
              <div>
                <p className="text-sm text-[#86868b]">
                  {itemCount} {itemCount === 1 ? "item" : "items"}
                </p>
                <p className="text-lg font-semibold text-[#1d1d1f]">
                  {total.toFixed(2)} USDC
                </p>
              </div>
              <button
                onClick={() => setShowCheckout(true)}
                className="flex items-center gap-2 rounded-full bg-[#0a0a0a] px-6 py-3 text-sm font-semibold text-white transition-all hover:bg-[#1d1d1f] active:scale-[0.97]"
              >
                Checkout with identiPay
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <line x1="5" y1="12" x2="19" y2="12" />
                  <polyline points="12 5 19 12 12 19" />
                </svg>
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Footer */}
      <footer className="border-t border-black/[0.04] bg-white py-8">
        <div className="mx-auto flex max-w-6xl flex-col items-center gap-4 px-6 sm:flex-row sm:justify-between">
          <div className="flex items-center gap-2 text-[#86868b]">
            <span className="text-xs">Powered by</span>
            <Logo className="h-4 text-[#1d1d1f]" />
          </div>
          <p className="text-xs text-[#86868b]">
            Demo store for integration showcase. No real transactions.
          </p>
        </div>
      </footer>
    </div>
  );
}

export default function Home() {
  return (
    <CartProvider>
      <StorePage />
    </CartProvider>
  );
}
