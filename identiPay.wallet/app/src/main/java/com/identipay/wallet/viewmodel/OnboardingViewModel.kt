package com.identipay.wallet.viewmodel

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.wallet.security.KeyStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class OnboardingState {
    data object Idle : OnboardingState()
    data object KeyGenInProgress : OnboardingState()
    data class KeyGenComplete(val publicKey: String?) : OnboardingState()
    data object KeyGenFailed : OnboardingState()
    data object RegistrationInProgress : OnboardingState()
    data class RegistrationComplete(val userDid: String) : OnboardingState()
    data object RegistrationFailed : OnboardingState()
}

class OnboardingViewModel(
    private val keyStoreManager: KeyStoreManager
) : ViewModel() {

    private val keyAlias = "identipay_user_main_key"

    private val _onboardingState = MutableStateFlow<OnboardingState>(OnboardingState.Idle)
    val onboardingState: StateFlow<OnboardingState> = _onboardingState.asStateFlow()

    @RequiresApi(Build.VERSION_CODES.O)
    fun generateKeys() {
        viewModelScope.launch {
            _onboardingState.value = OnboardingState.KeyGenInProgress
            val generated = keyStoreManager.generateKeyPairIfNotExists(keyAlias)
            if (generated) {
                val publicKey = keyStoreManager.getPublicKeyBase64(keyAlias)
                if (publicKey != null) {
                    _onboardingState.value = OnboardingState.KeyGenComplete(publicKey)
                } else {
                    _onboardingState.value = OnboardingState.KeyGenFailed
                }
            } else {
                val publicKey = keyStoreManager.getPublicKeyBase64(keyAlias)
                if (keyStoreManager.keyExists(keyAlias) && publicKey != null) {
                    _onboardingState.value =
                        OnboardingState.KeyGenComplete(publicKey)
                } else {
                    _onboardingState.value = OnboardingState.KeyGenFailed
                }
            }
        }
    }

    fun registerUser(publicKey: String) {
        viewModelScope.launch {
            _onboardingState.value = OnboardingState.RegistrationInProgress
            try {
                val fakeDid =
                    "did:identiPay:api.example.com:${java.util.UUID.randomUUID()}"
                kotlinx.coroutines.delay(1500)

                _onboardingState.value = OnboardingState.RegistrationComplete(fakeDid)
            } catch (e: Exception) {
                _onboardingState.value = OnboardingState.RegistrationFailed
            }

        }
    }

    fun resetState() {
        _onboardingState.value = OnboardingState.Idle
    }
}