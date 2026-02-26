package com.identipay.wallet.network

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides Sui client configuration and contract object IDs.
 */
@Singleton
class SuiClientProvider @Inject constructor() {

    companion object {
        // Testnet contract object IDs
        const val PACKAGE_ID =
            "0x421ef10b138561c401d26a2b425d968462b68c88e2e7038220ecf8d76197ed60"
        const val META_REGISTRY_ID =
            "0xfbf7d2bbd3648405fb39efb555c87b82a4060b21ba4e8f8eccc46f9f5c5b159f"
        const val SETTLEMENT_STATE_ID =
            "0x67efba465370c481be98bef8a5a94fb2bd21bbd214314eb9912458e238d7e0e2"
        const val VERIFICATION_KEY_ID =
            "0x684794997aa68cee5d8e16f3204f13ac7d3a422ee9b8085fa9abee99deb2e3b9"

        const val SUI_TESTNET_URL = "https://fullnode.testnet.sui.io:443"
    }
}
