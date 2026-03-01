package com.identipay.wallet.ui.checkout

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.wallet.crypto.PoseidonHash
import com.identipay.wallet.data.preferences.UserPreferences
import com.identipay.wallet.data.repository.BalanceRepository
import com.identipay.wallet.data.repository.CommerceRepository
import com.identipay.wallet.data.repository.CommerceResult
import com.identipay.wallet.network.CommerceProposal
import com.identipay.wallet.network.TransactionStatus
import com.identipay.wallet.network.WebSocketManager
import com.identipay.wallet.zk.AgeCheckInput
import com.identipay.wallet.zk.ProofGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.util.Calendar
import javax.inject.Inject

enum class CheckoutStep {
    IDLE, SIGNING, PROVING_AGE, ENCRYPTING, SETTLING, SUCCESS, ERROR, AGE_FAILED
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
    private val proofGenerator: ProofGenerator,
    private val userPreferences: UserPreferences,
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
                var zkProofBytes: ByteArray? = null
                var zkPublicInputsBytes: ByteArray? = null

                if (hasAgeGate) {
                    _uiState.update { it.copy(step = CheckoutStep.PROVING_AGE) }

                    val birthYear = userPreferences.getBirthYearOnce()
                        ?: throw IllegalStateException("Birth year not stored")
                    val birthMonth = userPreferences.getBirthMonthOnce()
                        ?: throw IllegalStateException("Birth month not stored")
                    val birthDay = userPreferences.getBirthDayOnce()
                        ?: throw IllegalStateException("Birth day not stored")
                    val userSaltStr = userPreferences.getUserSaltOnce()
                        ?: throw IllegalStateException("User salt not stored")
                    val identityCommitmentStr = userPreferences.getIdentityCommitmentOnce()
                        ?: throw IllegalStateException("Identity commitment not stored")

                    val userSalt = BigInteger(userSaltStr)
                    val identityCommitment = BigInteger(identityCommitmentStr)

                    // Compute referenceDate as YYYYMMDD integer
                    val cal = Calendar.getInstance()
                    val referenceDate = cal.get(Calendar.YEAR) * 10000 +
                        (cal.get(Calendar.MONTH) + 1) * 100 +
                        cal.get(Calendar.DAY_OF_MONTH)

                    val ageThreshold = proposal.constraints!!.ageGate!!

                    // Compute dobHash = Poseidon(birthYear, birthMonth, birthDay)
                    val dobHash = PoseidonHash.hash3(
                        BigInteger.valueOf(birthYear.toLong()),
                        BigInteger.valueOf(birthMonth.toLong()),
                        BigInteger.valueOf(birthDay.toLong()),
                    )

                    // Parse intentHash hex to BigInteger
                    val intentHashHex = proposal.intentHash.removePrefix("0x")
                    val intentHash = BigInteger(intentHashHex, 16).mod(PoseidonHash.FIELD_PRIME)

                    val ageCheckInput = AgeCheckInput.create(
                        birthYear = birthYear,
                        birthMonth = birthMonth,
                        birthDay = birthDay,
                        dobHash = dobHash,
                        userSalt = userSalt,
                        ageThreshold = ageThreshold,
                        referenceDate = referenceDate,
                        identityCommitment = identityCommitment,
                        intentHash = intentHash,
                    )

                    val proofResult = proofGenerator.generateAgeProof(ageCheckInput)
                    zkProofBytes = proofResult.proofBytes
                    zkPublicInputsBytes = proofResult.publicInputsBytes
                }

                _uiState.update { it.copy(step = CheckoutStep.ENCRYPTING) }

                _uiState.update { it.copy(step = CheckoutStep.SETTLING) }

                val result = commerceRepository.executeCheckout(
                    proposal,
                    zkProof = zkProofBytes,
                    zkPublicInputs = zkPublicInputsBytes,
                )

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
                val msg = e.message ?: ""
                val isAgeFail = _uiState.value.step == CheckoutStep.PROVING_AGE ||
                    msg.contains("age", ignoreCase = true) ||
                    msg.contains("Proof generation failed", ignoreCase = true)
                if (isAgeFail) {
                    val ageRequired = _uiState.value.proposal?.constraints?.ageGate ?: 18
                    _uiState.update {
                        it.copy(
                            step = CheckoutStep.AGE_FAILED,
                            error = "You must be $ageRequired or older to complete this purchase.",
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            step = CheckoutStep.ERROR,
                            error = e.message ?: "Checkout failed",
                        )
                    }
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
