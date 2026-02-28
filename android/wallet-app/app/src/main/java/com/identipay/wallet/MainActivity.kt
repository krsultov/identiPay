package com.identipay.wallet

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.identipay.wallet.data.preferences.UserPreferences
import com.identipay.wallet.navigation.NavGraph
import com.identipay.wallet.navigation.Route
import com.identipay.wallet.nfc.PassportReader
import com.identipay.wallet.ui.common.BiometricHelper
import com.identipay.wallet.ui.theme.IdentipayWalletTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

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
                    var biometricPassed by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()

                    // Trigger biometric prompt on launch for onboarded users
                    LaunchedEffect(isOnboarded) {
                        if (isOnboarded) {
                            val result = BiometricHelper.authenticate(
                                activity = this@MainActivity,
                                title = "Unlock Identipay",
                                subtitle = "Verify your identity to open wallet",
                            )
                            if (result.isSuccess) {
                                biometricPassed = true
                            }
                        }
                    }

                    // Non-onboarded users skip biometric gate
                    if (!isOnboarded || biometricPassed) {
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
                    } else {
                        // Lock screen
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Identipay",
                                style = MaterialTheme.typography.headlineLarge,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Wallet locked",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = {
                                scope.launch {
                                    val result = BiometricHelper.authenticate(
                                        activity = this@MainActivity,
                                        title = "Unlock Identipay",
                                        subtitle = "Verify your identity to open wallet",
                                    )
                                    if (result.isSuccess) {
                                        biometricPassed = true
                                    }
                                }
                            }) {
                                Text("Unlock")
                            }
                        }
                    }
                }
            }
        }
    }
}
