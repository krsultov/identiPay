package com.identipay.wallet.ui.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.wallet.data.repository.PaymentRepository
import com.identipay.wallet.data.repository.ReceiveAddress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReceiveUiState(
    val address: ReceiveAddress? = null,
    val counter: Int = 0,
    val copied: Boolean = false,
)

@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiveUiState())
    val uiState: StateFlow<ReceiveUiState> = _uiState.asStateFlow()

    init {
        generateAddress()
    }

    fun generateAddress() {
        val counter = _uiState.value.counter
        viewModelScope.launch {
            val address = paymentRepository.deriveReceiveAddress(counter)
            _uiState.update {
                it.copy(address = address, counter = counter + 1, copied = false)
            }
        }
    }

    fun onCopied() {
        _uiState.update { it.copy(copied = true) }
    }
}
