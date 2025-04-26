package com.identipay.wallet.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Button
import androidx.compose.ui.platform.LocalContext
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.identipay.wallet.viewmodel.DashboardViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.identipay.wallet.R

object WalletDestinations {
    const val TRANSACTION_CONFIRM_ROUTE = "transaction_confirm"
    const val TRANSACTION_ID_ARG = "transactionId"
    val routeWithArgs = "$TRANSACTION_CONFIRM_ROUTE/{$TRANSACTION_ID_ARG}"
}

@Composable
fun WalletDashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel
) {
    val userDid by viewModel.userDid.collectAsState()
    val context = LocalContext.current

    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedTransactionId = result.contents
            Log.i("DashboardScreen", "QR Scanned Result: $scannedTransactionId")
            if (scannedTransactionId.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\$"))) {
                navController.navigate("${WalletDestinations.TRANSACTION_CONFIRM_ROUTE}/$scannedTransactionId")
            } else {
                Toast.makeText(context, "Invalid Transaction QR Code.", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.i("DashboardScreen", "QR Scan Cancelled")
            Toast.makeText(context, "Scan cancelled.", Toast.LENGTH_SHORT).show()
        }
    }


    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF5C9DFF), Color(0xFFE0EFFF), Color.White),
        startY = 0f,
        endY = 800f
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Hold near reader",
                color = Color.Black.copy(alpha = 0.7f),
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {

                    Image(
                        painter = painterResource(id = R.drawable.identipay_logo),
                        contentDescription = "identiPay Logo",
                        modifier = Modifier.width(160.dp).padding(15.dp).align(Alignment.TopEnd),
                        contentScale = ContentScale.Fit
                    )

                    Column(
                        modifier = Modifier.align(Alignment.BottomStart),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Your IdentiPay DID",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (userDid == null || userDid == "Loading DID...") {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        } else if (userDid?.startsWith("Error:") == true) {
                            Text(
                                text = userDid!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Left
                            )
                        } else {
                            Text(
                                text = userDid!!,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Left,
                                maxLines = 3
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val options = ScanOptions().apply {
                        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        setPrompt("Scan IdentiPay QR Code")
                        setBeepEnabled(true)
                        setOrientationLocked(false)
                    }
                    qrScanLauncher.launch(options)
                },
                modifier = Modifier.height(52.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Scan Payment Code", fontSize = 16.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
