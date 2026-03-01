package com.identipay.identipaypos.data

data class Product(
    val id: String,
    val name: String,
    val description: String,
    val price: Double,
    val currency: String = "USDC",
    val category: String,
    val ageGate: Int? = null,
)

object Merchant {
    const val NAME = "TechVault"
    const val ADDRESS = "0x9f9a52525712f64c6225f076857cb5c32096c203a499760f43749ee360d4a5fa"
    const val HOSTNAME = "techvault.store"
    const val DID = "did:identipay:techvault.store:ccd20e4a-ae80-49f3-862c-2f05c2714d1b"
    const val PUBLIC_KEY = "a6317b7521f98e96e8ac16dab916af8fdc3f65be3e7305954f219f6ca64dcdb5"
    const val TAGLINE = "Premium tech essentials"
}

val PRODUCTS = listOf(
    Product(
        id = "headphones-pro",
        name = "Studio Monitor Headphones",
        description = "Premium over-ear headphones with active noise cancellation and 40mm drivers",
        price = 0.25,
        category = "Audio",
    ),
    Product(
        id = "mechanical-keyboard",
        name = "Mechanical Keyboard",
        description = "Hot-swappable switches, per-key RGB, aluminum frame with PBT keycaps",
        price = 0.18,
        category = "Peripherals",
    ),
    Product(
        id = "wireless-charger",
        name = "Wireless Charging Pad",
        description = "15W Qi2 magnetic alignment, USB-C, compatible with all modern devices",
        price = 0.05,
        category = "Accessories",
    ),
    Product(
        id = "usb-hub",
        name = "USB-C Hub 7-in-1",
        description = "HDMI 4K@60Hz, 100W PD pass-through, SD/microSD, USB 3.2 Gen 2",
        price = 0.07,
        category = "Accessories",
    ),
    Product(
        id = "desk-lamp",
        name = "LED Desk Lamp Pro",
        description = "Adjustable color temperature, wireless charging base, touch controls",
        price = 0.09,
        category = "Lighting",
    ),
    Product(
        id = "webcam-4k",
        name = "4K Webcam",
        description = "Auto-focus, HDR, dual noise-cancelling mics, privacy shutter",
        price = 0.13,
        category = "Peripherals",
    ),
    Product(
        id = "vaporizer-pro",
        name = "Premium Vaporizer Kit",
        description = "Variable wattage, OLED display, ceramic coil, USB-C fast charge. Age verification required.",
        price = 0.15,
        category = "18+",
        ageGate = 18,
    ),
)
