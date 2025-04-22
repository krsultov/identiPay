package com.identipay.identipaypos
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.identipay.identipaypos.ui.theme.IdentiPayPOSTheme
import com.identipay.identipaypos.viewmodel.PosViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IdentiPayPOSTheme {
                PosAppScreen()
            }
        }
    }
}

@Composable
fun PosAppScreen(viewModel: PosViewModel = viewModel()) {

    val uiState by viewModel.uiState

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("IdentiPay POS", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

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
                onClick = viewModel::generatePaymentOfferQr,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate Payment Request QR")
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
                } else if (uiState.qrCodeBitmap != null) {
                    Image(
                        bitmap = uiState.qrCodeBitmap!!.asImageBitmap(),
                        contentDescription = "Payment QR Code",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("QR Code will appear here", textAlign = TextAlign.Center)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
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

@Preview(showBackground = true)
@Composable
fun PosAppScreenPreview() {
    IdentiPayPOSTheme {
        PosAppScreen(viewModel = PosViewModel())
    }
}