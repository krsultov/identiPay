package com.identipay.wallet.ui.identity

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

private const val TAG = "MrzScan"

/**
 * Parses TD3 (passport) MRZ from recognized text lines.
 * Returns triple of (docNumber, dateOfBirth, expiryDate) in YYMMDD format, or null.
 */
private fun parseMrzFromText(text: String): Triple<String, String, String>? {
    // Normalize: replace common OCR mistakes in MRZ context
    val lines = text.lines()
        .map { it.trim().uppercase().replace(" ", "") }
        .filter { it.length >= 30 }

    // Look for TD3 MRZ line 2 pattern:
    // 44 chars, contains '<', has digits in date positions
    for (line in lines) {
        // TD3 line 2 is 44 chars, but OCR may be slightly off
        if (line.length < 42 || line.length > 46) continue
        // Line 2 typically starts with alphanumeric (doc number) and contains '<'
        if (!line.contains('<') && !line.contains('«')) continue

        val normalized = line.replace('«', '<').replace('O', '0')

        // Try to extract from standard positions
        // Doc number: positions 0-8 (9 chars), check digit at 9
        // Nationality: 10-12
        // DOB: 13-18 (YYMMDD), check digit at 19
        // Sex: 20
        // Expiry: 21-26 (YYMMDD), check digit at 27
        if (normalized.length >= 28) {
            val docNumberRaw = normalized.substring(0, 9).trimEnd('<')
            val dobRaw = normalized.substring(13, 19)
            val expiryRaw = normalized.substring(21, 27)

            // Validate: doc number should be alphanumeric
            if (!docNumberRaw.all { it.isLetterOrDigit() }) continue
            if (docNumberRaw.isEmpty()) continue

            // Validate dates are 6 digits
            if (!dobRaw.all { it.isDigit() } || dobRaw.length != 6) continue
            if (!expiryRaw.all { it.isDigit() } || expiryRaw.length != 6) continue

            // Basic date range check
            val dobMonth = dobRaw.substring(2, 4).toIntOrNull() ?: continue
            val dobDay = dobRaw.substring(4, 6).toIntOrNull() ?: continue
            val expMonth = expiryRaw.substring(2, 4).toIntOrNull() ?: continue
            val expDay = expiryRaw.substring(4, 6).toIntOrNull() ?: continue

            if (dobMonth !in 1..12 || dobDay !in 1..31) continue
            if (expMonth !in 1..12 || expDay !in 1..31) continue

            Log.d(TAG, "MRZ parsed: doc=$docNumberRaw, dob=$dobRaw, exp=$expiryRaw")
            return Triple(docNumberRaw, dobRaw, expiryRaw)
        }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MrzScanScreen(
    viewModel: IdentityViewModel,
    onMrzScanned: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Passport MRZ") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
        ) {
            if (hasCameraPermission) {
                CameraPreviewWithMrzAnalysis(
                    onMrzDetected = { docNumber, dob, expiry ->
                        if (!isProcessing) {
                            isProcessing = true
                            scanResult = "$docNumber / $dob / $expiry"
                            viewModel.setScannedMrz(docNumber, dob, expiry)
                            onMrzScanned()
                        }
                    },
                )

                // Overlay instructions
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(16.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Point camera at the bottom of your passport's data page",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Align the two lines of text at the bottom (<<<...)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (scanResult != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Detected: $scanResult",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Green,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Camera permission is required to scan the passport MRZ.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreviewWithMrzAnalysis(
    onMrzDetected: (docNumber: String, dob: String, expiry: String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var detected by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            textRecognizer.close()
            executor.shutdown()
        }
    }

    LaunchedEffect(cameraProviderFuture) {
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder()
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(executor) { imageProxy: ImageProxy ->
            if (detected) {
                imageProxy.close()
                return@setAnalyzer
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val inputImage = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
                textRecognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        val result = parseMrzFromText(visionText.text)
                        if (result != null && !detected) {
                            detected = true
                            onMrzDetected(result.first, result.second, result.third)
                        }
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )
}
