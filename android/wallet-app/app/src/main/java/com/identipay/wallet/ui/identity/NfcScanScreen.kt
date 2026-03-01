package com.identipay.wallet.ui.identity

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.identipay.wallet.nfc.CredentialData
import com.identipay.wallet.nfc.PassportReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcScanScreen(
    viewModel: IdentityViewModel,
    passportReader: PassportReader,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(uiState.nfcStatus) {
        if (uiState.nfcStatus == NfcStatus.Success) {
            onSuccess()
        }
    }

    DisposableEffect(activity, uiState.bacKey) {
        val bacKey = uiState.bacKey
        if (activity != null && bacKey != null) {
            viewModel.onNfcScanStarted()
            passportReader.enableReaderMode(
                activity,
                bacKey,
                object : PassportReader.ReadCallback {
                    override fun onProgress(step: String) {
                        viewModel.onNfcProgress(step)
                    }

                    override fun onSuccess(credential: CredentialData) {
                        viewModel.onNfcSuccess(credential)
                    }

                    override fun onError(error: String) {
                        viewModel.onNfcError(error)
                    }
                },
            )
        }

        onDispose {
            if (activity != null) {
                passportReader.disableReaderMode(activity)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Passport") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                when (uiState.nfcStatus) {
                    NfcStatus.Idle, NfcStatus.Scanning -> {
                        PulsingNfcIndicator(
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Hold your passport against the back of your phone",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Keep it steady until reading is complete",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    NfcStatus.Reading -> {
                        PulsingNfcIndicator(
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = "Reading passport data...",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Do not move your phone",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    NfcStatus.Success -> {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Passport read successfully",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    NfcStatus.Error -> {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "Reading failed",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = uiState.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingNfcIndicator(
    color: Color,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_pulse")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_outer",
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_inner",
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale1)
                .border(2.dp, color.copy(alpha = 0.2f), CircleShape),
        )
        // Inner pulsing ring
        Box(
            modifier = Modifier
                .size(90.dp)
                .scale(scale2)
                .border(2.dp, color.copy(alpha = 0.4f), CircleShape),
        )
        // Center icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(color.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Contactless,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = color,
            )
        }
    }
}
