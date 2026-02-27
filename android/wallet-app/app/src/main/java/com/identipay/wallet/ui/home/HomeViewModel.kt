package com.identipay.wallet.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.wallet.data.db.dao.TransactionDao
import com.identipay.wallet.data.db.entity.TransactionEntity
import com.identipay.wallet.data.preferences.UserPreferences
import com.identipay.wallet.data.repository.AnnouncementRepository
import com.identipay.wallet.data.repository.BalanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val registeredName: String? = null,
    val balance: String = "0.00",
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
    private val balanceRepository: BalanceRepository,
    private val announcementRepository: AnnouncementRepository,
    private val transactionDao: TransactionDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "HomeViewModel"
    }

    init {
        viewModelScope.launch {
            userPreferences.registeredName.collect { name ->
                Log.d(TAG, "registeredName=$name")
                _uiState.update { it.copy(registeredName = name, isLoading = false) }
            }
        }
        viewModelScope.launch {
            balanceRepository.formattedBalance.collect { balance ->
                Log.d(TAG, "formattedBalance flow emitted: $balance")
                _uiState.update { it.copy(balance = balance) }
            }
        }
        viewModelScope.launch {
            transactionDao.getRecent(5).collect { txs ->
                Log.d(TAG, "recentTransactions flow emitted: ${txs.size} txs")
                _uiState.update { it.copy(recentTransactions = txs) }
            }
        }
        // Initial scan + refresh on launch
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            Log.d(TAG, "refresh: starting")
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val found = announcementRepository.scanNew()
                Log.d(TAG, "refresh: scanNew returned $found new addresses")
                balanceRepository.refreshAll()
                Log.d(TAG, "refresh: refreshAll completed")
            } catch (e: Exception) {
                Log.e(TAG, "refresh: FAILED", e)
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
