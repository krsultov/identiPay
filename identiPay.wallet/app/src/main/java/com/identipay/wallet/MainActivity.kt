package com.identipay.wallet

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.identipay.wallet.navigation.Routes
import com.identipay.wallet.ui.screens.KeyGenerationScreen
import com.identipay.wallet.ui.screens.WelcomeScreen
import com.identipay.wallet.ui.screens.RegistrationScreen
import com.identipay.wallet.ui.screens.WalletDashboardScreen
import com.identipay.wallet.security.KeyStoreManager
import com.identipay.wallet.viewmodel.OnboardingViewModel
import com.identipay.wallet.ui.theme.IdentiPayWalletTheme

class ViewModelFactory(private val keyStoreManager: KeyStoreManager) :
    androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(keyStoreManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : AppCompatActivity() {

    private val keyStoreManager by lazy { KeyStoreManager() }
    private lateinit var viewModelFactory: ViewModelFactory

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModelFactory = ViewModelFactory(keyStoreManager)
        enableEdgeToEdge()
        setContent {
            IdentiPayWalletTheme {
                val navController = rememberNavController()
                val onboardingViewModel: OnboardingViewModel = viewModel(factory = viewModelFactory)

                val alreadyOnboarded = checkOnboardingStatus()
                val startDestination = if (alreadyOnboarded) Routes.MAIN_WALLET else Routes.WELCOME

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        composable(Routes.WELCOME) {
                            WelcomeScreen(navController = navController)
                        }
                        composable(Routes.KEY_GENERATION) {
                            KeyGenerationScreen(
                                navController = navController,
                                viewModel = onboardingViewModel
                            )
                        }
                        composable(Routes.REGISTRATION) {
                            RegistrationScreen(
                                navController = navController,
                                viewModel = onboardingViewModel
                            )
                        }
                        composable(Routes.MAIN_WALLET) {
                            WalletDashboardScreen()
                        }
                    }
                }
            }
        }
    }

    private fun checkOnboardingStatus(): Boolean {
        return false
    }
}