package com.identipay.wallet.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.identipay.wallet.nfc.PassportReader
import com.identipay.wallet.ui.history.HistoryScreen
import com.identipay.wallet.ui.history.HistoryViewModel
import com.identipay.wallet.ui.home.HomeScreen
import com.identipay.wallet.ui.home.HomeViewModel
import com.identipay.wallet.ui.identity.IdentitySetupScreen
import com.identipay.wallet.ui.identity.IdentityViewModel
import com.identipay.wallet.ui.identity.MrzScanScreen
import com.identipay.wallet.ui.identity.NamePickerScreen
import com.identipay.wallet.ui.identity.NfcScanScreen
import com.identipay.wallet.ui.identity.PinEntryScreen
import com.identipay.wallet.ui.checkout.CheckoutViewModel
import com.identipay.wallet.ui.checkout.ConfirmScreen
import com.identipay.wallet.ui.checkout.ProposalReviewScreen
import com.identipay.wallet.ui.receive.ReceiveScreen
import com.identipay.wallet.ui.receive.ReceiveViewModel
import com.identipay.wallet.ui.scanner.ScannerScreen
import com.identipay.wallet.ui.scanner.ScannerViewModel
import com.identipay.wallet.ui.send.SendScreen
import com.identipay.wallet.ui.send.SendViewModel
import com.identipay.wallet.ui.stealth.StealthAddressesScreen
import com.identipay.wallet.ui.stealth.StealthAddressesViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument

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
            HomeScreen(
                viewModel = viewModel,
                onSend = { navController.navigate(Route.Send.route) },
                onReceive = { navController.navigate(Route.Receive.route) },
                onHistory = { navController.navigate(Route.History.route) },
                onScan = { navController.navigate(Route.Scanner.route) },
                onStealthAddresses = { navController.navigate(Route.StealthAddresses.route) },
            )
        }

        composable(Route.Send.route) {
            val viewModel: SendViewModel = hiltViewModel()
            SendScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSuccess = { navController.popBackStack() },
            )
        }

        composable(Route.Receive.route) {
            val viewModel: ReceiveViewModel = hiltViewModel()
            ReceiveScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Route.StealthAddresses.route) {
            val viewModel: StealthAddressesViewModel = hiltViewModel()
            StealthAddressesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Route.History.route) {
            val viewModel: HistoryViewModel = hiltViewModel()
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Route.Scanner.route) {
            val viewModel: ScannerViewModel = hiltViewModel()
            ScannerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onProposalReady = { txId ->
                    navController.navigate(Route.ProposalReview.create(txId)) {
                        popUpTo(Route.Scanner.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Route.ProposalReview.route,
            arguments = listOf(navArgument("txId") { type = NavType.StringType }),
        ) {
            val viewModel: CheckoutViewModel = hiltViewModel()
            ProposalReviewScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onConfirm = { txId, txDigest ->
                    navController.navigate(Route.Confirm.create(txId, txDigest)) {
                        popUpTo(Route.ProposalReview.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Route.Confirm.route,
            arguments = listOf(
                navArgument("txId") { type = NavType.StringType },
                navArgument("txDigest") { type = NavType.StringType },
            ),
        ) { entry ->
            val txDigest = entry.arguments?.getString("txDigest") ?: ""
            ConfirmScreen(
                txDigest = txDigest,
                merchantName = null,
                onDone = {
                    navController.navigate(Route.Home.route) {
                        popUpTo(Route.Home.route) { inclusive = true }
                    }
                },
            )
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
                    onSuccess = { navController.navigate(Route.PinEntry.route) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Route.PinEntry.route) { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(Route.IdentityFlow.route)
                }
                val viewModel: IdentityViewModel = hiltViewModel(parentEntry)
                PinEntryScreen(
                    viewModel = viewModel,
                    onNewRegistration = { navController.navigate(Route.NamePicker.route) },
                    onReactivated = {
                        // Wallet recovered from chain — skip name picker, go home
                        navController.navigate(Route.Home.route) {
                            popUpTo(Route.IdentityFlow.route) { inclusive = true }
                        }
                    },
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
