package com.identipay.wallet.ui.screens

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.identipay.wallet.viewmodel.SigningState
import com.identipay.wallet.viewmodel.TransactionConfirmState
import com.identipay.wallet.viewmodel.TransactionConfirmViewModel
import java.security.Signature
import com.identipay.wallet.LocalViewModelFactory
import com.identipay.wallet.viewmodel.TransactionConfirmViewModelFactory
import com.identipay.wallet.navigation.Routes

@Composable
fun TransactionConfirmScreen(
    navController: NavController,
    transactionId: String,
    viewModel: TransactionConfirmViewModel = viewModel(
        factory = TransactionConfirmViewModelFactory(transactionId, LocalViewModelFactory.current)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val signingState by viewModel.signingState.collectAsState()
    val context = LocalContext.current
    val activity = LocalContext.current as? FragmentActivity

    val executor = remember { ContextCompat.getMainExecutor(context) }

    val biometricCallback = remember {
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.e("ConfirmScreen", "Auth error: [$errorCode] $errString")
                viewModel.handleAuthenticationFailure("Authentication error: $errString")
                Toast.makeText(context, "Authentication Error: $errString", Toast.LENGTH_SHORT).show()
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.i("ConfirmScreen", "Auth succeeded!")
                val currentLoadedState = viewModel.uiState.value as? TransactionConfirmState.OfferLoaded
                viewModel.handleAuthenticationSuccess(
                    result.cryptoObject?.signature,
                    currentLoadedState?.transaction
                )
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w("ConfirmScreen", "Auth failed (e.g., wrong fingerprint)")
                Toast.makeText(context, "Authentication Failed", Toast.LENGTH_SHORT).show()
                viewModel.handleAuthenticationFailure("Authentication failed.")
            }
        }
    }

    val biometricPrompt = remember(activity, executor, biometricCallback) {
        activity?.let { BiometricPrompt(it, executor, biometricCallback) }
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate Transaction")
            .setSubtitle("Confirm payment by authenticating")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
    }

    val onApproveClick: () -> Unit = Button@{
        if (activity == null || biometricPrompt == null) {
            Log.e("ConfirmScreen", "Activity or BiometricPrompt is null. Cannot authenticate.")
            Toast.makeText(context, "Cannot initiate authentication.", Toast.LENGTH_SHORT).show()
            return@Button
        }

        val currentState = viewModel.uiState.value
        if (currentState is TransactionConfirmState.OfferLoaded) {
            Log.d("ConfirmScreen", "Approve clicked. Preparing to sign.")
            if (!viewModel.prepareSignatureData(currentState.transaction)) {
                Toast.makeText(context, "Error preparing transaction data.", Toast.LENGTH_SHORT).show()
                return@Button
            }

            val signatureObject: Signature? = viewModel.getInitializedSignatureForSigning()

            if (signatureObject != null) {
                Log.d("ConfirmScreen", "Signature initialized. Showing prompt.")
                biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(signatureObject))
            } else {
                Log.e("ConfirmScreen", "Failed to initialize Signature object.")
                Toast.makeText(context, "Error preparing signature.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w("ConfirmScreen", "Approve clicked but transaction not in loaded state.")
            Toast.makeText(context, "Transaction details not loaded yet.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(signingState) {
        if (signingState is SigningState.SigningComplete) {
            Toast.makeText(context, "Transaction Complete!", Toast.LENGTH_LONG).show()
            navController.popBackStack(Routes.MAIN_WALLET, inclusive = false)
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "Confirm Transaction",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Transaction ID: ${transactionId.take(8)}...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                when (val state = uiState) {
                    is TransactionConfirmState.Idle, TransactionConfirmState.Loading -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading transaction details...")
                    }
                    is TransactionConfirmState.Error -> {
                        Text(
                            "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadTransactionDetails() }) {
                            Text("Retry")
                        }
                    }
                    is TransactionConfirmState.OfferLoaded -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Recipient DID:", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    state.transaction.payload?.recipientDid ?: "N/A",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text("Amount:", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "${state.transaction.payload?.amount ?: 0.0} ${state.transaction.payload?.currency ?: "???"}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text("Type:", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    state.transaction.payload?.type ?: "Unknown",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                state.transaction.payload?.metadataJson?.let {
                                    Text("Metadata:", style = MaterialTheme.typography.labelMedium)
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (val signState = signingState) {
                    SigningState.AwaitingAuthentication -> Text("Waiting for authentication...")
                    SigningState.SigningInProgress -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Processing...")
                    }
                    is SigningState.SigningFailed -> Text(
                        "Failed: ${signState.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                    SigningState.SigningComplete -> Text(
                        "Complete!",
                        color = MaterialTheme.colorScheme.primary
                    )
                    SigningState.Idle -> Box(modifier = Modifier.height(24.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val actionsEnabled = uiState is TransactionConfirmState.OfferLoaded && signingState is SigningState.Idle
                val rejectEnabled = signingState !is SigningState.SigningInProgress && signingState !is SigningState.AwaitingAuthentication

                Button(
                    onClick = { if (rejectEnabled) navController.popBackStack() },
                    enabled = rejectEnabled,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Reject")
                }
                Button(
                    onClick = onApproveClick,
                    enabled = actionsEnabled,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Text("Approve & Sign")
                }
            }
        }
    }
}