package com.identipay.wallet

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.identipay.wallet.data.local.AppDatabase
import com.identipay.wallet.navigation.Routes
import com.identipay.wallet.security.KeyStoreManager
import com.identipay.wallet.ui.screens.KeyGenerationScreen
import com.identipay.wallet.ui.screens.RegistrationScreen
import com.identipay.wallet.ui.screens.WalletDashboardScreen
import com.identipay.wallet.ui.screens.WelcomeScreen
import com.identipay.wallet.ui.theme.IdentiPayWalletTheme
import com.identipay.wallet.viewmodel.OnboardingViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val keyStoreManager by lazy { KeyStoreManager() }
    private lateinit var viewModelFactory: ViewModelFactory

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModelFactory = ViewModelFactory(applicationContext, keyStoreManager)
        enableEdgeToEdge()

        var startRoute = Routes.WELCOME
        lifecycleScope.launch {
            val userData = AppDatabase.getDatabase(applicationContext).userDao().getUserData()
            if (userData?.onboardingComplete == true) {
                startRoute = Routes.MAIN_WALLET
            }
            Log.i("MainActivity", "User data: $userData")
            setMainActivityContent(startRoute)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setMainActivityContent(startDestination: String) {
        setContent {
            IdentiPayWalletTheme {
                val navController = rememberNavController()
                val onboardingViewModel: OnboardingViewModel = viewModel(factory = viewModelFactory)

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
}