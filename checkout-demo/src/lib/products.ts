export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  currency: string;
  image: string;
  category: string;
  ageGate?: number;
}

export const PRODUCTS: Product[] = [
  {
    id: "headphones-pro",
    name: "Studio Monitor Headphones",
    description: "Premium over-ear headphones with active noise cancellation and 40mm drivers",
    price: 0.25,
    currency: "USDC",
    image: "/products/headphones.svg",
    category: "Audio",
  },
  {
    id: "mechanical-keyboard",
    name: "Mechanical Keyboard",
    description: "Hot-swappable switches, per-key RGB, aluminum frame with PBT keycaps",
    price: 0.18,
    currency: "USDC",
    image: "/products/keyboard.svg",
    category: "Peripherals",
  },
  {
    id: "wireless-charger",
    name: "Wireless Charging Pad",
    description: "15W Qi2 magnetic alignment, USB-C, compatible with all modern devices",
    price: 0.05,
    currency: "USDC",
    image: "/products/charger.svg",
    category: "Accessories",
  },
  {
    id: "usb-hub",
    name: "USB-C Hub 7-in-1",
    description: "HDMI 4K@60Hz, 100W PD pass-through, SD/microSD, USB 3.2 Gen 2",
    price: 0.07,
    currency: "USDC",
    image: "/products/hub.svg",
    category: "Accessories",
  },
  {
    id: "desk-lamp",
    name: "LED Desk Lamp Pro",
    description: "Adjustable color temperature, wireless charging base, touch controls",
    price: 0.09,
    currency: "USDC",
    image: "/products/lamp.svg",
    category: "Lighting",
  },
  {
    id: "webcam-4k",
    name: "4K Webcam",
    description: "Auto-focus, HDR, dual noise-cancelling mics, privacy shutter",
    price: 0.13,
    currency: "USDC",
    image: "/products/webcam.svg",
    category: "Peripherals",
  },
  {
    id: "vaporizer-pro",
    name: "Premium Vaporizer Kit",
    description: "Variable wattage, OLED display, ceramic coil, USB-C fast charge. Age verification required.",
    price: 0.15,
    currency: "USDC",
    image: "/products/vaporizer.svg",
    category: "18+",
    ageGate: 18,
  },
];

export const MERCHANT = {
  name: "TechVault",
  address: "0x9f9a52525712f64c6225f076857cb5c32096c203a499760f43749ee360d4a5fa",
  hostname: "techvault.store",
  did: "did:identipay:techvault.store:ccd20e4a-ae80-49f3-862c-2f05c2714d1b",
  publicKey: "a6317b7521f98e96e8ac16dab916af8fdc3f65be3e7305954f219f6ca64dcdb5",
  tagline: "Premium tech essentials",
};
