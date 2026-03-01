package com.identipay.identipaypos.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

data class SettlementEvent(
    val suiTxDigest: String,
)

class SettlementListener(
    private val transactionId: String,
    private val onSettlement: (SettlementEvent) -> Unit,
    private val onError: (String) -> Unit = {},
) {
    private var webSocket: WebSocket? = null
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // no timeout for WebSocket
        .build()

    fun connect() {
        val protocol = if (IdentipayApi.BACKEND_URL.startsWith("https")) "wss" else "ws"
        val wsUrl = "$protocol://${IdentipayApi.backendHost}/ws/transactions/$transactionId"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val data = gson.fromJson(text, JsonObject::class.java)
                    val type = data.get("type")?.asString

                    val isSettled = type == "settlement" ||
                            (type == "status" && data.get("status")?.asString == "settled")

                    if (isSettled) {
                        val digest = data.get("suiTxDigest")?.asString ?: ""
                        onSettlement(SettlementEvent(suiTxDigest = digest))
                    }
                } catch (_: Exception) {
                    // ignore parse errors
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onError(t.message ?: "WebSocket connection failed")
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Done")
        webSocket = null
    }
}
