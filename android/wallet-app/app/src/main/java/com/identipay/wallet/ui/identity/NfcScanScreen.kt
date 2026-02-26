package com.identipay.wallet.ui.identity

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.identipay.wallet.nfc.CredentialData
import com.identipay.wallet.nfc.PassportReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcScanScreen(
    viewModel: IdentityViewModel,
    passportReader: PassportReader,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    // Navigate on success
    LaunchedEffect(uiState.nfcStatus) {
        if (uiState.nfcStatus == NfcStatus.Success) {
            onSuccess()
        }
    }

    // Enable/disable NFC reader mode with lifecycle
    DisposableEffect(activity, uiState.bacKey) {
        val bacKey = uiState.bacKey
        if (activity != null && bacKey != null) {
            viewModel.onNfcScanStarted()
            passportReader.enableReaderMode(
                activity,
                bacKey,
                object : PassportReader.ReadCallback {
                    override fun onProgress(step: String) {
                        viewModel.onNfcProgress(step)
                    }

                    override fun onSuccess(credential: CredentialData) {
                        viewModel.onNfcSuccess(credential)
                    }

                    override fun onError(error: String) {
                        viewModel.onNfcError(error)
                    }
                },
            )
        }

        onDispose {
            if (activity != null) {
                passportReader.disableReaderMode(activity)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Passport") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                when (uiState.nfcStatus) {
                    NfcStatus.Idle, NfcStatus.Scanning -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Hold your passport against the back of your phone",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Keep it steady until reading is complete",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    NfcStatus.Reading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = "Reading passport data...",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Do not move your phone",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    NfcStatus.Success -> {
                        Text(
                            text = "Passport read successfully",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                    }
                    NfcStatus.Error -> {
                        Text(
                            text = "Reading failed",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = uiState.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
