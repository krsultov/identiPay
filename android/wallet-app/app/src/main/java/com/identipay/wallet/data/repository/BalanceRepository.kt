package com.identipay.wallet.data.repository

import android.util.Log
import com.identipay.wallet.data.db.dao.StealthAddressDao
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Serializable
private data class SuiRpcResponse(
    val result: BalanceResult? = null,
)

@Serializable
private data class BalanceResult(
    val totalBalance: String = "0",
)

@Serializable
private data class SuiTxResponse(
    val result: TxResult? = null,
)

@Serializable
private data class TxResult(
    val balanceChanges: List<BalanceChange>? = null,
    val events: List<SuiEvent>? = null,
    val timestampMs: String? = null,
)

@Serializable
private data class BalanceChange(
    val owner: BalanceOwner? = null,
    val coinType: String = "",
    val amount: String = "0",
)

@Serializable
private data class BalanceOwner(
    @kotlinx.serialization.SerialName("AddressOwner")
    val addressOwner: String? = null,
)

@Serializable
private data class SuiEvent(
    val type: String = "",
    val parsedJson: kotlinx.serialization.json.JsonObject? = null,
)

// suix_getOwnedObjects response types
@Serializable
private data class OwnedObjectsResponse(
    val result: OwnedObjectsResult? = null,
)

@Serializable
private data class OwnedObjectsResult(
    val data: List<OwnedObjectData> = emptyList(),
    val hasNextPage: Boolean = false,
    val nextCursor: String? = null,
)

@Serializable
private data class OwnedObjectData(
    val data: ObjectInfo? = null,
)

@Serializable
private data class ObjectInfo(
    val objectId: String = "",
    val previousTransaction: String? = null,
)

data class RecoveredTransaction(
    val txDigest: String,
    val type: String,     // "receive" or "commerce"
    val amount: Long,
    val merchantName: String? = null,
    val stealthAddress: String,
    val timestamp: Long,
    val buyerStealthAddress: String? = null,
    val merchantAddress: String? = null,
)

