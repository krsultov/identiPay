package com.identipay.wallet.ui.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.wallet.crypto.SeedManager
import com.identipay.wallet.data.repository.IdentityRepository
import com.identipay.wallet.data.repository.InitKeysResult
import com.identipay.wallet.network.TransactionBuilder
import com.identipay.wallet.nfc.CredentialData
import com.identipay.wallet.nfc.MrzParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jmrtd.BACKey
import javax.inject.Inject

data class IdentityUiState(
    val docNumber: String = "",
    val dateOfBirth: String = "",
    val expiryDate: String = "",
    val mrzValid: Boolean = false,
    val bacKey: BACKey? = null,
    val nfcStatus: NfcStatus = NfcStatus.Idle,
    val credentialData: CredentialData? = null,
    val pin: String = "",
    val pinConfirm: String = "",
    val pinError: String? = null,
    val keyDerivationStatus: KeyDerivationStatus = KeyDerivationStatus.Idle,
    val reactivatedName: String? = null,
    val chosenName: String = "",
    val nameAvailable: Boolean? = null,
    val nameCheckLoading: Boolean = false,
    val registrationStatus: RegistrationStatus = RegistrationStatus.Idle,
    val error: String? = null,
)

enum class NfcStatus {
    Idle,
    Scanning,
    Reading,
    Success,
    Error,
}

enum class KeyDerivationStatus {
    Idle,
    Deriving,
    Done,
    Error,
}

enum class RegistrationStatus {
    Idle,
    GeneratingProof,
    Submitting,
    Success,
    Error,
}

@HiltViewModel
class IdentityViewModel @Inject constructor(
    private val seedManager: SeedManager,
    private val mrzParser: MrzParser,
    private val identityRepository: IdentityRepository,
    private val transactionBuilder: TransactionBuilder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IdentityUiState())
    val uiState: StateFlow<IdentityUiState> = _uiState.asStateFlow()

    private var nameCheckJob: Job? = null

    fun updateDocNumber(value: String) {
        _uiState.update { it.copy(docNumber = value) }
        validateMrz()
    }

    fun updateDateOfBirth(value: String) {
        _uiState.update { it.copy(dateOfBirth = value) }
        validateMrz()
    }

    fun updateExpiryDate(value: String) {
        _uiState.update { it.copy(expiryDate = value) }
        validateMrz()
    }

    /**
     * Set MRZ fields from OCR scan result and auto-validate.
     */
    fun setScannedMrz(docNumber: String, dateOfBirth: String, expiryDate: String) {
        _uiState.update {
            it.copy(docNumber = docNumber, dateOfBirth = dateOfBirth, expiryDate = expiryDate)
        }
        validateMrz()
    }

    private fun validateMrz() {
        val state = _uiState.value
        val valid = mrzParser.isValidDocNumber(state.docNumber) &&
                mrzParser.isValidDate(state.dateOfBirth) &&
                mrzParser.isValidDate(state.expiryDate)
        val bacKey = if (valid) {
            try {
                mrzParser.createBacKey(state.docNumber, state.dateOfBirth, state.expiryDate)
            } catch (e: Exception) {
                null
            }
        } else null
        _uiState.update { it.copy(mrzValid = valid, bacKey = bacKey) }
    }

    fun onNfcScanStarted() {
        _uiState.update { it.copy(nfcStatus = NfcStatus.Scanning) }
    }

    fun onNfcProgress(step: String) {
        _uiState.update { it.copy(nfcStatus = NfcStatus.Reading) }
    }

    fun onNfcSuccess(credential: CredentialData) {
        _uiState.update {
            it.copy(
                nfcStatus = NfcStatus.Success,
                credentialData = credential,
            )
        }
    }

    fun onNfcError(error: String) {
        _uiState.update {
            it.copy(
                nfcStatus = NfcStatus.Error,
                error = error,
            )
        }
    }

    // --- PIN entry ---

    fun updatePin(value: String) {
        if (value.length <= 6 && value.all { it.isDigit() }) {
            _uiState.update { it.copy(pin = value, pinError = null) }
        }
    }

    fun updatePinConfirm(value: String) {
        if (value.length <= 6 && value.all { it.isDigit() }) {
            _uiState.update { it.copy(pinConfirm = value, pinError = null) }
        }
    }

    /**
     * Validate PINs match, then derive seed from passport + PIN.
     */
    fun confirmPin() {
        val state = _uiState.value
        if (state.pin.length != 6) {
            _uiState.update { it.copy(pinError = "PIN must be exactly 6 digits") }
            return
        }
        if (state.pin != state.pinConfirm) {
            _uiState.update { it.copy(pinError = "PINs do not match") }
            return
        }
        val credential = state.credentialData ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(keyDerivationStatus = KeyDerivationStatus.Deriving) }
            try {
                val result = withContext(Dispatchers.Default) {
                    identityRepository.initializeKeys(credential, state.pin)
                }
                val reactivatedName = when (result) {
                    is InitKeysResult.Reactivated -> result.name
                    is InitKeysResult.NewRegistration -> null
                }
                _uiState.update {
                    it.copy(
                        keyDerivationStatus = KeyDerivationStatus.Done,
                        reactivatedName = reactivatedName,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        keyDerivationStatus = KeyDerivationStatus.Error,
                        error = e.message,
                    )
                }
            }
        }
    }

    // --- Name picker ---

    fun updateChosenName(name: String) {
        val cleanName = name.lowercase().trim()
        _uiState.update {
            it.copy(
                chosenName = cleanName,
                nameAvailable = null,
            )
        }

        // Debounced availability check
        nameCheckJob?.cancel()
        if (cleanName.length >= 3 && transactionBuilder.isValidName(cleanName)) {
            nameCheckJob = viewModelScope.launch {
                delay(500) // debounce
                _uiState.update { it.copy(nameCheckLoading = true) }
                try {
                    val available = identityRepository.isNameAvailable(cleanName)
                    _uiState.update {
                        it.copy(nameAvailable = available, nameCheckLoading = false)
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(nameAvailable = null, nameCheckLoading = false)
                    }
                }
            }
        }
    }

    fun register() {
        val state = _uiState.value
        val credential = state.credentialData ?: return
        val name = state.chosenName

        if (!transactionBuilder.isValidName(name)) return
        if (state.nameAvailable != true) return

        viewModelScope.launch {
            _uiState.update { it.copy(registrationStatus = RegistrationStatus.GeneratingProof) }
            try {
                _uiState.update { it.copy(registrationStatus = RegistrationStatus.Submitting) }
                val txDigest = identityRepository.registerName(name, credential)
                _uiState.update { it.copy(registrationStatus = RegistrationStatus.Success) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        registrationStatus = RegistrationStatus.Error,
                        error = e.message,
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
