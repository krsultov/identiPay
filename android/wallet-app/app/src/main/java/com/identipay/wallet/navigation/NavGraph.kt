package com.identipay.wallet.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.identipay.wallet.nfc.PassportReader
import com.identipay.wallet.ui.home.HomeScreen
import com.identipay.wallet.ui.home.HomeViewModel
import com.identipay.wallet.ui.identity.IdentitySetupScreen
import com.identipay.wallet.ui.identity.IdentityViewModel
import com.identipay.wallet.ui.identity.MrzScanScreen
import com.identipay.wallet.ui.identity.NamePickerScreen
import com.identipay.wallet.ui.identity.NfcScanScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    passportReader: PassportReader,
    startDestination: String,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Route.Home.route) {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(viewModel = viewModel)
        }

        // Nested graph so IdentityViewModel is shared across all identity screens
        navigation(
            startDestination = Route.IdentitySetup.route,
            route = Route.IdentityFlow.route,
        ) {
            composable(Route.IdentitySetup.route) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(Route.IdentityFlow.route)
                }
                val viewModel: IdentityViewModel = hiltViewModel(parentEntry)
                IdentitySetupScreen(
                    viewModel = viewModel,
                    onContinue = { navController.navigate(Route.MrzScan.route) },
                )
            }

            composable(Route.MrzScan.route) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(Route.IdentityFlow.route)
                }
                val viewModel: IdentityViewModel = hiltViewModel(parentEntry)
                MrzScanScreen(
                    viewModel = viewModel,
                    onMrzScanned = { navController.navigate(Route.NfcScan.route) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Route.NfcScan.route) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(Route.IdentityFlow.route)
                }
                val viewModel: IdentityViewModel = hiltViewModel(parentEntry)
                NfcScanScreen(
                    viewModel = viewModel,
                    passportReader = passportReader,
                    onSuccess = { navController.navigate(Route.NamePicker.route) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Route.NamePicker.route) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(Route.IdentityFlow.route)
                }
                val viewModel: IdentityViewModel = hiltViewModel(parentEntry)
                NamePickerScreen(
                    viewModel = viewModel,
                    onRegistered = {
                        navController.navigate(Route.Home.route) {
                            popUpTo(Route.IdentityFlow.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
