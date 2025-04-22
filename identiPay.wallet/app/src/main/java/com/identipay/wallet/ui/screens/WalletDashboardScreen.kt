package com.identipay.wallet.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Button
import androidx.compose.ui.platform.LocalContext
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.identipay.wallet.viewmodel.DashboardViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModelProvider


object WalletDestinations {
    const val TRANSACTION_CONFIRM_ROUTE = "transaction_confirm"
    const val TRANSACTION_ID_ARG = "transactionId"
    val routeWithArgs = "$TRANSACTION_CONFIRM_ROUTE/{$TRANSACTION_ID_ARG}"
}

@Composable
fun WalletDashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel = viewModel(factory = LocalViewModelFactory.current)
) {
    val userDid by viewModel.userDid.collectAsState()
    val context = LocalContext.current

    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedTransactionId = result.contents
            Log.i("DashboardScreen", "QR Scanned Result: $scannedTransactionId")
            if (scannedTransactionId.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\$"))) {
                navController.navigate("${WalletDestinations.TRANSACTION_CONFIRM_ROUTE}/$scannedTransactionId")
            } else {
                android.widget.Toast.makeText(context, "Invalid Transaction QR Code format.", android.widget.Toast.LENGTH_LONG).show()
            }

        } else {
            Log.i("DashboardScreen", "QR Scan Cancelled")
            android.widget.Toast.makeText(context, "Scan cancelled.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "IdentiPay Wallet",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Your Decentralized Identifier (DID):",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (userDid == null || userDid == "Loading DID...") {
            Text(text = "Loading...", style = MaterialTheme.typography.bodyMedium)
        } else if (userDid?.startsWith("Error:") == true) {
            Text(text = userDid!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(text = userDid!!, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val options = ScanOptions()
                options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                options.setPrompt("Scan Payment QR Code")
                options.setCameraId(0)
                options.setBeepEnabled(true)
                options.setBarcodeImageEnabled(false)
                options.setOrientationLocked(false)

                qrScanLauncher.launch(options)
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Text("Scan Payment QR Code")
        }
    }
}

val LocalViewModelFactory = compositionLocalOf<ViewModelProvider.Factory> {
    error("ViewModelFactory not provided")
}