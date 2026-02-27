package com.identipay.wallet.ui.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.wallet.data.repository.BalanceRepository
import com.identipay.wallet.data.repository.PaymentRepository
import com.identipay.wallet.data.repository.SendResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SendUiState(
    val recipientName: String = "",
    val amount: String = "",
    val isSending: Boolean = false,
    val error: String? = null,
    val txDigest: String? = null,
    val availableBalance: String = "0.00",
)

@HiltViewModel
class SendViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
    private val balanceRepository: BalanceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SendUiState())
    val uiState: StateFlow<SendUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            balanceRepository.formattedBalance.collect { balance ->
                _uiState.update { it.copy(availableBalance = balance) }
            }
        }
    }

    fun updateRecipient(name: String) {
        _uiState.update { it.copy(recipientName = name, error = null) }
    }

    fun updateAmount(amount: String) {
        _uiState.update { it.copy(amount = amount, error = null) }
    }

    fun send() {
        val state = _uiState.value
        if (state.recipientName.isBlank()) {
            _uiState.update { it.copy(error = "Enter a recipient name") }
            return
        }
        val amountDecimal = state.amount.toDoubleOrNull()
        if (amountDecimal == null || amountDecimal <= 0) {
            _uiState.update { it.copy(error = "Enter a valid amount") }
            return
        }

        val amountMicros = (amountDecimal * 1_000_000).toLong()

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }

            val result = paymentRepository.sendToName(
                name = state.recipientName.removePrefix("@").removeSuffix(".idpay"),
                amountMicros = amountMicros,
            )

            when (result) {
                is SendResult.Success -> {
                    _uiState.update {
                        it.copy(isSending = false, txDigest = result.txDigest)
                    }
                    // Refresh balance after send
                    balanceRepository.refreshAll()
                }
                is SendResult.Error -> {
                    _uiState.update {
                        it.copy(isSending = false, error = result.message)
                    }
                }
            }
        }
    }

    fun clearResult() {
        _uiState.update { it.copy(txDigest = null, error = null) }
    }
}
