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
import androidx.compose.ui.graphics.Color
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
                Log.e("ConfirmScreen", "Auth error: [$errorCode] $errString")
                viewModel.handleAuthenticationFailure("Authentication error: $errString")
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                Log.i("ConfirmScreen", "Auth succeeded!")
                val signatureObject = result.cryptoObject?.signature

                viewModel.handleAuthenticationSuccess(
                    signatureObject?.sign(),
                    (viewModel.uiState.value as? TransactionConfirmState.OfferLoaded)?.transaction
                )
            }

            override fun onAuthenticationFailed() {
                Log.w("ConfirmScreen", "Auth failed (wrong biometric)")
                viewModel.handleAuthenticationFailure("Authentication failed.")
            }
        }
    }
    val biometricPrompt = remember(activity, executor, biometricCallback) {
        if (activity != null) BiometricPrompt(activity, executor, biometricCallback) else null
    }
    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate Transaction")
            .setSubtitle("Confirm payment by authenticating")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
    }

    val onApproveClick: () -> Unit = {
        val currentState = viewModel.uiState.value
        if (activity == null || biometricPrompt == null) {
            Toast.makeText(context, "Cannot initiate authentication.", Toast.LENGTH_SHORT).show()
            return@Button
        }
        if (currentState is TransactionConfirmState.OfferLoaded) {
            Log.d("ConfirmScreen", "Approve clicked. Preparing to sign.")
            val dataToSign = viewModel.prepareSignatureData(currentState.transaction) ?: return@Button

            val signatureObject: Signature? = try {
                viewModel.getInitializedSignatureForSigning()
            } catch (e: Exception) { null }

            if (signatureObject != null) {
                Log.d("ConfirmScreen", "Signature initialized. Showing prompt.")
                biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(signatureObject))
            } else {
                Log.e("ConfirmScreen", "Failed to initialize Signature object.")
                Toast.makeText(context, "Error preparing signature.", Toast.LENGTH_SHORT).show()
                viewModel.handleAuthenticationFailure("Crypto Init Error")
            }
        } else {
            Log.w("ConfirmScreen", "Approve clicked but transaction not loaded.")
            Toast.makeText(context, "Transaction details not loaded yet.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(signingState) {
        if (signingState is SigningState.SigningComplete) {
            Toast.makeText(context, "Transaction Complete!", Toast.LENGTH_LONG).show()
            navController.popBackStack(Routes.MAIN_WALLET, inclusive = false)
        }
    }


    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Confirm Transaction", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Transaction ID: ${transactionId.take(8)}...")
            Spacer(modifier = Modifier.height(24.dp))

            when (val state = uiState) {
                is TransactionConfirmState.Idle, TransactionConfirmState.Loading -> {
                    CircularProgressIndicator()
                    Text("Loading transaction details...")
                }
                is TransactionConfirmState.Error -> {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.loadTransactionDetails() }) { Text("Retry") }
                }
                is TransactionConfirmState.OfferLoaded -> {
                    Text("Recipient DID: ${state.transaction.payload?.recipientDid ?: "N/A"}")
                    Text("Amount: ${state.transaction.payload?.amount ?: 0.0} ${state.transaction.payload?.currency ?: "???"}")
                    Text("Type: ${state.transaction.payload?.type ?: "Unknown"}")
                    state.transaction.payload?.metadataJson?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Metadata: $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            when (val signState = signingState) {
                SigningState.AwaitingAuthentication -> { Spacer(Modifier.height(16.dp)); Text("Waiting for authentication...") }
                SigningState.SigningInProgress -> { Spacer(Modifier.height(16.dp)); CircularProgressIndicator(); Text("Processing...") }
                is SigningState.SigningFailed -> { Spacer(Modifier.height(16.dp)); Text("Failed: ${signState.message}", color = MaterialTheme.colorScheme.error)}
                SigningState.SigningComplete -> { Spacer(Modifier.height(16.dp)); Text("Complete!", color = Color(0xFF006400)) }
                SigningState.Idle -> { }
            }
        }


        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val enableActions = uiState is TransactionConfirmState.OfferLoaded && signingState is SigningState.Idle

            Button(
                onClick = { navController.popBackStack() },
                enabled = signingState !is SigningState.SigningInProgress && signingState !is SigningState.AwaitingAuthentication,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Text("Reject")
            }
            Button(
                onClick = onApproveClick, // Call the prepared lambda
                enabled = enableActions, // Enable only when loaded and idle
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            ) {
                Text("Approve & Sign")
            }
        }
    }
}