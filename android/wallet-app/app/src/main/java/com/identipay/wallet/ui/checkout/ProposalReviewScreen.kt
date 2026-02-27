package com.identipay.wallet.ui.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.identipay.wallet.network.CommerceProposal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposalReviewScreen(
    viewModel: CheckoutViewModel,
    onBack: () -> Unit,
    onConfirm: (txId: String, txDigest: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate to confirm on success
    if (uiState.step == CheckoutStep.SUCCESS && uiState.proposal != null && uiState.txDigest != null) {
        onConfirm(uiState.proposal!!.transactionId, uiState.txDigest!!)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Payment") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<")
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading proposal...")
                }
            }
            uiState.proposal != null -> {
                ProposalContent(
                    proposal = uiState.proposal!!,
                    step = uiState.step,
                    error = uiState.error,
                    onPay = viewModel::pay,
                    onBack = onBack,
                    modifier = Modifier.padding(padding),
                )
            }
            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = onBack) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProposalContent(
    proposal: CommerceProposal,
    step: CheckoutStep,
    error: String?,
    onPay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        // Merchant info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Merchant",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = proposal.merchant.name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = proposal.merchant.did,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Line items
        Text(
            text = "Items",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))

        for (item in proposal.items) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${item.quantity}x ${item.name}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${item.unitPrice} ${item.currency ?: proposal.amount.currency}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Total
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${proposal.amount.value} ${proposal.amount.currency}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Deliverables
        if (proposal.deliverables.receipt || proposal.deliverables.warranty != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Deliverables",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (proposal.deliverables.receipt) {
                        Text("Encrypted receipt", style = MaterialTheme.typography.bodyMedium)
                    }
                    proposal.deliverables.warranty?.let { w ->
                        Text(
                            "Warranty: ${w.durationDays} days${if (w.transferable) " (transferable)" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // Constraints (age gate)
        proposal.constraints?.ageGate?.let { age ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Text(
                    text = "Age verification required: ${age}+",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Error
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Progress indicator for checkout steps
        if (step != CheckoutStep.IDLE && step != CheckoutStep.ERROR) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (step) {
                        CheckoutStep.SIGNING -> "Signing intent..."
                        CheckoutStep.PROVING_AGE -> "Generating age proof..."
                        CheckoutStep.ENCRYPTING -> "Encrypting artifacts..."
                        CheckoutStep.SETTLING -> "Settling on-chain..."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Pay / Cancel buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                enabled = step == CheckoutStep.IDLE || step == CheckoutStep.ERROR,
            ) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onPay,
                modifier = Modifier.weight(1f),
                enabled = step == CheckoutStep.IDLE,
            ) {
                Text("Pay ${proposal.amount.value} ${proposal.amount.currency}")
            }
        }
    }
}
