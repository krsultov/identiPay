package com.identipay.wallet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.wallet.crypto.SeedManager
import com.identipay.wallet.data.preferences.UserPreferences
import com.identipay.wallet.data.preferences.WalletKeys
import com.identipay.wallet.data.repository.PaymentRepository
import com.identipay.wallet.data.repository.PoolRepository
import com.identipay.wallet.data.repository.PoolResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val registeredName: String? = null,
    val mnemonic: String? = null,
    val showMnemonic: Boolean = false,
    val viewingKeyHex: String? = null,
    val showViewingKey: Boolean = false,
    val poolNoteCount: Int = 0,
    val poolBalance: Long = 0L,
    val withdrawAllInProgress: Boolean = false,
    val withdrawAllResult: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val seedManager: SeedManager,
    private val walletKeys: WalletKeys,
    private val userPreferences: UserPreferences,
    private val poolRepository: PoolRepository,
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val name = userPreferences.registeredName.first()
            _uiState.update { it.copy(registeredName = name) }
            refreshPoolInfo()
        }
    }

    private suspend fun refreshPoolInfo() {
        val notes = poolRepository.getUnspentNotes()
        val balance = poolRepository.getTotalUnspent()
        _uiState.update { it.copy(poolNoteCount = notes.size, poolBalance = balance) }
    }

    /**
     * Reveal the 24-word mnemonic recovery phrase.
     * Should only be called after biometric authentication succeeds.
     */
    fun revealMnemonic() {
        try {
            val mnemonic = seedManager.getMnemonic()
            if (mnemonic != null) {
                _uiState.update { it.copy(showMnemonic = true, mnemonic = mnemonic) }
            } else {
                // Passport-derived wallets have no mnemonic
                _uiState.update {
                    it.copy(
                        showMnemonic = true,
                        mnemonic = "Recovery is via passport + PIN.\nYour seed is derived deterministically from your identity document.",
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(showMnemonic = true, mnemonic = "Failed to retrieve: ${e.message}")
            }
        }
    }

    fun hideMnemonic() {
        _uiState.update { it.copy(showMnemonic = false, mnemonic = null) }
    }

    /**
     * Get the viewing key for auditor delegation.
     * Should only be called after biometric authentication succeeds.
     */
    fun revealViewingKey() {
        try {
            val viewKeyPair = walletKeys.getViewKeyPair()
            val hex = viewKeyPair.privateKey.joinToString("") { "%02x".format(it) }
            _uiState.update { it.copy(showViewingKey = true, viewingKeyHex = hex) }
        } catch (e: Exception) {
            _uiState.update { it.copy(viewingKeyHex = "Failed: ${e.message}") }
        }
    }

    fun hideViewingKey() {
        _uiState.update { it.copy(showViewingKey = false, viewingKeyHex = null) }
    }

    /**
     * Withdraw all unclaimed pool notes to a fresh stealth address.
     */
    fun withdrawAllPoolNotes() {
        viewModelScope.launch {
            _uiState.update { it.copy(withdrawAllInProgress = true, withdrawAllResult = null) }
            try {
                // Derive a fresh stealth address for the withdrawal
                val counter = userPreferences.getReceiveCounterOnce()
                val receiveAddr = paymentRepository.deriveReceiveAddress(counter)

                val result = poolRepository.withdrawAll(receiveAddr.stealthAddress)
                when (result) {
                    is PoolResult.Success -> {
                        _uiState.update {
                            it.copy(withdrawAllResult = "All notes withdrawn successfully")
                        }
                    }
                    is PoolResult.Error -> {
                        _uiState.update {
                            it.copy(withdrawAllResult = "Error: ${result.message}")
                        }
                    }
                }
                refreshPoolInfo()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(withdrawAllResult = "Error: ${e.message}")
                }
            } finally {
                _uiState.update { it.copy(withdrawAllInProgress = false) }
            }
        }
    }

    fun clearWithdrawResult() {
        _uiState.update { it.copy(withdrawAllResult = null) }
    }
}
