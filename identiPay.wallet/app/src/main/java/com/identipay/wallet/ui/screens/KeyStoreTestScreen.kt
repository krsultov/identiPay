package com.identipay.wallet.ui.screens

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.identipay.wallet.security.KeyStoreManager
import com.identipay.wallet.ui.theme.IdentiPayWalletTheme
import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.security.Signature
import java.util.concurrent.Executor

class KeyStoreTestScreen : AppCompatActivity() {

    private val keyStoreManager = KeyStoreManager()
    private val keyAlias = "identipay_user_main_key"

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo


    private var dataToSignAfterAuth by mutableStateOf<ByteArray?>(null)
    private var signatureResultForUi by mutableStateOf<String?>("Signature: (Not generated)")
    private var statusMessageForUi by mutableStateOf("IdentiPay Wallet Keystore Test")


    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.e("MainActivity", "Authentication error: [$errorCode] $errString")
                    statusMessageForUi = "Authentication error: $errString"
                    signatureResultForUi = "Signature: (Authentication Error)"
                    dataToSignAfterAuth = null
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.i("MainActivity", "Authentication succeeded!")
                    statusMessageForUi = "Authentication successful!"

                    val authenticatedSignature = result.cryptoObject?.signature
                    val dataBytes = dataToSignAfterAuth

                    if (authenticatedSignature != null && dataBytes != null) {
                        try {
                            authenticatedSignature.update(dataBytes)
                            val signatureBytes = authenticatedSignature.sign()
                            val signatureBase64Url = keyStoreManager.encodeSignatureBase64Url(signatureBytes)
                            signatureResultForUi = "Signature: $signatureBase64Url"
                            statusMessageForUi = "Data signed successfully."
                            Log.d("MainActivity", "Signing complete. Signature: $signatureBase64Url")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Signing failed AFTER authentication", e)
                            statusMessageForUi = "Signing failed after authentication."
                            signatureResultForUi = "Signature: (Signing Failed)"
                        }
                    } else {
                        Log.e("MainActivity", "Error: Signature object or data missing after authentication.")
                        statusMessageForUi = "Error retrieving crypto object after auth."
                        signatureResultForUi = "Signature: (Crypto Error)"
                    }
                    dataToSignAfterAuth = null
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w("MainActivity", "Authentication failed (e.g., wrong fingerprint)")
                    statusMessageForUi = "Authentication failed."
                    dataToSignAfterAuth = null
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("IdentiPay Biometric Sign In")
            .setSubtitle("Authenticate to sign transaction data")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()


        setContent {
            IdentiPayWalletTheme {
                var currentStatusMessage by remember { mutableStateOf(statusMessageForUi) }
                var publicKeyDisplay by remember { mutableStateOf<String?>("Public Key: (Not loaded)") }
                var currentSignatureDisplay by remember { mutableStateOf(signatureResultForUi) }

                LaunchedEffect(statusMessageForUi) { currentStatusMessage = statusMessageForUi }
                LaunchedEffect(signatureResultForUi) { currentSignatureDisplay = signatureResultForUi }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    KeyStoreTestScreen(
                        statusMessage = currentStatusMessage,
                        publicKey = publicKeyDisplay,
                        signature = currentSignatureDisplay,
                        modifier = Modifier.padding(innerPadding),
                        onGenerateKey = {
                            Thread {
                                val generated = keyStoreManager.generateKeyPairIfNotExists(keyAlias)
                                runOnUiThread {
                                    statusMessageForUi = if (generated) "New key pair generated." else "Key pair exists/failed."
                                    publicKeyDisplay = keyStoreManager.getPublicKeyBase64(keyAlias)?.let { "..." } ?: "..."
                                }
                            }.start()
                        },
                        onLoadPublicKey = {
                            Thread {
                                val pubKey = keyStoreManager.getPublicKeyBase64(keyAlias)
                                Log.i("MainActivity", "Loaded public key: $pubKey")
                                runOnUiThread {
                                    publicKeyDisplay = pubKey?.let { "Public Key: $it" } ?: "Public Key: (Not found)"
                                    statusMessageForUi = if (pubKey != null) "Public key loaded." else "Could not load key."
                                }
                            }.start()
                        },
                        onSignData = {
                            triggerBiometricSign()
                        }
                    )
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun triggerBiometricSign() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                Log.d("MainActivity", "App can authenticate using biometrics or device credential.")
            else -> {
                statusMessageForUi = "Biometric/Credential auth not set up or available."
                Toast.makeText(this, statusMessageForUi, Toast.LENGTH_SHORT).show()
                return
            }
        }

        val dataBytes = "This is the data to be signed for IdentiPay.".toByteArray(StandardCharsets.UTF_8)
        dataToSignAfterAuth = dataBytes

        val privateKey: PrivateKey? = try {
            (keyStoreManager.getPrivateKeyEntry(keyAlias))?.privateKey
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to get private key for signing init", e)
            statusMessageForUi = "Error getting private key."
            dataToSignAfterAuth = null
            return
        }

        if (privateKey == null) {
            Log.e("MainActivity", "Private key is null for alias '$keyAlias'")
            statusMessageForUi = "Private key not found for signing."
            dataToSignAfterAuth = null
            return
        }

        val signature: Signature? = try {
            Signature.getInstance(KeyStoreManager.SIGNATURE_ALGORITHM).apply {
                initSign(privateKey)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to initialize Signature", e)
            statusMessageForUi = "Error initializing crypto."
            dataToSignAfterAuth = null
            return
        }

        if (signature == null) {
            Log.e("MainActivity", "Signature object is null after init")
            statusMessageForUi = "Crypto init failed."
            dataToSignAfterAuth = null
            return
        }

        Log.d("MainActivity", "Showing biometric prompt...")
        statusMessageForUi = "Authenticating..."
        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(signature))
    }
}
@Composable
fun KeyStoreTestScreen(
    statusMessage: String,
    publicKey: String?,
    signature: String?,
    modifier: Modifier = Modifier,
    onGenerateKey: () -> Unit,
    onLoadPublicKey: () -> Unit,
    onSignData: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = statusMessage)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onGenerateKey) {
            Text("Generate/Check Key Pair")
        }
        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = onLoadPublicKey) {
            Text("Load Public Key")
        }
        Text(text = publicKey ?: "Public Key: Error/Unavailable", modifier = Modifier.padding(top = 8.dp))
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onSignData) {
            Text("Sign Sample Data")
        }
        Text(text = signature ?: "Signature: Error/Unavailable", modifier = Modifier.padding(top = 8.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    IdentiPayWalletTheme {
        KeyStoreTestScreen(
            statusMessage = "IdentiPay Wallet Keystore Test",
            publicKey = "Public Key: (Not loaded)",
            signature = "Signature: (Not generated)",
            onGenerateKey = {},
            onLoadPublicKey = {},
            onSignData = {}
        )
    }
}