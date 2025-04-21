package com.identipay.wallet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.identipay.wallet.viewmodel.DashboardViewModel

@Composable
fun WalletDashboardScreen(
    viewModel: DashboardViewModel = viewModel(factory = LocalViewModelFactory.current)
) {
    val userDid by viewModel.userDid.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
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
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else if (userDid?.startsWith("Error:") == true) {
            Text(
                text = userDid!!,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
        else {
            Text(
                text = userDid!!,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Credentials & Transactions will appear here.")

    }
}

val LocalViewModelFactory = compositionLocalOf<ViewModelProvider.Factory> {
    error("ViewModelFactory not provided")
}