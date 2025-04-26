package com.identipay.identipaypos.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.identipaypos.data.remote.IdentiPayPosApiService
import com.identipay.identipaypos.data.remote.RetrofitClient
import com.identipay.identipaypos.data.remote.CreateTransactionOfferRequestDto
import com.identipay.identipaypos.data.remote.TransactionTypeDto
import com.identipay.identipaypos.R
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.runtime.State
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.identipay.identipaypos.PrinterServiceAccessor
import com.identipay.identipaypos.data.remote.TransactionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.UUID
import androidx.core.graphics.scale
import com.identipay.identipaypos.nfc.ActiveTransactionHolder

data class PosScreenState(
    val amount: String = "",
    val currency: String = "EUR",
    val recipientDid: String = "did:identiPay:pos:merchant123",
    val transactionType: TransactionTypeDto = TransactionTypeDto.Payment,
    val metadata: String = "",
    val qrCodeBitmap: Bitmap? = null,
    val statusMessage: String = "Enter amount and generate QR",
    val isPolling: Boolean = false,
    val pollingTransactionId: UUID? = null,
    val finalTransactionStatus: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class PosViewModel(
    private val apiService: IdentiPayPosApiService = RetrofitClient.instance
) : ViewModel() {

    companion object {
        private const val TAG = "PosViewModel"
        private const val TARGET_LOGO_WIDTH_PX = 296
    }

    private val _uiState = mutableStateOf(PosScreenState())
    val uiState: State<PosScreenState> = _uiState

    private val _printRequestState = MutableStateFlow<TransactionDto?>(null)
    val printRequestState: StateFlow<TransactionDto?> = _printRequestState.asStateFlow()

    private var pollingJob: Job? = null

    fun consumePrintRequest() {
        _printRequestState.value = null
    }

    fun onAmountChange(newValue: String) {
        if (newValue.matches(Regex("^\\d*\\.?\\d*\$"))) {
            _uiState.value = _uiState.value.copy(amount = newValue, errorMessage = null)
        }
    }

    fun onCurrencyChange(newValue: String) {
        if (newValue.length <= 3) {
            _uiState.value =
                _uiState.value.copy(currency = newValue.uppercase(), errorMessage = null)
        }
    }

    fun generatePaymentOfferQr() {

        stopPolling()

        val amountDecimal = _uiState.value.amount.toDoubleOrNull()
        val currencyCode = _uiState.value.currency

        if (amountDecimal == null || amountDecimal <= 0) {
            _uiState.value = _uiState.value.copy(errorMessage = "Invalid amount")
            return
        }
        if (currencyCode.length != 3) {
            _uiState.value =
                _uiState.value.copy(errorMessage = "Invalid currency code (must be 3 letters)")
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null,
            statusMessage = "Generating offer...",
            qrCodeBitmap = null,
            isPolling = false,
            pollingTransactionId = null,
            finalTransactionStatus = null
        )

        viewModelScope.launch {
            try {
                val request = CreateTransactionOfferRequestDto(
                    recipientDid = _uiState.value.recipientDid,
                    transactionType = _uiState.value.transactionType,
                    amount = amountDecimal,
                    currency = currencyCode,
                    metadataJson = _uiState.value.metadata.ifEmpty { null }
                )

                Log.d(TAG, "Sending request: $request")
                val response = apiService.createTransactionOffer(request)

                if (response.isSuccessful) {
                    val transactionOffer = response.body()!!
                    val transactionId = transactionOffer.id.toString()
                    Log.i(TAG, "Offer created successfully. Transaction ID: $transactionId")

                    val qrBitmap = generateQrBitmap(transactionId)

                    if (qrBitmap != null) {
                        ActiveTransactionHolder.currentTransactionId = transactionId

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            qrCodeBitmap = qrBitmap,
                            statusMessage = "Scan QR Code to Pay (ID: ${transactionId.take(8)}...)",
                            pollingTransactionId = UUID.fromString(transactionId)
                        )

                        startPolling(UUID.fromString(transactionId))
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = "Failed to generate QR code",
                            statusMessage = "Error"
                        )
                    }

                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown API error"
                    Log.e(TAG, "API Error: ${response.code()} - $errorBody")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "API Error: ${response.code()}",
                        statusMessage = "Error"
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Network or other error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error: ${e.message}",
                    statusMessage = "Error"
                )
            }
        }
    }

    fun startPolling(transactionId: UUID) {
        pollingJob?.cancel()
        val pollingId = transactionId

        _uiState.value = _uiState.value.copy(
            isPolling = true,
            pollingTransactionId = pollingId,
            statusMessage = "Offer Created. Waiting for payment...",
            isLoading = false,
            finalTransactionStatus = null
        )
        Log.d(TAG, "Starting polling for transaction ID: $pollingId")

        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            var currentStatus = "Pending"
            while (isActive && currentStatus.equals("Pending", ignoreCase = true)) {
                try {
                    Log.d(TAG, "Polling status for $pollingId...")
                    val response = apiService.getTransactionById(pollingId)

                    if (response.isSuccessful && response.body() != null) {
                        currentStatus = response.body()!!.status
                        Log.d(TAG, "Fetched status for $pollingId: $currentStatus")
                        if (!currentStatus.equals("Pending", ignoreCase = true)) {
                            _uiState.value = _uiState.value.copy(
                                isPolling = false,
                                finalTransactionStatus = currentStatus,
                                statusMessage = "Transaction $currentStatus!"
                            )
                            Log.i(
                                TAG,
                                "Polling stopped. Final status for $pollingId: $currentStatus"
                            )
                            if (currentStatus.equals("Completed", ignoreCase = true)) {
                                _printRequestState.value = response.body()
                            }
                        } else {
                            _uiState.value =
                                _uiState.value.copy(statusMessage = "Waiting for payment...")
                        }
                    } else {
                        Log.w(TAG, "Polling API call failed for $pollingId: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during polling for $pollingId", e)
                }

                if (isActive && currentStatus.equals("Pending", ignoreCase = true)) {
                    delay(1000)
                }
            }

            if (!isActive) {
                Log.d(TAG, "Polling cancelled for $pollingId.")
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        ActiveTransactionHolder.currentTransactionId = null
        _uiState.value = _uiState.value.copy(isPolling = false, pollingTransactionId = null)
        Log.d(TAG, "Polling manually stopped.")
    }

    fun executePrintReceipt(
        context: Context,
        printerAccessor: PrinterServiceAccessor?,
        transaction: TransactionDto?
    ) {
        if (printerAccessor == null) {
            Log.e(TAG, "Print failed: Printer Accessor is null.")
            _uiState.value = _uiState.value.copy(errorMessage = "Printer accessor unavailable")
            return
        }
        if (!printerAccessor.isBound()) {
            Log.e(TAG, "Print failed: Printer Service not bound.")
            _uiState.value = _uiState.value.copy(errorMessage = "Printer is not ready")
            printerAccessor.attemptBind()
            return
        }
        val service = printerAccessor.getPrinterService()
        if (service == null) {
            Log.e(TAG, "Print failed: Printer Service interface is null despite being bound.")
            _uiState.value = _uiState.value.copy(errorMessage = "Printer communication error")
            return
        }
        if (transaction?.payload == null) {
            Log.e(TAG, "Print failed: Transaction data missing.")
            _uiState.value =
                _uiState.value.copy(errorMessage = "Cannot print: Missing transaction data")
            return
        }

        Log.i(TAG, "Executing print receipt for transaction ${transaction.id}")
        viewModelScope.launch(Dispatchers.IO) {

            var scaledLogoBitmap: Bitmap? = null

            try {
                val originalLogoBitmap: Bitmap? = try {
                    BitmapFactory.decodeResource(context.resources, R.drawable.identipay_logo)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load logo bitmap resource", e)
                    null
                }

                if (originalLogoBitmap != null) {
                    try {
                        val originalWidth = originalLogoBitmap.width
                        val originalHeight = originalLogoBitmap.height
                        Log.d(TAG, "Original logo size: ${originalWidth}x${originalHeight}")

                        if (originalWidth > TARGET_LOGO_WIDTH_PX) {
                            val scaleFactor =
                                TARGET_LOGO_WIDTH_PX.toFloat() / originalWidth.toFloat()
                            val targetHeight = (originalHeight * scaleFactor).toInt()

                            if (targetHeight > 0) {
                                Log.d(
                                    TAG,
                                    "Scaling logo to: ${TARGET_LOGO_WIDTH_PX}x${targetHeight}"
                                )
                                scaledLogoBitmap =
                                    originalLogoBitmap.scale(TARGET_LOGO_WIDTH_PX, targetHeight, false)
                            } else {
                                Log.w(
                                    TAG,
                                    "Calculated target height is zero or less, using original logo."
                                )
                                scaledLogoBitmap =
                                    originalLogoBitmap
                            }
                        } else {
                            Log.d(TAG, "Logo is already smaller than target width, using original.")
                            scaledLogoBitmap = originalLogoBitmap
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to scale logo bitmap", e)
                        scaledLogoBitmap = null
                    }
                }

                service.apply {
                    val amountString = transaction.payload.amount.toBigDecimal().toPlainString()

                    setAlignment(1)
                    scaledLogoBitmap?.let { printBitmap(it); nextLine(2) }
                    setTextSize(24f)
                    printText(transaction.payload.recipientDid)
                    nextLine(1)

                    val currency = transaction.payload.currency
                    val lineItemText = arrayOf(
                        transaction.payload.type,
                        "$amountString $currency"
                    )
                    val lineItemWeights = intArrayOf(2, 2)
                    val lineItemAligns = intArrayOf(0, 2)
                    printTableText(lineItemText, lineItemWeights, lineItemAligns)
                    printText("--------------------------------")
                    nextLine(1)

                    val detailsText = arrayOf(
                        "Tx ID:", transaction.id.toString(),
                        "SDR:", transaction.senderDid,
                        "RCP:", transaction.payload.recipientDid,
                        "Status:", transaction.status,
                        "Date:",transaction.createdAt
                    )
                    val detailsWeights = intArrayOf(1, 3)
                    val detailsAligns = intArrayOf(0, 0)
                    setTextSize(18f)
                    for (i in detailsText.indices step 2) {
                        if (i + 1 < detailsText.size) {
                            printTableText(arrayOf(detailsText[i], detailsText[i+1]), detailsWeights, detailsAligns)
                        } else {
                            printTableText(arrayOf(detailsText[i], ""), detailsWeights, detailsAligns)
                        }
                    }

                    setTextSize(24f)
                    printText("--------------------------------")
                    nextLine(1)

                    setAlignment(1)
                    setTextBold(true)
                    setTextSize(30f)
                    printTableText(arrayOf("Total", "$amountString $currency"), intArrayOf(2, 2), intArrayOf(0, 0))
                    nextLine(1)
                    setTextSize(24f)
                    setTextBold(false)

                    setAlignment(1)
                    printQRCode(transaction.id.toString(), 5, 1)
                    nextLine(3)
                }
                Log.i(TAG, "Print commands sent successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during printing", e)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(errorMessage = "Printing failed")
                }
            }
        }
    }

    private fun generateQrBitmap(text: String): Bitmap? {
        return try {
            val writer = MultiFormatWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 500, 500)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp[x, y] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            bmp
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate QR Bitmap", e)
            null
        }
    }
}