package com.identipay.wallet.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    viewModel: TransactionDetailViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.transaction == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Transaction not found", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val tx = uiState.transaction!!
            val isSend = tx.type == "send" || tx.type == "commerce"
            val amountStr = formatAmount(tx.amount)
            val dateStr = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US)
                .format(Date(tx.timestamp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Hero amount card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSend) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "${if (isSend) "-" else "+"}$amountStr USDC",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSend) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (tx.type) {
                                "send" -> "Sent"
                                "receive" -> "Received"
                                "commerce" -> "Purchase"
                                else -> tx.type.replaceFirstChar { it.uppercase() }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSend) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Details card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        DetailRow("Date", dateStr)

                        tx.counterpartyName?.let { name ->
                            DetailRow(
                                if (isSend) "To" else "From",
                                "@$name.idpay",
                            )
                        }

                        DetailRow("Type", tx.type.replaceFirstChar { it.uppercase() })

                        DetailRow(
                            "Tx Digest",
                            tx.txDigest.take(12) + "..." + tx.txDigest.takeLast(8),
                        )
                    }
                }

                // Decrypted receipt
                uiState.decryptedReceipt?.let { receiptJson ->
                    Spacer(modifier = Modifier.height(16.dp))
                    ReceiptCard(receiptJson)
                }

                // Decrypted warranty
                uiState.decryptedWarranty?.let { warrantyJson ->
                    Spacer(modifier = Modifier.height(16.dp))
                    WarrantyCard(warrantyJson)
                }

                uiState.decryptionError?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Could not decrypt artifacts: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ReceiptCard(receiptJson: String) {
    val json = try {
        Json.parseToJsonElement(receiptJson).jsonObject
    } catch (e: Exception) {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Receipt",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (json != null) {
                json["merchant"]?.jsonPrimitive?.content?.let {
                    DetailRow("Merchant", it)
                }

                json["items"]?.jsonArray?.let { items ->
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    items.forEach { item ->
                        val obj = item.jsonObject
                        val name = obj["name"]?.jsonPrimitive?.content ?: "Item"
                        val qty = obj["quantity"]?.jsonPrimitive?.content ?: "1"
                        val price = obj["unitPrice"]?.jsonPrimitive?.content ?: "\u2014"
                        DetailRow("$name x$qty", "$price USDC")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                json["amount"]?.jsonPrimitive?.content?.let {
                    DetailRow("Total", "$it ${json["currency"]?.jsonPrimitive?.content ?: "USDC"}")
                }
                json["transactionId"]?.jsonPrimitive?.content?.let {
                    DetailRow("Transaction ID", it.take(16) + "...")
                }
            } else {
                Text(
                    text = receiptJson,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun WarrantyCard(warrantyJson: String) {
    val json = try {
        Json.parseToJsonElement(warrantyJson).jsonObject
    } catch (e: Exception) {
        null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Warranty",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (json != null) {
                json["merchant"]?.jsonPrimitive?.content?.let {
                    DetailRow("Merchant", it)
                }
                json["durationDays"]?.jsonPrimitive?.content?.let {
                    DetailRow("Duration", "$it days")
                }
                json["transferable"]?.jsonPrimitive?.content?.let {
                    DetailRow("Transferable", if (it == "true") "Yes" else "No")
                }
            } else {
                Text(
                    text = warrantyJson,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private fun formatAmount(micros: Long): String {
    val whole = micros / 1_000_000
    val frac = (micros % 1_000_000) / 10_000
    return "%d.%02d".format(whole, frac)
}
