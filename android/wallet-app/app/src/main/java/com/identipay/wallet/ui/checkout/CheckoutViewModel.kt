package com.identipay.wallet.ui.checkout

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.wallet.data.repository.BalanceRepository
import com.identipay.wallet.data.repository.CommerceRepository
import com.identipay.wallet.data.repository.CommerceResult
import com.identipay.wallet.network.CommerceProposal
import com.identipay.wallet.network.TransactionStatus
import com.identipay.wallet.network.WebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CheckoutStep {
    IDLE, SIGNING, PROVING_AGE, ENCRYPTING, SETTLING, SUCCESS, ERROR
}

data class CheckoutUiState(
    val proposal: CommerceProposal? = null,
    val isLoading: Boolean = false,
    val step: CheckoutStep = CheckoutStep.IDLE,
    val txDigest: String? = null,
    val error: String? = null,
    val wsStatus: TransactionStatus? = null,
)

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val commerceRepository: CommerceRepository,
    private val balanceRepository: BalanceRepository,
    private val webSocketManager: WebSocketManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val TAG = "CheckoutViewModel"
    }

    private val txId: String = savedStateHandle["txId"] ?: ""

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    init {
        if (txId.isNotBlank()) {
            loadProposal(txId)
        }
    }

    fun loadProposal(transactionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = commerceRepository.fetchProposal(transactionId)
            result.fold(
                onSuccess = { proposal ->
                    _uiState.update {
                        it.copy(isLoading = false, proposal = proposal)
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load proposal",
                        )
                    }
                },
            )
        }
    }

    /**
     * Execute the checkout: sign, encrypt, settle on-chain.
     */
    fun pay() {
        val proposal = _uiState.value.proposal ?: return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(step = CheckoutStep.SIGNING) }

                val hasAgeGate = proposal.constraints?.ageGate != null

                if (hasAgeGate) {
                    _uiState.update { it.copy(step = CheckoutStep.PROVING_AGE) }
                    // ZK proof generation would go here; for now, pass null
                }

                _uiState.update { it.copy(step = CheckoutStep.ENCRYPTING) }

                _uiState.update { it.copy(step = CheckoutStep.SETTLING) }

                val result = commerceRepository.executeCheckout(proposal)

                when (result) {
                    is CommerceResult.Success -> {
                        _uiState.update {
                            it.copy(
                                step = CheckoutStep.SUCCESS,
                                txDigest = result.txDigest,
                            )
                        }
                        // Observe WebSocket for settlement confirmation
                        observeSettlement(proposal.transactionId)
                        // Refresh balance
                        balanceRepository.refreshAll()
                    }
                    is CommerceResult.Error -> {
                        _uiState.update {
                            it.copy(
                                step = CheckoutStep.ERROR,
                                error = result.message,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Checkout failed", e)
                _uiState.update {
                    it.copy(
                        step = CheckoutStep.ERROR,
                        error = e.message ?: "Checkout failed",
                    )
                }
            }
        }
    }

    private fun observeSettlement(transactionId: String) {
        viewModelScope.launch {
            webSocketManager.observe(transactionId).collect { update ->
                _uiState.update { it.copy(wsStatus = update.status) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, step = CheckoutStep.IDLE) }
    }
}
