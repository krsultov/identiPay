package com.identipay.wallet.network

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides Sui client configuration and contract object IDs.
 */
@Singleton
class SuiClientProvider @Inject constructor() {

    companion object {
        // Testnet contract object IDs (republished with Poseidon shielded pool)
        const val PACKAGE_ID =
            "0x1d78444dc29300d7ef1fda1cc292b154cba7dce8de68e12371f89179c3fdaf19"
        const val META_REGISTRY_ID =
            "0x8cabbec91c9d1245fa68eb8efd025794f51e9d6583213d8e619f4007c46b312c"
        const val SETTLEMENT_STATE_ID =
            "0x93fd4f555edcf8ed11e2596b0dde3648e89840911ea77aadd5608d79473e5bfc"
        const val VERIFICATION_KEY_ID =
            "0x4ed1d3067b8670c8f7928c6938ca18a993878f4b837565edf2222b9fded5d341"
        const val AGE_CHECK_VK_ID =
            "0xdefefee9c4bbb3af94109fdf7fc141bf2fffb746e9cffeb1a0d5ae5e978db91a"
        const val POOL_SPEND_VK_ID =
            "0x966146185968577d51499ba3a348ac85bcab8f53d3265ef41aa31e39977f2567"
        const val SHIELDED_POOL_ID =
            "0x3994d64f8cad8e6fee026d626e60bf93d08bf5bf26f3ea7084d339c63b11e2bb"
        const val TRUST_REGISTRY_ID =
            "0x4300ec779bf610ac7c54d5b5010da2d57eaadfa99556309de9fdb292c74b0860"

        const val SUI_TESTNET_URL = "https://fullnode.testnet.sui.io:443"
    }
}
