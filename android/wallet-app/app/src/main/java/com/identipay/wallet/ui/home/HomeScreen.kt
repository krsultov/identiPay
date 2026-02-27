package com.identipay.wallet.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.identipay.wallet.data.db.entity.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSend: () -> Unit = {},
    onReceive: () -> Unit = {},
    onHistory: () -> Unit = {},
    onScan: () -> Unit = {},
    onStealthAddresses: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "identiPay",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(8.dp))

                uiState.registeredName?.let { name ->
                    Text(
                        text = "@$name.idpay",
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Balance",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "${uiState.balance} USDC",
                    style = MaterialTheme.typography.displaySmall,
                )

                TextButton(onClick = onStealthAddresses) {
                    Text(
                        text = "Stealth Addresses",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Send / Receive / Scan buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(
                        onClick = onSend,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Send")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onReceive,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Receive")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onScan,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Scan")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Recent transactions header
                if (uiState.recentTransactions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Recent",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(onClick = onHistory) {
                            Text("View All")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            items(uiState.recentTransactions) { tx ->
                RecentTransactionCard(tx)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.recentTransactions.isEmpty() && !uiState.isLoading) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No transactions yet.\nSend or receive USDC to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentTransactionCard(tx: TransactionEntity) {
    val isSend = tx.type == "send"
    val whole = tx.amount / 1_000_000
    val frac = (tx.amount % 1_000_000) / 10_000
    val amountStr = "%d.%02d".format(whole, frac)
    val dateStr = SimpleDateFormat("MMM d", Locale.US).format(Date(tx.timestamp))

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = if (isSend) "Sent" else "Received",
                    style = MaterialTheme.typography.bodyMedium,
                )
                tx.counterpartyName?.let { name ->
                    Text(
                        text = "@$name.idpay",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isSend) "-" else "+"}$amountStr",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSend) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
