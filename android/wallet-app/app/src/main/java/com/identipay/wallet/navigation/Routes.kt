package com.identipay.wallet.navigation

sealed class Route(val route: String) {
    object Home : Route("home")
    object IdentityFlow : Route("identity_flow")
    object IdentitySetup : Route("identity_setup")
    object MrzScan : Route("mrz_scan")
    object NfcScan : Route("nfc_scan")
    object NamePicker : Route("name_picker")
}
