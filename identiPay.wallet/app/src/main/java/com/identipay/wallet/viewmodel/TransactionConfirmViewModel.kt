package com.identipay.wallet.viewmodel

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.wallet.data.local.UserDao
import com.identipay.wallet.network.IdentiPayApiService
import com.identipay.wallet.security.KeyStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log
import androidx.annotation.RequiresApi
import java.nio.charset.StandardCharsets
import java.util.UUID
import com.google.gson.GsonBuilder


sealed class TransactionConfirmState {
    data object Idle : TransactionConfirmState()
    data object Loading : TransactionConfirmState()
    data class OfferLoaded(val transaction: TransactionDto) : TransactionConfirmState()
    data class Error(val message: String) : TransactionConfirmState()
}

sealed class SigningState {
    data object Idle : SigningState()
    data object AwaitingAuthentication : SigningState()
    data object SigningInProgress : SigningState()
    data object SigningComplete : SigningState()
    data class SigningFailed(val message: String) : SigningState()
}

data class TransactionDto(
    val id: UUID,
    val senderDid: String?,
    val status: String,
    val createdAt: String,
    val payload: TransactionPayloadDto?
)

data class TransactionPayloadDto(
    val id: UUID,
    val type: String,
    val recipientDid: String,
    val currency: String,
    val amount: Double,
    val metadataJson: String?
)

data class SignTransactionRequestDto(
    val Signature: String,
    val SenderDid: String
)


class TransactionConfirmViewModel(
    private val transactionIdString: String,
    private val apiService: IdentiPayApiService,
    private val userDao: UserDao,
    private val keyStoreManager: KeyStoreManager
) : ViewModel() {

    companion object { private const val TAG = "TxConfirmViewModel" }

    private val _uiState = MutableStateFlow<TransactionConfirmState>(TransactionConfirmState.Idle)
    val uiState: StateFlow<TransactionConfirmState> = _uiState.asStateFlow()

    private val _signingState = MutableStateFlow<SigningState>(SigningState.Idle)
    val signingState: StateFlow<SigningState> = _signingState.asStateFlow()

    var signatureResult: String? = null
        private set

    init {
        loadTransactionDetails()
    }

    fun loadTransactionDetails() {
        viewModelScope.launch {
            _uiState.value = TransactionConfirmState.Loading
            try {
                val transactionId = UUID.fromString(transactionIdString)
                Log.d(TAG, "Fetching transaction details for ID: $transactionId")
                val response = apiService.getTransactionById(transactionId)

                if (response.isSuccessful && response.body() != null) {
                    val transactionDto = response.body()!!
                    Log.d(TAG, "Transaction details loaded: ${transactionDto.id}, Status: ${transactionDto.status}")
                    if (transactionDto.payload == null) {
                        Log.e(TAG, "Transaction payload is null in response.")
                        _uiState.value = TransactionConfirmState.Error("Payload data missing.")
                    } else if (transactionDto.status != "Pending") {
                        Log.w(TAG, "Transaction status is not Pending: ${transactionDto.status}")
                        _uiState.value = TransactionConfirmState.Error("Transaction is not pending (Status: ${transactionDto.status})")
                    }
                    else {
                        _uiState.value = TransactionConfirmState.OfferLoaded(transactionDto)
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown API error"
                    Log.e(TAG, "Failed to load transaction details: ${response.code()} - $errorBody")
                    _uiState.value = TransactionConfirmState.Error("Error loading details: ${response.code()}")
                }
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid Transaction ID format: $transactionIdString", e)
                _uiState.value = TransactionConfirmState.Error("Invalid Transaction ID.")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading transaction details", e)
                _uiState.value = TransactionConfirmState.Error("Failed to load transaction.")
            }
        }
    }

    fun prepareSignatureData(transactionDto: TransactionDto): ByteArray? {
        _signingState.value = SigningState.AwaitingAuthentication
        try {
            return preparePayloadForSigning(transactionDto.payload!!)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing payload for signing", e)
            _signingState.value = SigningState.SigningFailed("Payload prep error: ${e.message}")
            return null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun handleAuthenticationSuccess(signature: ByteArray?, transactionDto: TransactionDto?) {
        viewModelScope.launch {
            if (signature == null) {
                Log.w(TAG, "Authentication succeeded but signature was null.")
                _signingState.value = SigningState.SigningFailed("Authentication succeeded but signature generation failed.")
                return@launch
            }

            val signatureBase64Url = keyStoreManager.encodeSignatureBase64Url(signature)
            signatureResult = signatureBase64Url
            Log.i(TAG, "Authentication successful, signature generated: ${signatureBase64Url.take(10)}...")
            _signingState.value = SigningState.SigningInProgress

            val userData = userDao.getUserData()
            if (userData == null) {
                Log.e(TAG, "Cannot complete transaction: User DID not found locally.")
                _signingState.value = SigningState.SigningFailed("Your DID not found locally.")
                return@launch
            }
            val senderDid = userData.userDid

            try {
                Log.d(TAG, "Calling signAndComplete API for Tx: ${transactionDto.id} by Sender: $senderDid")
                val request = SignTransactionRequestDto(Signature = signatureBase64Url, SenderDid = senderDid)
                val response = apiService.signAndCompleteTransaction(transactionDto.id, request)

                if (response.isSuccessful) {
                    Log.i(TAG, "Transaction successfully signed and completed on backend.")
                    _signingState.value = SigningState.SigningComplete
                    // update UI state ?
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown API error"
                    Log.e(TAG, "API Error completing transaction: ${response.code()} - $errorBody")
                    _signingState.value = SigningState.SigningFailed("API Error: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error completing transaction", e)
                _signingState.value = SigningState.SigningFailed("Network/Server Error: ${e.message}")
            }
        }
    }

    fun handleAuthenticationFailure(errorMessage: String) {
        _signingState.value = SigningState.SigningFailed(errorMessage)
    }


    private fun preparePayloadForSigning(payloadDto: TransactionPayloadDto): ByteArray {
        val canonicalPayloadMap = linkedMapOf<String, Any?>()
        canonicalPayloadMap["Id"] = payloadDto.id.toString()
        canonicalPayloadMap["Type"] = payloadDto.type
        canonicalPayloadMap["RecipientDid"] = payloadDto.recipientDid
        canonicalPayloadMap["Amount"] = payloadDto.amount.toBigDecimal().toPlainString()
        canonicalPayloadMap["Currency"] = payloadDto.currency
        canonicalPayloadMap["MetadataJson"] = payloadDto.metadataJson

        val gson = GsonBuilder()
            .disableHtmlEscaping()
            .create()

        val jsonPayloadString = gson.toJson(canonicalPayloadMap)
        Log.d(TAG, "Canonical JSON for signing: $jsonPayloadString")
        return jsonPayloadString.toByteArray(StandardCharsets.UTF_8)
    }
}