package com.identipay.wallet

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.identipay.wallet.data.local.AppDatabase
import com.identipay.wallet.navigation.Routes
import com.identipay.wallet.security.KeyStoreManager
import com.identipay.wallet.ui.screens.KeyGenerationScreen
import com.identipay.wallet.ui.screens.RegistrationScreen
import com.identipay.wallet.ui.screens.TransactionConfirmScreen
import com.identipay.wallet.ui.screens.WalletDashboardScreen
import com.identipay.wallet.ui.screens.WalletDestinations
import com.identipay.wallet.ui.screens.WelcomeScreen
import com.identipay.wallet.ui.theme.IdentiPayWalletTheme
import com.identipay.wallet.viewmodel.DashboardViewModel
import com.identipay.wallet.viewmodel.OnboardingViewModel
import com.identipay.wallet.viewmodel.TransactionConfirmViewModel
import com.identipay.wallet.viewmodel.TransactionConfirmViewModelFactory
import com.identipay.wallet.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.charset.StandardCharsets

val LocalViewModelFactory = compositionLocalOf<ViewModelProvider.Factory> {
    error("ViewModelFactory not provided")
}

sealed class NavigationEvent {
    data class NavigateToConfirm(val transactionId: String) : NavigationEvent()
    data object Idle : NavigationEvent()
}

class MainActivity : AppCompatActivity() {

    private val keyStoreManager by lazy { KeyStoreManager() }
    private lateinit var viewModelFactory: ViewModelFactory
    private lateinit var onboardingViewModel: OnboardingViewModel

    private var nfcAdapter: NfcAdapter? = null
    lateinit var navController: NavHostController

    private val _navigationEvent = MutableStateFlow<NavigationEvent>(NavigationEvent.Idle)
    val navigationEvent: StateFlow<NavigationEvent> = _navigationEvent.asStateFlow()

    private val nfcReaderCallback = NfcAdapter.ReaderCallback { tag ->
        Log.i("MainActivity", "NFC Tag Detected: ${tag.id?.joinToString("")}")

        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            Log.w("MainActivity", "Tag does not support IsoDep (APDU).")
            return@ReaderCallback
        }

