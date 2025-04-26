package com.identipay.wallet.ui.screens

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

@Composable
fun RegistrationScreen(navController: NavController, viewModel: OnboardingViewModel) {
    val state by viewModel.onboardingState.collectAsState()

    LaunchedEffect(state) {
        println("Current state in RegistrationScreen: $state")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            OnboardingState.RegistrationInProgress -> {
                CircularProgressIndicator()
                Text("Registering Your Identity...")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Communicating with server...")
            }

            is OnboardingState.RegistrationComplete -> {
                Text("Registration Successful!")
                Text("Your DID: ${(state as OnboardingState.RegistrationComplete).userDid}")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    navController.navigate(Routes.MAIN_WALLET) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }) {
                    Text("Continue to Wallet")
                }
            }

            OnboardingState.RegistrationFailed -> {
                Text("Registration Failed. Please check connection and retry.")
                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    viewModel.resetState()
                    navController.popBackStack(Routes.KEY_GENERATION, inclusive = false)
                }) {
                    Text("Try Again")
                }
            }

            else -> {
                Text("Waiting for registration...")
                CircularProgressIndicator()
            }
        }
    }
}