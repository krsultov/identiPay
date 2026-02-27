package com.identipay.wallet.network

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WsMessage(
    val type: String,
    val transactionId: String? = null,
    val status: String? = null,
    val suiTxDigest: String? = null,
    val message: String? = null,
)

enum class TransactionStatus {
    PENDING, SETTLED, EXPIRED, CANCELLED, ERROR
}

data class TxStatusUpdate(
    val status: TransactionStatus,
    val suiTxDigest: String? = null,
    val errorMessage: String? = null,
)

/**
 * Ktor WebSocket client for transaction status updates.
 * Connects to wss://identipay.me/ws/transactions/:txId
 */
@Singleton
class WebSocketManager @Inject constructor() {

    companion object {
        private const val TAG = "WebSocketManager"
        private const val WS_HOST = "identipay.me"
        private const val WS_PATH_PREFIX = "/ws/transactions/"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Observe transaction status updates via WebSocket.
     * Emits status updates as they arrive; completes when the connection closes.
     */
    fun observe(txId: String): Flow<TxStatusUpdate> = callbackFlow {
        val client = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            install(io.ktor.client.plugins.websocket.WebSockets)
        }

        try {
            client.webSocket(
                host = WS_HOST,
                path = "$WS_PATH_PREFIX$txId",
            ) {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        try {
                            val msg = json.decodeFromString<WsMessage>(text)
                            val update = when (msg.type) {
                                "status" -> TxStatusUpdate(
                                    status = parseStatus(msg.status),
                                    suiTxDigest = msg.suiTxDigest,
                                )
                                "settlement" -> TxStatusUpdate(
                                    status = TransactionStatus.SETTLED,
                                    suiTxDigest = msg.suiTxDigest,
                                )
                                "error" -> TxStatusUpdate(
                                    status = TransactionStatus.ERROR,
                                    errorMessage = msg.message,
                                )
                                else -> null
                            }
                            update?.let { trySend(it) }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse WS message: $text", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket error for tx $txId", e)
            trySend(TxStatusUpdate(TransactionStatus.ERROR, errorMessage = e.message))
        } finally {
            client.close()
        }

        awaitClose()
    }

    private fun parseStatus(status: String?): TransactionStatus = when (status) {
        "pending" -> TransactionStatus.PENDING
        "settled" -> TransactionStatus.SETTLED
        "expired" -> TransactionStatus.EXPIRED
        "cancelled" -> TransactionStatus.CANCELLED
        else -> TransactionStatus.PENDING
    }
}