        try {
            isoDep.connect()
            Log.d("MainActivity", "IsoDep connected.")

            val selectCommand = createSelectAidApdu(IDENTIPAY_POS_AID)
            Log.d("MainActivity", "Sending SELECT AID APDU: ${selectCommand.toHexString()}")
            val selectResponse = isoDep.transceive(selectCommand)
            Log.d("MainActivity", "SELECT AID Response: ${selectResponse.toHexString()}")

            if (!isSuccessResponse(selectResponse)) {
                Log.e("MainActivity", "Failed to select IdentiPay POS AID.")
                runOnUiThread { Toast.makeText(this, "App not found on POS tag", Toast.LENGTH_SHORT).show() }
                isoDep.close()
                return@ReaderCallback
            }
            Log.i("MainActivity", "IdentiPay POS AID Selected successfully.")

            val getTxIdCommand = createGetTransactionIdApdu()
            Log.d("MainActivity", "Sending GET TX ID APDU: ${getTxIdCommand.toHexString()}")
            val getTxIdResponse = isoDep.transceive(getTxIdCommand)
            Log.d("MainActivity", "GET TX ID Response: ${getTxIdResponse.toHexString()}")

            if (isSuccessResponse(getTxIdResponse) && getTxIdResponse.size > 2) {
                val dataBytes = getTxIdResponse.copyOfRange(0, getTxIdResponse.size - 2)
                val transactionId = String(dataBytes, StandardCharsets.UTF_8)
                Log.i("MainActivity", "Received Transaction ID via HCE: $transactionId")

                runOnUiThread {
                    if (transactionId.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\$"))) {
                        Toast.makeText(this, "Transaction ID Received via NFC!", Toast.LENGTH_SHORT).show()
                        _navigationEvent.value = NavigationEvent.NavigateToConfirm(transactionId)
                    } else {
                        Log.w("MainActivity", "Received invalid TxID format via HCE.")
                        Toast.makeText(this, "Invalid data from POS tag", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Log.e("MainActivity", "Failed to get Transaction ID from POS tag. SW: ${getTxIdResponse.takeLast(2).toByteArray().toHexString()}")
                runOnUiThread { Toast.makeText(this, "Could not get TxID from POS", Toast.LENGTH_SHORT).show() }
            }

            isoDep.close()

        } catch (e: IOException) {
            Log.e("MainActivity", "NFC Communication Error (IOException)", e)
            runOnUiThread { Toast.makeText(this, "NFC Communication Error", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) {
            Log.e("MainActivity", "NFC Error", e)
            runOnUiThread { Toast.makeText(this, "NFC Read Error", Toast.LENGTH_SHORT).show() }
        } finally {
            if (isoDep.isConnected) {
                try { isoDep.close() } catch (_: IOException) { }
            }
        }
    }

    companion object {
        private val TAG = "MainActivityNFC"

        private val IDENTIPAY_POS_AID = hexStringToByteArray("F123456789FDEF")
        private const val INS_GET_TRANSACTION_ID = 0x01

        fun createSelectAidApdu(aid: ByteArray): ByteArray {
            val header = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00)
            val lc = byteArrayOf(aid.size.toByte())
            return header + lc + aid
        }

        fun createGetTransactionIdApdu(): ByteArray {
            return byteArrayOf(0x00, INS_GET_TRANSACTION_ID.toByte(), 0x00, 0x00)
        }

        fun isSuccessResponse(responseApdu: ByteArray?): Boolean {
            return responseApdu != null && responseApdu.size >= 2 &&
                    responseApdu[responseApdu.size - 2] == 0x90.toByte() &&
                    responseApdu[responseApdu.size - 1] == 0x00.toByte()
        }

        private fun hexStringToByteArray(hex: String): ByteArray {
            val len = hex.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] =
                    ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
                i += 2
            }
            return data
        }

        private fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModelFactory = ViewModelFactory(applicationContext, keyStoreManager)
        onboardingViewModel = ViewModelProvider(this, viewModelFactory)[OnboardingViewModel::class.java]

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

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

    override fun onResume() {
        super.onResume()

        if (nfcAdapter == null) {
            Log.e(TAG, "NFC Adapter is still null in onResume.")
            Toast.makeText(this, "NFC is not supported on this device.", Toast.LENGTH_LONG).show()
        } else if (!nfcAdapter!!.isEnabled) {
            Log.w(TAG, "NFC is disabled.")
            Toast.makeText(this, "Please enable NFC in system settings.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        } else {
            enableNfcReaderMode()
        }

        enableNfcReaderMode()
    }

    override fun onPause() {
        super.onPause()
        disableNfcReaderMode()
    }

    private fun enableNfcReaderMode() {
        if (nfcAdapter?.isEnabled == true) {
            Log.d(TAG, "Enabling NFC Reader Mode.")
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

            nfcAdapter?.enableReaderMode(this, nfcReaderCallback, flags, null)
        } else {
            Log.w(TAG, "NFC Adapter not enabled, cannot enable Reader Mode.")
        }
    }

    private fun disableNfcReaderMode() {
        if (nfcAdapter?.isEnabled == true) {
            Log.d(TAG, "Disabling NFC Reader Mode.")
            try {
                nfcAdapter?.disableReaderMode(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error disabling reader mode", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setMainActivityContent(startDestination: String) {
        setContent {
            IdentiPayWalletTheme {
                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    navigationEvent.collectLatest { event ->
                        when (event) {
                            is NavigationEvent.NavigateToConfirm -> {
                                Log.d("MainActivity", "Navigating to confirm screen via state: ${event.transactionId}")
                                if(navController.currentDestination?.route?.startsWith(WalletDestinations.TRANSACTION_CONFIRM_ROUTE) == false) {
                                    navController.navigate("${WalletDestinations.TRANSACTION_CONFIRM_ROUTE}/${event.transactionId}")
                                }
                                _navigationEvent.value = NavigationEvent.Idle
                            }
                            NavigationEvent.Idle -> { }
                        }
                    }
                }

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
                            val dashboardViewModel: DashboardViewModel = viewModel(factory = viewModelFactory)
                            WalletDashboardScreen(navController = navController, viewModel = dashboardViewModel)
                        }
                        composable(
                            route = WalletDestinations.routeWithArgs,
                            arguments = listOf(navArgument(WalletDestinations.TRANSACTION_ID_ARG) { type = NavType.StringType })
                        ) { backStackEntry ->
                            val transactionId = backStackEntry.arguments?.getString(WalletDestinations.TRANSACTION_ID_ARG)
                                ?: throw IllegalArgumentException("Transaction ID not found in arguments")

                            val txConfirmFactory = TransactionConfirmViewModelFactory(transactionId, viewModelFactory)
                            val txConfirmViewModel: TransactionConfirmViewModel = viewModel(factory = txConfirmFactory)

                            TransactionConfirmScreen(
                                navController = navController,
                                transactionId = transactionId,
                                viewModel = txConfirmViewModel
                            )
                        }
                    }
                }
            }
        }
    }
}