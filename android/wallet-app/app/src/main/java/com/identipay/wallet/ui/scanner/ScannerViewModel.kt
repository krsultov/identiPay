package com.identipay.wallet.ui.scanner

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.wallet.data.repository.CommerceRepository
import com.identipay.wallet.network.CommerceProposal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScannerUiState(
    val isProcessing: Boolean = false,
    val error: String? = null,
    val scannedTxId: String? = null,
    val proposal: CommerceProposal? = null,
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val commerceRepository: CommerceRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "ScannerViewModel"
        private val IDENTIPAY_PAY_REGEX = Regex("""identipay://pay/([a-f0-9-]+)""")
        private val IDENTIPAY_URL_REGEX = Regex("""https?://identipay\.me/pay/([a-f0-9-]+)""")
        // did:identipay:<hostname>:<transaction-id> per whitepaper Section 5
        private val IDENTIPAY_DID_REGEX = Regex("""did:identipay:[^:]+:([a-f0-9-]+)""")
    }

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    /**
     * Parse a scanned QR code value and fetch the proposal if valid.
     */
    fun onQrScanned(rawValue: String) {
        if (_uiState.value.isProcessing) return

        val txId = extractTransactionId(rawValue)
        if (txId == null) {
            Log.d(TAG, "Ignoring non-identipay QR: $rawValue")
            return
        }

        _uiState.update { it.copy(isProcessing = true, error = null, scannedTxId = txId) }

        viewModelScope.launch {
            val result = commerceRepository.fetchProposal(txId)
            result.fold(
                onSuccess = { proposal ->
                    _uiState.update {
                        it.copy(isProcessing = false, proposal = proposal)
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = e.message ?: "Failed to fetch proposal",
                        )
                    }
                },
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetScanner() {
        _uiState.update { ScannerUiState() }
    }

    private fun extractTransactionId(rawValue: String): String? {
        // Try did:identipay:<hostname>:<transaction-id> (whitepaper Section 5)
        IDENTIPAY_DID_REGEX.find(rawValue)?.let {
            return it.groupValues[1]
        }
        // Try identipay://pay/{txId}
        IDENTIPAY_PAY_REGEX.find(rawValue)?.let {
            return it.groupValues[1]
        }
        // Try https://identipay.me/pay/{txId}
        IDENTIPAY_URL_REGEX.find(rawValue)?.let {
            return it.groupValues[1]
        }
        // Try raw UUID
        if (rawValue.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))) {
            return rawValue
        }
        return null
    }
}
