package com.identipay.wallet.ui.stealth

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.wallet.data.db.dao.StealthAddressDao
import com.identipay.wallet.data.db.entity.StealthAddressEntity
import com.identipay.wallet.data.repository.AnnouncementRepository
import com.identipay.wallet.data.repository.BalanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StealthAddressesUiState(
    val addresses: List<StealthAddressEntity> = emptyList(),
    val isLoading: Boolean = true,
    val dumpedSecret: DumpedSecret? = null,
)

data class DumpedSecret(
    val stealthAddress: String,
    val privateKeyHex: String,
    val privateKeyBase64: String,
)

@HiltViewModel
class StealthAddressesViewModel @Inject constructor(
    private val stealthAddressDao: StealthAddressDao,
    private val announcementRepository: AnnouncementRepository,
    private val balanceRepository: BalanceRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "StealthAddressesVM"
    }

    private val _uiState = MutableStateFlow(StealthAddressesUiState())
    val uiState: StateFlow<StealthAddressesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            stealthAddressDao.getAll().collect { addresses ->
                _uiState.update { it.copy(addresses = addresses, isLoading = false) }
            }
        }
    }

    fun deleteAddress(address: String) {
        viewModelScope.launch {
            Log.d(TAG, "Deleting stealth address: $address")
            stealthAddressDao.deleteByAddress(address)
            // Full rescan will re-derive and recover the address from on-chain
            // announcements if it still exists, restoring the private key
            Log.d(TAG, "Triggering full rescan to recover any announced addresses")
            val recovered = announcementRepository.fullRescan()
            if (recovered > 0) {
                Log.d(TAG, "Recovered $recovered stealth address(es) from announcements")
                balanceRepository.refreshAll()
            }
        }
    }

    fun dumpSecret(entity: StealthAddressEntity) {
        val privKeyBytes = Base64.decode(entity.stealthPrivKeyEnc, Base64.NO_WRAP)
        val hex = privKeyBytes.joinToString("") { "%02x".format(it) }
        _uiState.update {
            it.copy(
                dumpedSecret = DumpedSecret(
                    stealthAddress = entity.stealthAddress,
                    privateKeyHex = hex,
                    privateKeyBase64 = entity.stealthPrivKeyEnc,
                )
            )
        }
    }

    fun clearDump() {
        _uiState.update { it.copy(dumpedSecret = null) }
    }
}
