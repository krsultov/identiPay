package com.identipay.wallet.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.identipay.wallet.navigation.Routes
import com.identipay.wallet.viewmodel.OnboardingState
import com.identipay.wallet.viewmodel.OnboardingViewModel

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun KeyGenerationScreen(navController: NavController, viewModel: OnboardingViewModel) {
    val state by viewModel.onboardingState.collectAsState()
    var publicKeyForRegistration by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        when (val currentState = state) {
            is OnboardingState.KeyGenComplete -> {
                publicKeyForRegistration = currentState.publicKey
            }

            is OnboardingState.KeyGenFailed -> { /* Show error message? */ }

            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            OnboardingState.Idle -> {
                Text("Secure Key Generation")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.generateKeys() }) {
                    Text("Generate Secure Keys")
                }
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { navController.popBackStack() },
                    enabled = state !is OnboardingState.KeyGenInProgress
                ) {
                    Text("Back")
                }
            }

            OnboardingState.KeyGenInProgress -> {
                CircularProgressIndicator()
                Text("Generating keys...")
            }

            is OnboardingState.KeyGenComplete -> {
                Text("Keys generated successfully!")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Public Key (start): ${publicKeyForRegistration?.take(10)}...")
                Button(onClick = {
                    if (publicKeyForRegistration != null) {
                        viewModel.registerUser(publicKeyForRegistration!!)
                        navController.navigate(Routes.REGISTRATION) {
                            popUpTo(Routes.KEY_GENERATION) { inclusive = true }
                        }
                    } else {
                        viewModel.resetState()
                    }
                }) {
                    Text("Register Identity")
                }
            }

            OnboardingState.KeyGenFailed -> {
                Text("Key generation failed. Please try again.")
                Button(onClick = { viewModel.generateKeys() }) {
                    Text("Retry Generation")
                }
            }

            else -> {}
        }
    }
}