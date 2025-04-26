package com.identipay.identipaypos.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.identipay.identipaypos.LocalPrinterService
import com.identipay.identipaypos.R
import com.identipay.identipaypos.viewmodel.PosViewModel
import java.util.UUID

@Composable
fun PosAppScreen(viewModel: PosViewModel = viewModel()) {

    val uiState by viewModel.uiState
    val previousPollingTxIdState = remember { mutableStateOf<UUID?>(null) }
    val printRequestData by viewModel.printRequestState.collectAsState()
    val printerAccessor = LocalPrinterService.current
    val context = LocalContext.current

    LaunchedEffect(uiState.pollingTransactionId) {
        val currentTxId = uiState.pollingTransactionId
        if (currentTxId != null && currentTxId != previousPollingTxIdState.value) {
            Log.d(
                "PosAppScreen",
                "Detected new transaction offer ID, starting polling: $currentTxId"
            )
            viewModel.startPolling(currentTxId)
            previousPollingTxIdState.value = currentTxId
        }
    }

    LaunchedEffect(printRequestData) {
        printRequestData?.let { transactionToPrint ->
            Log.d("PosAppScreen", "Print request detected for Tx: ${transactionToPrint.id}")
            viewModel.executePrintReceipt(context, printerAccessor, transactionToPrint)
            viewModel.consumePrintRequest()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.identipaypos_logo),
                contentDescription = "identiPay Logo",
                modifier = Modifier.width(300.dp).height(160.dp),
                contentScale = ContentScale.Fit
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = uiState.amount,
                    onValueChange = viewModel::onAmountChange,
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(0.6f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = uiState.currency,
                    onValueChange = viewModel::onCurrencyChange,
                    label = { Text("Currency") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(0.4f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                enabled = !uiState.isLoading && !uiState.isPolling,
                onClick = viewModel::generatePaymentOfferQr,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.isPolling) "Waiting for Confirmation..." else "Generate Payment Offer")
            }
            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(250.dp)
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading && uiState.qrCodeBitmap == null) {
                    CircularProgressIndicator()
                } else if (uiState.finalTransactionStatus.equals("Completed", ignoreCase = true)) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Transaction Completed",
                        tint = Color(0xFF006400),
                        modifier = Modifier.size(100.dp)
                    )
                } else if (uiState.qrCodeBitmap != null) {
                    Image(
                        bitmap = uiState.qrCodeBitmap!!.asImageBitmap(),
                        contentDescription = "Payment QR Code",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                } else {
                    Text(
                        text = "No QR Code Available",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = when (uiState.finalTransactionStatus) {
                    "Completed" -> Color(0xFF006400)
                    "Failed" -> MaterialTheme.colorScheme.error
                    else -> LocalContentColor.current
                }
            )

            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}