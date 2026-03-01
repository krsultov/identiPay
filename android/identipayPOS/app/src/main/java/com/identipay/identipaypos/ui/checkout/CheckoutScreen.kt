package com.identipay.identipaypos.ui.checkout

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.identipay.identipaypos.data.CartViewModel
import com.identipay.identipaypos.data.IdentipayApi
import com.identipay.identipaypos.data.Merchant
import com.identipay.identipaypos.data.ProposalResponse
import com.identipay.identipaypos.data.ReceiptData
import com.identipay.identipaypos.data.ReceiptLineItem
import com.identipay.identipaypos.data.ReceiptPrinter
import com.identipay.identipaypos.data.SettlementListener
import com.identipay.identipaypos.LocalPrinterService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class CheckoutStep { REVIEW, CREATING, PAY, CONFIRMING, SUCCESS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    cartViewModel: CartViewModel,
    onBack: () -> Unit,
    onNewSale: () -> Unit,
) {
    val cartItems by cartViewModel.items.collectAsState()
    val total = cartItems.sumOf { it.product.price * it.quantity }
    val itemCount = cartItems.sumOf { it.quantity }
    val maxAgeGate = cartItems.maxOfOrNull { it.product.ageGate ?: 0 } ?: 0

    var step by remember { mutableStateOf(CheckoutStep.REVIEW) }
    var proposal by remember { mutableStateOf<ProposalResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var countdown by remember { mutableIntStateOf(900) }
    var txHash by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Countdown timer
    LaunchedEffect(step) {
        if (step == CheckoutStep.PAY) {
            countdown = 900
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
        }
    }

    // WebSocket settlement listener
    DisposableEffect(step, proposal?.transactionId) {
        val txId = proposal?.transactionId
        if (step != CheckoutStep.PAY || txId == null) {
            return@DisposableEffect onDispose { }
        }
        val listener = SettlementListener(
            transactionId = txId,
            onSettlement = { event ->
                txHash = event.suiTxDigest
                step = CheckoutStep.CONFIRMING
                scope.launch {
                    delay(2000)
                    step = CheckoutStep.SUCCESS
                }
            },
        )
        listener.connect()
        onDispose { listener.disconnect() }
    }

    Scaffold(
        topBar = {
            if (step == CheckoutStep.REVIEW || step == CheckoutStep.PAY) {
                TopAppBar(
                    title = {
                        Text(
                            if (step == CheckoutStep.REVIEW) "Checkout" else "Scan to Pay",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (step == CheckoutStep.PAY) step = CheckoutStep.REVIEW else onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = step,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            transitionSpec = {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
            },
            label = "checkout-step",
        ) { currentStep ->
            when (currentStep) {
                CheckoutStep.REVIEW -> ReviewStep(
                    cartItems = cartItems,
                    total = total,
                    itemCount = itemCount,
                    maxAgeGate = maxAgeGate,
                    error = error,
                    onPay = {
                        scope.launch {
                            step = CheckoutStep.CREATING
                            error = null
                            try {
                                val result = IdentipayApi.createProposal(
                                    items = cartItems,
                                    total = total,
                                    ageGate = if (maxAgeGate > 0) maxAgeGate else null,
                                )
                                proposal = result
                                step = CheckoutStep.PAY
                            } catch (e: Exception) {
                                error = e.message ?: "Failed to create proposal"
                                step = CheckoutStep.REVIEW
                            }
                        }
                    },
                )
                CheckoutStep.CREATING -> CreatingStep()
                CheckoutStep.PAY -> PayStep(
                    proposal = proposal,
                    countdown = countdown,
                    onCancel = { step = CheckoutStep.REVIEW },
                )
                CheckoutStep.CONFIRMING -> ConfirmingStep(maxAgeGate = maxAgeGate)
                CheckoutStep.SUCCESS -> SuccessStep(
                    cartItems = cartItems,
                    total = total,
                    transactionId = proposal?.transactionId ?: "",
                    intentHash = proposal?.intentHash ?: "",
                    txHash = txHash,
                    maxAgeGate = maxAgeGate,
                    onNewSale = onNewSale,
                )
            }
        }
    }
}

// ── Step: Review ──

@Composable
private fun ReviewStep(
    cartItems: List<com.identipay.identipaypos.data.CartItem>,
    total: Double,
    itemCount: Int,
    maxAgeGate: Int,
    error: String?,
    onPay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            "Order Summary",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "${Merchant.NAME} — $itemCount ${if (itemCount == 1) "item" else "items"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        // Line items
        cartItems.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.product.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${"%.2f".format(item.product.price)} USDC x ${item.quantity}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${"%.2f".format(item.product.price * item.quantity)} USDC",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Spacer(Modifier.height(8.dp))

        // Subtotal & fee
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${"%.2f".format(total)} USDC", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Network fee", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Free", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.tertiary)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // Total
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "${"%.2f".format(total)} USDC",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Privacy card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(modifier = Modifier.padding(14.dp)) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Privacy guaranteed by identiPay",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Atomic stealth payment prevents address reuse and purchase history linkage. End-to-end encrypted receipts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Age gate notice
        if (maxAgeGate > 0) {
            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.3f)),
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Age verification required (${maxAgeGate}+)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Your wallet will generate a zero-knowledge proof of age. Your birthdate stays private.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Error
        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Error", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onPay,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            enabled = cartItems.isNotEmpty(),
        ) {
            Text(
                "Pay ${"%.2f".format(total)} USDC with identiPay",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Step: Creating ──

@Composable
private fun CreatingStep() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.dp,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Securing connection",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Generating cryptographic intent...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Step: Pay (QR Code) ──

@Composable
private fun PayStep(
    proposal: ProposalResponse?,
    countdown: Int,
    onCancel: () -> Unit,
) {
    val qrPayload = proposal?.uri ?: ""
    val qrBitmap = remember(qrPayload) { generateQrBitmap(qrPayload, 600) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            "Scan to pay",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Open your identiPay wallet app and scan the QR code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        // QR code card
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Payment QR Code",
                        modifier = Modifier.size(220.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Countdown
                val isUrgent = countdown < 60
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            color = if (isUrgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        "EXPIRES IN ${formatTime(countdown)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Waiting indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Waiting for payment...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onCancel) {
            Text("Cancel payment", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Step: Confirming ──

@Composable
private fun ConfirmingStep(maxAgeGate: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                strokeWidth = 3.dp,
            )
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Processing...",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Verifying cryptographic proofs on Sui.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        Column(horizontalAlignment = Alignment.Start) {
            ConfirmStepRow("Intent verified", done = true)
            if (maxAgeGate > 0) {
                ConfirmStepRow("ZK age proof verified (${maxAgeGate}+)", done = true)
            }
            ConfirmStepRow("Stealth address derived", done = true)
            ConfirmStepRow("Atomic settlement", active = true)
            ConfirmStepRow("Minting payload receipt")
        }
    }
}

@Composable
private fun ConfirmStepRow(label: String, done: Boolean = false, active: Boolean = false) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp, horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            done -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            active -> {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (done || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Step: Success ──

@Composable
private fun SuccessStep(
    cartItems: List<com.identipay.identipaypos.data.CartItem>,
    total: Double,
    transactionId: String,
    intentHash: String,
    txHash: String,
    maxAgeGate: Int,
    onNewSale: () -> Unit,
) {
    val printerAccessor = LocalPrinterService.current
    val scope = rememberCoroutineScope()

    // Auto-print receipt on success
    LaunchedEffect(transactionId) {
        val receiptData = ReceiptData(
            merchantName = Merchant.NAME,
            merchantTagline = Merchant.TAGLINE,
            items = cartItems.map { item ->
                ReceiptLineItem(
                    name = item.product.name,
                    quantity = item.quantity,
                    unitPrice = item.product.price,
                    total = item.product.price * item.quantity,
                )
            },
            subtotal = total,
            currency = "USDC",
            transactionId = transactionId,
            intentHash = intentHash,
            suiTxDigest = txHash,
            ageVerification = if (maxAgeGate > 0) maxAgeGate else null,
        )
        ReceiptPrinter.print(printerAccessor, receiptData)
    }

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-scale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))

        // Animated checkmark
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Payment successful",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your order is confirmed. A private receipt has been delivered to the stealth address.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        // Transaction details card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "TRANSACTION DETAILS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(12.dp))

                DetailRow("Transaction ID", if (transactionId.length > 8) transactionId.take(8) + "..." else transactionId, mono = true)
                if (intentHash.isNotEmpty()) {
                    DetailRow("Intent hash", intentHash.take(10) + "..." + intentHash.takeLast(6), mono = true)
                }
                if (txHash.isNotEmpty()) {
                    DetailRow("Sui tx digest", txHash.take(10) + "..." + txHash.takeLast(6), mono = true)
                }
                DetailRow("Settlement", "Atomic", highlight = true)
                if (maxAgeGate > 0) {
                    DetailRow("Age verification", "${maxAgeGate}+ (ZK proof)", highlight = true)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Reprint button
        Button(
            onClick = {
                scope.launch {
                    val receiptData = ReceiptData(
                        merchantName = Merchant.NAME,
                        merchantTagline = Merchant.TAGLINE,
                        items = cartItems.map { item ->
                            ReceiptLineItem(
                                name = item.product.name,
                                quantity = item.quantity,
                                unitPrice = item.product.price,
                                total = item.product.price * item.quantity,
                            )
                        },
                        subtotal = total,
                        currency = "USDC",
                        transactionId = transactionId,
                        intentHash = intentHash,
                        suiTxDigest = txHash,
                        ageVerification = if (maxAgeGate > 0) maxAgeGate else null,
                    )
                    ReceiptPrinter.print(printerAccessor, receiptData)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                Icons.Default.Receipt,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Reprint Receipt",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onNewSale,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            Text(
                "New Sale",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = if (mono) FontFamily.Monospace else FontFamily.SansSerif,
            ),
            fontWeight = FontWeight.Medium,
            color = if (highlight) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Helpers ──

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    if (content.isEmpty()) return null
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}
