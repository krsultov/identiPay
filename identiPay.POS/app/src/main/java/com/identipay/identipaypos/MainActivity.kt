package com.identipay.identipaypos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.identipay.identipaypos.ui.theme.IdentiPayPOSTheme
import com.identipay.identipaypos.viewmodel.PosViewModel
import recieptservice.com.recieptservice.PrinterInterface
import java.util.concurrent.atomic.AtomicReference
import com.identipay.identipaypos.ui.screens.PosAppScreen

val LocalPrinterService = staticCompositionLocalOf<PrinterServiceAccessor?> { null }

interface PrinterServiceAccessor {
    fun getPrinterService(): PrinterInterface?
    fun isBound(): Boolean
    fun attemptBind()
}

class MainActivity : ComponentActivity(), PrinterServiceAccessor {
    private val printerServiceRef = AtomicReference<PrinterInterface?>()
    @Volatile private var isPrinterServiceBound = false

    private val printerServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i("MainActivity", "Printer Service Connected")
            printerServiceRef.set(PrinterInterface.Stub.asInterface(service))
            isPrinterServiceBound = true
            Toast.makeText(this@MainActivity, "Printer Ready", Toast.LENGTH_SHORT).show()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w("MainActivity", "Printer Service Disconnected")
            printerServiceRef.set(null)
            isPrinterServiceBound = false
            Toast.makeText(this@MainActivity, "Printer Disconnected", Toast.LENGTH_SHORT).show()
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.e("MainActivity", "Printer Service Binding Died")
            onServiceDisconnected(name)
        }

        override fun onNullBinding(name: ComponentName?) {
            Log.e("MainActivity", "Printer Service Null Binding - Service available but didn't bind?")
            isPrinterServiceBound = false
            Toast.makeText(this@MainActivity, "Printer Binding Failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bindToPrinterService()

        setContent {
            CompositionLocalProvider(LocalPrinterService provides this) {
                IdentiPayPOSTheme {
                    PosAppScreen(viewModel = PosViewModel())
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindPrinterService()
    }

    override fun getPrinterService(): PrinterInterface? {
        return if (isPrinterServiceBound) printerServiceRef.get() else null
    }
    override fun isBound(): Boolean {
        return isPrinterServiceBound
    }

    override fun attemptBind() {
        bindToPrinterService()
    }

    private fun bindToPrinterService() {
        if (!isPrinterServiceBound) {
            Log.d("MainActivity", "Attempting to bind to printer service...")
            val intent = Intent()
            val servicePackage = "recieptservice.com.recieptservice"
            val serviceClass = "recieptservice.com.recieptservice.service.PrinterService"
            intent.component = ComponentName(servicePackage, serviceClass)

            try {
                val bound = bindService(intent, printerServiceConnection, Context.BIND_AUTO_CREATE)
                if (!bound) {
                    Log.e("MainActivity", "bindService returned false. Service missing or inaccessible?")
                    Toast.makeText(this, "Printer Service Not Found/Accessible", Toast.LENGTH_LONG).show()
                    isPrinterServiceBound = false
                } else {
                    Log.d("MainActivity", "bindService call initiated.")
                }
            } catch (sec: SecurityException) {
                Log.e("MainActivity", "SecurityException binding printer service.", sec)
                Toast.makeText(this, "Permission denied for Printer", Toast.LENGTH_LONG).show()
                isPrinterServiceBound = false
            } catch (e: Exception) {
                Log.e("MainActivity", "Exception binding printer service.", e)
                Toast.makeText(this, "Error connecting to Printer", Toast.LENGTH_LONG).show()
                isPrinterServiceBound = false
            }
        } else {
            Log.d("MainActivity", "Printer service binding already established or pending.")
        }
    }

    private fun unbindPrinterService() {
        if (isPrinterServiceBound) {
            Log.d("MainActivity", "Unbinding from printer service.")
            try {
                unbindService(printerServiceConnection)
            } catch (e: IllegalArgumentException) {
                Log.w("MainActivity", "Service was already unbound?", e)
            } finally {
                isPrinterServiceBound = false
                printerServiceRef.set(null)
            }
        }
    }
}