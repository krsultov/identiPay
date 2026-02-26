package com.identipay.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.identipay.wallet.data.preferences.UserPreferences
import com.identipay.wallet.navigation.NavGraph
import com.identipay.wallet.navigation.Route
import com.identipay.wallet.nfc.PassportReader
import com.identipay.wallet.ui.theme.IdentipayWalletTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var passportReader: PassportReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IdentipayWalletTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val isOnboarded by userPreferences.isOnboarded.collectAsState(initial = false)
                    val navController = rememberNavController()

                    val startDestination = if (isOnboarded) {
                        Route.Home.route
                    } else {
                        Route.IdentityFlow.route
                    }

                    NavGraph(
                        navController = navController,
                        passportReader = passportReader,
                        startDestination = startDestination,
                    )
                }
            }
        }
    }
}
