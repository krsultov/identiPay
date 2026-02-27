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
            "0xd1e2ab2d699d37ca57f06005328975e4854785e96f44340bdb0eb8257d4136aa"
        const val META_REGISTRY_ID =
            "0x364ecd9ef42701316f2ce88eee8c55c928dfdeb0403ce582a45adf1406f23306"
        const val SETTLEMENT_STATE_ID =
            "0xee2855a6b4194019cc2091fe4ea1ecbfc18f04d1a3ff9bfbbd4122d9f28404a1"
        const val VERIFICATION_KEY_ID =
            "0xe8600d373af9019af92139cfd24489cfc7723fcb06dd812697d1f900b9e537a0"

        const val SUI_TESTNET_URL = "https://fullnode.testnet.sui.io:443"
    }
}
