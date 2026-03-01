package com.identipay.identipaypos.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.identipay.identipaypos.data.CartViewModel
import com.identipay.identipaypos.ui.checkout.CheckoutScreen
import com.identipay.identipaypos.ui.store.StoreScreen

object Routes {
    const val STORE = "store"
    const val CHECKOUT = "checkout"
}

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val cartViewModel: CartViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.STORE) {
        composable(Routes.STORE) {
            StoreScreen(
                cartViewModel = cartViewModel,
                onCheckout = { navController.navigate(Routes.CHECKOUT) },
            )
        }
        composable(Routes.CHECKOUT) {
            CheckoutScreen(
                cartViewModel = cartViewModel,
                onBack = { navController.popBackStack() },
                onNewSale = {
                    cartViewModel.clearCart()
                    navController.popBackStack()
                },
            )
        }
    }
}
