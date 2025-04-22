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
import java.math.RoundingMode
import java.security.PrivateKey
import java.security.Signature
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

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

    private var dataToSign: ByteArray? = null

    var signatureResult: String? = null
        private set

    private val keyAlias = "identipay_user_main_key"

    init {
        loadTransactionDetails()
    }

    fun loadTransactionDetails() {
        viewModelScope.launch {
            _uiState.value = TransactionConfirmState.Loading
            _signingState.value = SigningState.Idle
            dataToSign = null
            signatureResult = null
            try {
                val transactionId = UUID.fromString(transactionIdString)
                Log.d(TAG, "Fetching transaction details for ID: $transactionId")
                val response = apiService.getTransactionById(transactionId)

                if (response.isSuccessful && response.body() != null) {
                    val transactionDto = response.body()!!
                    Log.d(TAG, "Transaction details loaded: ${transactionDto.id}, Status: ${transactionDto.status}")
                    if (transactionDto.payload == null) {
                        Log.e(TAG, "Transaction payload is null in response.")
                        _uiState.value = TransactionConfirmState.Error("Payload data missing in transaction details.")
                    } else if (transactionDto.status.equals("Pending", ignoreCase = true)) {
                        _uiState.value = TransactionConfirmState.OfferLoaded(transactionDto)
                    } else {
                        Log.w(TAG, "Transaction status is not Pending: ${transactionDto.status}")
                        _uiState.value = TransactionConfirmState.Error("Transaction is not pending (Status: ${transactionDto.status})")
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

    fun prepareSignatureData(transactionDto: TransactionDto): Boolean {
        _signingState.value = SigningState.AwaitingAuthentication
        return try {
            if (transactionDto.payload == null) {
                throw IllegalStateException("Payload is null, cannot prepare data for signing.")
            }
            dataToSign = preparePayloadForSigningInternal(transactionDto.payload)
            dataToSign != null
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing payload for signing", e)
            _signingState.value = SigningState.SigningFailed("Payload prep error: ${e.message}")
            dataToSign = null
            false
        }
    }


    fun getInitializedSignatureForSigning(): Signature? {
        if(signingState.value !is SigningState.AwaitingAuthentication) {
            Log.e(TAG,"Attempted to get signature object in wrong state: ${signingState.value}")
            return null
        }
        if(dataToSign == null){
            Log.e(TAG,"Attempted to get signature object but dataToSign is null.")
            _signingState.value = SigningState.SigningFailed("Internal error: Missing data.")
            return null
        }

        val privateKey: PrivateKey? = try {
            keyStoreManager.getPrivateKeyEntry(keyAlias)?.privateKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get private key for signing init", e)
            _signingState.value = SigningState.SigningFailed("Error getting private key.")
            return null
        }

        if (privateKey == null) {
            Log.e(TAG, "Private key is null for alias '$keyAlias'")
            _signingState.value = SigningState.SigningFailed("Private key not found.")
            return null
        }

        return try {
            Signature.getInstance(KeyStoreManager.SIGNATURE_ALGORITHM).apply {
                initSign(privateKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Signature object", e)
            _signingState.value = SigningState.SigningFailed("Crypto init error.")
            return null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun handleAuthenticationSuccess(authenticatedSignature: Signature?, transactionFromState: TransactionDto?) {
        viewModelScope.launch {
            if (transactionFromState == null) {
                Log.e(TAG, "Cannot complete transaction: Transaction data missing after authentication.")
                _signingState.value = SigningState.SigningFailed("Transaction data unavailable.")
                return@launch
            }
            if (authenticatedSignature == null) {
                Log.e(TAG, "Authenticated signature object was null in callback.")
                _signingState.value = SigningState.SigningFailed("Authentication succeeded but signature object invalid.")
                return@launch
            }

            val dataBytes = dataToSign
            if (dataBytes == null) {
                Log.e(TAG, "Data to sign was null after authentication.")
                _signingState.value = SigningState.SigningFailed("Internal error: Missing data after auth.")
                return@launch
            }

            val signatureBytes: ByteArray? = try {
                authenticatedSignature.update(dataBytes)
                authenticatedSignature.sign()
            } catch (e: Exception) {
                Log.e(TAG, "Signing failed AFTER authentication", e)
                _signingState.value = SigningState.SigningFailed("Signing failed after authentication.")
                null
            } finally {
                dataToSign = null
            }

            if (signatureBytes == null) {
                return@launch
            }

            val signatureBase64Url = keyStoreManager.encodeSignatureBase64Url(signatureBytes)
            signatureResult = signatureBase64Url
            Log.i(TAG, "Signature generated: ${signatureBase64Url.take(10)}...")
            _signingState.value = SigningState.SigningInProgress

            val userData = userDao.getUserData()
            if (userData == null) {
                Log.e(TAG, "Cannot complete transaction: User DID not found locally.")
                _signingState.value = SigningState.SigningFailed("Your DID not found locally.")
                return@launch
            }
            val senderDid = userData.userDid

            try {
                Log.d(TAG, "Calling signAndComplete API for Tx: ${transactionFromState.id} by Sender: $senderDid")
                val request = SignTransactionRequestDto(Signature = signatureBase64Url, SenderDid = senderDid)
                val response = apiService.signAndCompleteTransaction(transactionFromState.id, request)

                if (response.isSuccessful && response.body() != null) {
                    Log.i(TAG, "Transaction successfully signed and completed on backend.")
                    _signingState.value = SigningState.SigningComplete
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown API error"
                    Log.e(TAG, "API Error completing transaction: ${response.code()} - $errorBody")
                    _signingState.value = SigningState.SigningFailed("API Error: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calling complete transaction API", e)
                _signingState.value = SigningState.SigningFailed("Network/Server Error: ${e.message}")
            }
        }
    }

    fun handleAuthenticationFailure(errorMessage: String) {
        if (_signingState.value == SigningState.AwaitingAuthentication) {
            _signingState.value = SigningState.SigningFailed(errorMessage)
        }
        dataToSign = null
    }

    private fun preparePayloadForSigningInternal(payloadDto: TransactionPayloadDto): ByteArray {
        val symbols = DecimalFormatSymbols(Locale.US)

        val decimalFormat = DecimalFormat("0.00000000", symbols)
        decimalFormat.roundingMode = RoundingMode.UNNECESSARY

        val canonicalPayloadMap = linkedMapOf<String, Any?>()
        canonicalPayloadMap["Id"] = payloadDto.id.toString()
        canonicalPayloadMap["Type"] = payloadDto.type
        canonicalPayloadMap["RecipientDid"] = payloadDto.recipientDid
        canonicalPayloadMap["Amount"] = decimalFormat.format(payloadDto.amount.toBigDecimal())
        canonicalPayloadMap["Currency"] = payloadDto.currency
        //canonicalPayloadMap["MetadataJson"] = payloadDto.metadataJson

        val gson = GsonBuilder().disableHtmlEscaping().create()
        val jsonPayloadString = gson.toJson(canonicalPayloadMap)
        Log.d(TAG, "Canonical JSON for signing: $jsonPayloadString")
        return jsonPayloadString.toByteArray(StandardCharsets.UTF_8)
    }
}