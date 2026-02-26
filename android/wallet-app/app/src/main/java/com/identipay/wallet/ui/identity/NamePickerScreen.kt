package com.identipay.wallet.ui.identity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.identipay.wallet.ui.common.LoadingOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NamePickerScreen(
    viewModel: IdentityViewModel,
    onRegistered: () -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.registrationStatus) {
        if (uiState.registrationStatus == RegistrationStatus.Success) {
            onRegistered()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose Your Name") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Pick a unique name for your identiPay handle.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.chosenName,
                onValueChange = viewModel::updateChosenName,
                label = { Text("Name") },
                placeholder = { Text("e.g. alice") },
                singleLine = true,
                suffix = { Text(".idpay") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                ),
                supportingText = {
                    when {
                        uiState.chosenName.isEmpty() -> {
                            Text("3-20 characters, lowercase letters, numbers, hyphens")
                        }
                        uiState.chosenName.length < 3 -> {
                            Text("Name must be at least 3 characters")
                        }
                        uiState.nameCheckLoading -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp))
                                Text("Checking availability...")
                            }
                        }
                        uiState.nameAvailable == true -> {
                            Text(
                                "@${uiState.chosenName}.idpay is available",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        uiState.nameAvailable == false -> {
                            Text(
                                "This name is already taken",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (uiState.registrationStatus == RegistrationStatus.Error) {
                Text(
                    text = uiState.error ?: "Registration failed",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = viewModel::register,
                enabled = uiState.nameAvailable == true &&
                        uiState.registrationStatus == RegistrationStatus.Idle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) {
                Text("Register @${uiState.chosenName}.idpay")
            }
        }

        // Loading overlay during registration
        if (uiState.registrationStatus in listOf(
                RegistrationStatus.GeneratingProof,
                RegistrationStatus.Submitting
            )
        ) {
            LoadingOverlay(
                message = when (uiState.registrationStatus) {
                    RegistrationStatus.GeneratingProof -> "Generating ZK proof..."
                    RegistrationStatus.Submitting -> "Submitting registration..."
                    else -> "Processing..."
                }
            )
        }
    }
}