@Singleton
class BalanceRepository @Inject constructor(
    private val stealthAddressDao: StealthAddressDao,
    @param:Named("suiRpc") private val httpClient: HttpClient,
) {
    companion object {
        private const val TAG = "BalanceRepository"
        private const val SUI_RPC = "https://fullnode.testnet.sui.io:443"
        private const val USDC_COIN_TYPE = "0xa1ec7fc00a6f40db9693ad1415d0c193ad3906494428cf252621037bd7117e29::usdc::USDC"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Total USDC balance across all stealth addresses as a Flow.
     * Value is in micro-USDC (6 decimals).
     */
    val totalBalance: Flow<Long> = stealthAddressDao.getTotalBalance().map { it ?: 0L }

    /**
     * Formatted balance string (e.g. "12.50").
     */
    val formattedBalance: Flow<String> = totalBalance.map { micros ->
        val whole = micros / 1_000_000
        val frac = (micros % 1_000_000) / 10_000 // 2 decimal places
        "%d.%02d".format(whole, frac)
    }

    /**
     * Refresh balances for all known stealth addresses by querying Sui RPC.
     */
    suspend fun refreshAll() {
        val addresses = stealthAddressDao.getAllOnce()
//        Log.d(TAG, "refreshAll: found ${addresses.size} stealth addresses in DB")
        if (addresses.isEmpty()) {
//            Log.w(TAG, "refreshAll: no stealth addresses — nothing to query")
            return
        }
        for (addr in addresses) {
//            Log.d(TAG, "refreshAll: querying balance for ${addr.stealthAddress}")
            val balance = queryBalance(addr.stealthAddress)
//            Log.d(TAG, "refreshAll: ${addr.stealthAddress} → balance=$balance")
            stealthAddressDao.updateBalance(addr.stealthAddress, balance)
        }
        val dbTotal = stealthAddressDao.getAllOnce().sumOf { it.balanceUsdc }
//        Log.d(TAG, "refreshAll: DB total after update = $dbTotal")
    }

    /**
     * Query transaction info: checks for SettlementEvent (commerce) first,
     * then falls back to USDC balance change (P2P receive).
     */
    suspend fun queryTransactionInfo(txDigest: String, stealthAddress: String): RecoveredTransaction? {
        return try {
            val jsonBody = """{"jsonrpc":"2.0","id":1,"method":"sui_getTransactionBlock","params":["$txDigest",{"showEvents":true,"showBalanceChanges":true}]}"""
            val httpResponse = httpClient.post(SUI_RPC) {
                setBody(TextContent(jsonBody, ContentType.Application.Json))
            }
            val rawBody = httpResponse.body<String>()
            val parsed = json.decodeFromString<SuiTxResponse>(rawBody)
            val result = parsed.result ?: return null
            val timestamp = result.timestampMs?.toLongOrNull() ?: System.currentTimeMillis()

            // Check for SettlementEvent → commerce transaction
            val settlementEvent = result.events?.firstOrNull { it.type.contains("::settlement::SettlementEvent") }
            if (settlementEvent != null) {
                val eventJson = settlementEvent.parsedJson
                val amount = eventJson?.get("amount")?.let {
                    when (it) {
                        is kotlinx.serialization.json.JsonPrimitive -> it.content.toLongOrNull()
                        else -> null
                    }
                } ?: 0L
                val buyerStealthAddr = eventJson?.get("buyer_stealth_address")?.let {
                    when (it) {
                        is kotlinx.serialization.json.JsonPrimitive -> it.content
                        else -> null
                    }
                }
                val merchantAddr = eventJson?.get("merchant")?.let {
                    when (it) {
                        is kotlinx.serialization.json.JsonPrimitive -> it.content
                        else -> null
                    }
                }
                return RecoveredTransaction(
                    txDigest = txDigest,
                    type = "commerce",
                    amount = amount,
                    stealthAddress = stealthAddress,
                    timestamp = timestamp,
                    buyerStealthAddress = buyerStealthAddr,
                    merchantAddress = merchantAddr,
                )
            }

            // Fall back to USDC balance change → P2P receive
            val change = result.balanceChanges?.firstOrNull { bc ->
                bc.coinType == USDC_COIN_TYPE &&
                        bc.owner?.addressOwner == stealthAddress
            }
            val amount = change?.amount?.toLongOrNull()?.let { kotlin.math.abs(it) }
            if (amount != null && amount > 0L) {
                return RecoveredTransaction(
                    txDigest = txDigest,
                    type = "receive",
                    amount = amount,
                    stealthAddress = stealthAddress,
                    timestamp = timestamp,
                )
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "queryTransactionInfo: FAILED for tx=$txDigest", e)
            null
        }
    }

    /**
     * Query ReceiptObjects owned by a stealth address.
     * Returns the creating transaction digest for each receipt found.
     */
    suspend fun queryOwnedReceipts(address: String, receiptType: String): List<String> {
        return try {
            val jsonBody = """{"jsonrpc":"2.0","id":1,"method":"suix_getOwnedObjects","params":["$address",{"filter":{"StructType":"$receiptType"},"options":{"showPreviousTransaction":true}},null,50]}"""
            val httpResponse = httpClient.post(SUI_RPC) {
                setBody(TextContent(jsonBody, ContentType.Application.Json))
            }
            val rawBody = httpResponse.body<String>()
            val parsed = json.decodeFromString<OwnedObjectsResponse>(rawBody)
            parsed.result?.data?.mapNotNull { it.data?.previousTransaction } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "queryOwnedReceipts: FAILED for $address", e)
            emptyList()
        }
    }

    /**
     * Query the USDC balance at a single Sui address via JSON-RPC.
     */
    suspend fun queryBalance(address: String): Long {
        return try {
            val jsonBody = """{"jsonrpc":"2.0","id":1,"method":"suix_getBalance","params":["$address","$USDC_COIN_TYPE"]}"""
//            Log.d(TAG, "queryBalance: POST $SUI_RPC body=$jsonBody")
            val httpResponse = httpClient.post(SUI_RPC) {
                setBody(TextContent(jsonBody, ContentType.Application.Json))
            }
//            Log.d(TAG, "queryBalance: HTTP status=${httpResponse.status}")
            val rawBody = httpResponse.body<String>()
//            Log.d(TAG, "queryBalance: raw response=$rawBody")
            val parsed = json.decodeFromString<SuiRpcResponse>(rawBody)
            val balance = parsed.result?.totalBalance?.toLongOrNull() ?: 0L
//            Log.d(TAG, "queryBalance: parsed totalBalance=${parsed.result?.totalBalance} → $balance")
            balance
        } catch (e: Exception) {
//            Log.e(TAG, "queryBalance: FAILED for $address", e)
            0L
        }
    }
}
