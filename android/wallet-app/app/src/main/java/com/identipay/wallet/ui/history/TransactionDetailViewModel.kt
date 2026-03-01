package com.identipay.wallet.ui.history

import android.util.Base64
import android.util.Log
import androidx.lifecycle.SavedStateHandle
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Named

data class TransactionDetailUiState(
    val transaction: TransactionEntity? = null,
    val isLoading: Boolean = true,
    val decryptedReceipt: String? = null,
    val decryptedWarranty: String? = null,
    val decryptionError: String? = null,
)

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionDao: TransactionDao,
    private val stealthAddressDao: StealthAddressDao,
    private val artifactEncryption: ArtifactEncryption,
    @param:Named("suiRpc") private val suiRpcClient: HttpClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val TAG = "TxDetailVM"
    }

    private val txDigest: String = savedStateHandle["txDigest"] ?: ""
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(TransactionDetailUiState())
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    init {
        if (txDigest.isNotBlank()) {
            loadTransaction()
        }
    }

    private fun loadTransaction() {
        viewModelScope.launch {
            try {
                val tx = transactionDao.getByDigest(txDigest)
                _uiState.update { it.copy(transaction = tx, isLoading = false) }

                if (tx != null && tx.type == "commerce") {
                    tryDecryptArtifacts(tx)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load transaction", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun tryDecryptArtifacts(tx: TransactionEntity) {
        try {
            // Use the buyer's stealth address (where receipt/warranty were delivered)
            val buyerAddr = tx.buyerStealthAddress
            if (buyerAddr == null) {
                Log.w(TAG, "No buyer stealth address stored for tx ${tx.txDigest}")
                return
            }

            // Fetch on-chain transaction to find receipt/warranty objects
            val txBlock = fetchTransactionBlock(tx.txDigest) ?: return

            // Extract created objects from the transaction effects
            val objects = extractCreatedObjects(txBlock)

            // Find the buyer's stealth key for decryption
            val stealthEntity = stealthAddressDao.getByAddress(buyerAddr) ?: run {
                Log.w(TAG, "Stealth address entity not found for $buyerAddr")
                return
            }
            val stealthPrivKey = Base64.decode(stealthEntity.stealthPrivKeyEnc, Base64.NO_WRAP)

            // Merchant's X25519 public key (needed for ECDH in decrypt)
            val merchantPubKeyHex = tx.merchantPublicKey
            if (merchantPubKeyHex == null) {
                Log.w(TAG, "No merchant public key stored for tx ${tx.txDigest}")
                return
            }
            val merchantPubKey = hexToBytes(merchantPubKeyHex)

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
                            _uiState.update {
                                it.copy(decryptedReceipt = String(plaintext, Charsets.UTF_8))
                            }
                        }
                    } else if (objectType.contains("warranty::WarrantyObject")) {
                        val fields = objectData["fields"]?.jsonObject ?: continue
                        val encTerms = extractBytesField(fields, "encrypted_terms")
                        val nonce = extractBytesField(fields, "terms_nonce")

                        if (encTerms != null && nonce != null) {
                            val plaintext = artifactEncryption.decrypt(
                                stealthPrivKey, merchantPubKey, encTerms, nonce,
                            )
                            _uiState.update {
                                it.copy(decryptedWarranty = String(plaintext, Charsets.UTF_8))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to process object $objectId", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Artifact decryption failed", e)
            _uiState.update { it.copy(decryptionError = e.message) }
        }
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

    private suspend fun fetchObject(objectId: String): kotlinx.serialization.json.JsonObject? {
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

    private fun extractBytesField(fields: kotlinx.serialization.json.JsonObject, fieldName: String): ByteArray? {
        val value = fields[fieldName] ?: return null
        return try {
            val arr = value.jsonArray
            ByteArray(arr.size) { i -> arr[i].jsonPrimitive.content.toInt().toByte() }
        } catch (e: Exception) {
            null
        }
    }
}
