package com.identipay.wallet.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import com.identipay.wallet.ui.common.IdentipayLogoMark
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.identipay.wallet.data.db.entity.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSend: () -> Unit = {},
    onReceive: () -> Unit = {},
    onHistory: () -> Unit = {},
    onScan: () -> Unit = {},
    onStealthAddresses: () -> Unit = {},
    onArtifacts: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    // Animate balance in on first load
    var balanceVisible by remember { mutableStateOf(false) }
    val balanceAlpha by animateFloatAsState(
        targetValue = if (balanceVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "balanceAlpha",
    )
    LaunchedEffect(uiState.balance) {
        balanceVisible = true
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                top = statusBarPadding.calculateTopPadding(),
                bottom = 32.dp,
            ),
        ) {
            // ── Top bar ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IdentipayLogoMark(
                            size = 26.dp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "identiPay",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    IconButton(
                        onClick = onSettings,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            }

            // ── Balance card ──
            item {
                Spacer(modifier = Modifier.height(20.dp))
                BalanceCard(
                    balance = uiState.balance,
                    registeredName = uiState.registeredName,
                    alpha = balanceAlpha,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            // ── Action buttons ──
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActionChip(
                        icon = Icons.Filled.ArrowUpward,
                        label = "Send",
                        onClick = onSend,
                        modifier = Modifier.weight(1f),
                    )
                    ActionChip(
                        icon = Icons.Filled.ArrowDownward,
                        label = "Receive",
                        onClick = onReceive,
                        modifier = Modifier.weight(1f),
                    )
                    ActionChip(
                        icon = Icons.Filled.QrCodeScanner,
                        label = "Scan",
                        onClick = onScan,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Quick links row ──
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuickLink(
                        icon = Icons.Filled.Shield,
                        label = "Stealth",
                        onClick = onStealthAddresses,
                        modifier = Modifier.weight(1f),
                    )
                    QuickLink(
                        icon = Icons.Filled.Receipt,
                        label = "Artifacts",
                        onClick = onArtifacts,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Recent transactions ──
            item {
                Spacer(modifier = Modifier.height(28.dp))
            }

            if (uiState.recentTransactions.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Recent Activity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        TextButton(onClick = onHistory) {
                            Text(
                                "See All",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 0.dp,
                        ),
                    ) {
                        Column {
                            uiState.recentTransactions.forEachIndexed { index, tx ->
                                TransactionRow(tx)
                                if (index < uiState.recentTransactions.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.recentTransactions.isEmpty() && !uiState.isLoading) {
                item {
                    Spacer(modifier = Modifier.height(48.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant
                                        .copy(alpha = 0.6f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No activity yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Send or receive USDC to get started",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

// ── Balance Card ──

@Composable
private fun BalanceCard(
    balance: String,
    registeredName: String?,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Subtle radial glow in the top-right corner
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.12f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.15f),
                            radius = size.width * 0.55f,
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.15f),
                        radius = size.width * 0.55f,
                    )
                }
                .padding(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(alpha),
            ) {
                // Tag line
                Text(
                    text = "Total Balance",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                        .copy(alpha = 0.7f),
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Balance amount
                Row(
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = balance,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1.5).sp,
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "USDC",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                            .copy(alpha = 0.6f),
                        modifier = Modifier.offset(y = (-6).dp),
                    )
                }

                // Username pill
                registeredName?.let { name ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                            .copy(alpha = 0.1f),
                    ) {
                        Text(
                            text = "@$name.idpay",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                .copy(alpha = 0.85f),
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 6.dp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

// ── Action Chip ──

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ── Quick Link ──

@Composable
private fun QuickLink(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Transaction Row ──

@Composable
private fun TransactionRow(tx: TransactionEntity) {
    val isSend = tx.type == "send"
    val isCommerce = tx.type == "commerce"
    val isDeposit = tx.type == "deposit"
    val isOutgoing = isSend || isCommerce

    val whole = tx.amount / 1_000_000
    val frac = (tx.amount % 1_000_000) / 10_000
    val amountStr = "%d.%02d".format(whole, frac)
    val dateStr = SimpleDateFormat("MMM d", Locale.US).format(Date(tx.timestamp))

    val icon = when {
        isCommerce -> Icons.Filled.Storefront
        isDeposit -> Icons.Filled.Shield
        isSend -> Icons.Filled.ArrowUpward
        else -> Icons.Filled.ArrowDownward
    }

    val label = when {
        isCommerce -> "Purchase"
        isDeposit -> "Pool Deposit"
        isSend -> "Sent"
        else -> "Received"
    }

    val iconTint = when {
        isOutgoing -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    val iconBg = when {
        isOutgoing -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = iconTint,
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Label + counterparty
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = tx.counterpartyName?.let { "@$it.idpay" } ?: dateStr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Amount + date
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (isOutgoing) "-" else "+"}$amountStr",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                ),
                color = if (isOutgoing) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.tertiary,
            )
            if (tx.counterpartyName != null) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
