package com.identipay.wallet.ui.artifacts

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.wallet.crypto.ArtifactEncryption
import com.identipay.wallet.data.db.dao.StealthAddressDao
import com.identipay.wallet.data.db.dao.TransactionDao
import com.identipay.wallet.data.db.entity.TransactionEntity
import com.identipay.wallet.network.SuiClientProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Named

sealed class ArtifactItem(
    val txDigest: String,
    val timestamp: Long,
    val counterpartyName: String?,
) {
    class Receipt(
        txDigest: String,
        timestamp: Long,
        counterpartyName: String?,
        val receiptJson: JsonObject,
    ) : ArtifactItem(txDigest, timestamp, counterpartyName)

    class Warranty(
        txDigest: String,
        timestamp: Long,
        counterpartyName: String?,
        val warrantyJson: JsonObject,
    ) : ArtifactItem(txDigest, timestamp, counterpartyName)
}

data class ArtifactsUiState(
    val isLoading: Boolean = true,
    val artifacts: List<ArtifactItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class ArtifactsViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val stealthAddressDao: StealthAddressDao,
    private val artifactEncryption: ArtifactEncryption,
    @param:Named("suiRpc") private val suiRpcClient: HttpClient,
) : ViewModel() {

    companion object {
        private const val TAG = "ArtifactsVM"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, List<ArtifactItem>>()

    private val _uiState = MutableStateFlow(ArtifactsUiState())
    val uiState: StateFlow<ArtifactsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            transactionDao.getCommerce().collect { txList ->
                loadArtifacts(txList)
            }
        }
    }

    private suspend fun loadArtifacts(txList: List<TransactionEntity>) {
        _uiState.update { it.copy(isLoading = true) }
        val allArtifacts = mutableListOf<ArtifactItem>()

        for (tx in txList) {
            val cached = cache[tx.txDigest]
            if (cached != null) {
                allArtifacts.addAll(cached)
                continue
            }

            val items = tryDecryptArtifacts(tx)
            cache[tx.txDigest] = items
            allArtifacts.addAll(items)
        }

        _uiState.update { it.copy(isLoading = false, artifacts = allArtifacts) }
    }

    private suspend fun tryDecryptArtifacts(tx: TransactionEntity): List<ArtifactItem> {
        val result = mutableListOf<ArtifactItem>()
        try {
            val buyerAddr = tx.buyerStealthAddress ?: return emptyList()
            val merchantPubKeyHex = tx.merchantPublicKey ?: return emptyList()

            val stealthEntity = stealthAddressDao.getByAddress(buyerAddr) ?: return emptyList()
            val stealthPrivKey = Base64.decode(stealthEntity.stealthPrivKeyEnc, Base64.NO_WRAP)
            val merchantPubKey = hexToBytes(merchantPubKeyHex)

            val txBlock = fetchTransactionBlock(tx.txDigest) ?: return emptyList()
            val objects = extractCreatedObjects(txBlock)

            for (objectId in objects) {
                try {
                    val objectData = fetchObject(objectId) ?: continue
                    val objectType = objectData["type"]?.jsonPrimitive?.content ?: continue

                    if (objectType.contains("receipt::ReceiptObject")) {
                        val fields = objectData["fields"]?.jsonObject ?: continue
                        val encPayload = extractBytesField(fields, "encrypted_payload")
                        val nonce = extractBytesField(fields, "payload_nonce")

                        if (encPayload != null && nonce != null) {
                            val plaintext = artifactEncryption.decrypt(
                                stealthPrivKey, merchantPubKey, encPayload, nonce,
                            )
                            val parsed = json.parseToJsonElement(
                                String(plaintext, Charsets.UTF_8),
                            ).jsonObject
                            result.add(
                                ArtifactItem.Receipt(
                                    txDigest = tx.txDigest,
                                    timestamp = tx.timestamp,
                                    counterpartyName = tx.counterpartyName,
                                    receiptJson = parsed,
                                ),
                            )
                        }
                    } else if (objectType.contains("warranty::WarrantyObject")) {
                        val fields = objectData["fields"]?.jsonObject ?: continue
                        val encTerms = extractBytesField(fields, "encrypted_terms")
                        val nonce = extractBytesField(fields, "terms_nonce")

                        if (encTerms != null && nonce != null) {
                            val plaintext = artifactEncryption.decrypt(
                                stealthPrivKey, merchantPubKey, encTerms, nonce,
                            )
                            val parsed = json.parseToJsonElement(
                                String(plaintext, Charsets.UTF_8),
                            ).jsonObject
                            result.add(
                                ArtifactItem.Warranty(
                                    txDigest = tx.txDigest,
                                    timestamp = tx.timestamp,
                                    counterpartyName = tx.counterpartyName,
                                    warrantyJson = parsed,
                                ),
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to process object $objectId", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Artifact decryption failed for tx ${tx.txDigest}", e)
        }
        return result
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = if (hex.startsWith("0x")) hex.substring(2) else hex
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private suspend fun fetchTransactionBlock(digest: String): JsonElement? {
        val body = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "sui_getTransactionBlock",
                "params": ["$digest", {"showEffects": true, "showObjectChanges": true}]
            }
        """.trimIndent()

        val response = suiRpcClient.post(SuiClientProvider.SUI_TESTNET_URL) {
            setBody(TextContent(body, ContentType.Application.Json))
        }.body<String>()

        val parsed = json.parseToJsonElement(response).jsonObject
        return parsed["result"]
    }

    private fun extractCreatedObjects(txBlock: JsonElement): List<String> {
        val objectChanges = txBlock.jsonObject["objectChanges"]?.jsonArray ?: return emptyList()
        return objectChanges.mapNotNull { change ->
            val changeObj = change.jsonObject
            val changeType = changeObj["type"]?.jsonPrimitive?.content
            if (changeType == "created") {
                changeObj["objectId"]?.jsonPrimitive?.content
            } else null
        }
    }

    private suspend fun fetchObject(objectId: String): JsonObject? {
        val body = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "sui_getObject",
                "params": ["$objectId", {"showContent": true}]
            }
        """.trimIndent()

        val response = suiRpcClient.post(SuiClientProvider.SUI_TESTNET_URL) {
            setBody(TextContent(body, ContentType.Application.Json))
        }.body<String>()

        val parsed = json.parseToJsonElement(response).jsonObject
        return parsed["result"]?.jsonObject?.get("data")?.jsonObject?.get("content")?.jsonObject
    }

    private fun extractBytesField(fields: JsonObject, fieldName: String): ByteArray? {
        val value = fields[fieldName] ?: return null
        return try {
            val arr = value.jsonArray
            ByteArray(arr.size) { i -> arr[i].jsonPrimitive.content.toInt().toByte() }
        } catch (e: Exception) {
            null
        }
    }
}
