package com.identipay.wallet.navigation

sealed class Route(val route: String) {
    object Home : Route("home")
    object IdentityFlow : Route("identity_flow")
    object IdentitySetup : Route("identity_setup")
    object MrzScan : Route("mrz_scan")
    object NfcScan : Route("nfc_scan")
    object PinEntry : Route("pin_entry")
    object NamePicker : Route("name_picker")
    object Send : Route("send")
    object Receive : Route("receive")
    object History : Route("history")
    object Scanner : Route("scanner")
    object CheckoutFlow : Route("checkout_flow/{txId}") {
        fun create(txId: String) = "checkout_flow/$txId"
    }
    object ProposalReview : Route("checkout/{txId}") {
        fun create(txId: String) = "checkout/$txId"
    }
    object Confirm : Route("confirm/{txId}/{txDigest}") {
        fun create(txId: String, txDigest: String) = "confirm/$txId/$txDigest"
    }
    object StealthAddresses : Route("stealth_addresses")
}
