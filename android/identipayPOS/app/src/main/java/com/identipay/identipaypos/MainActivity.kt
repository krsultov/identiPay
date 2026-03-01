package com.identipay.identipaypos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.identipay.identipaypos.data.PrinterServiceAccessor
import com.identipay.identipaypos.ui.navigation.AppNavGraph
import com.identipay.identipaypos.ui.theme.IdentipayPOSTheme
import recieptservice.com.recieptservice.PrinterInterface
import java.util.concurrent.atomic.AtomicReference

val LocalPrinterService = staticCompositionLocalOf<PrinterServiceAccessor> {
    error("No PrinterServiceAccessor provided")
}

class MainActivity : ComponentActivity(), PrinterServiceAccessor {

    companion object {
        private const val TAG = "MainActivity"
        private const val PRINTER_SERVICE_PACKAGE = "recieptservice.com.recieptservice"
        private const val PRINTER_SERVICE_CLASS = "recieptservice.com.recieptservice.service.PrinterService"
    }

    private val printerServiceRef = AtomicReference<PrinterInterface?>(null)

    @Volatile
    private var isPrinterServiceBound = false

    private val printerServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            printerServiceRef.set(PrinterInterface.Stub.asInterface(service))
            isPrinterServiceBound = true
            Log.i(TAG, "Printer service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            printerServiceRef.set(null)
            isPrinterServiceBound = false
            Log.w(TAG, "Printer service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bindToPrinterService()
        setContent {
            IdentipayPOSTheme {
                CompositionLocalProvider(LocalPrinterService provides this) {
                    AppNavGraph()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isPrinterServiceBound) {
            unbindService(printerServiceConnection)
            isPrinterServiceBound = false
        }
    }

    // ── PrinterServiceAccessor ──

    override fun getPrinterService(): PrinterInterface? = printerServiceRef.get()

    override fun isBound(): Boolean = isPrinterServiceBound

    override fun attemptBind() {
        if (!isPrinterServiceBound) {
            bindToPrinterService()
        }
    }

    private fun bindToPrinterService() {
        val intent = Intent().apply {
            component = ComponentName(PRINTER_SERVICE_PACKAGE, PRINTER_SERVICE_CLASS)
        }
        try {
            bindService(intent, printerServiceConnection, Context.BIND_AUTO_CREATE)
            Log.i(TAG, "Binding to printer service...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind to printer service", e)
        }
    }
}
