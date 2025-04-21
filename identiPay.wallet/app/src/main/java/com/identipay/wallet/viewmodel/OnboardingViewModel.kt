package com.identipay.wallet.viewmodel

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.wallet.data.local.UserDao
import com.identipay.wallet.data.local.UserDataEntity
import com.identipay.wallet.network.CreateUserRequestDto
import com.identipay.wallet.network.CreateUserResponseDto
import com.identipay.wallet.network.IdentiPayApiService
import com.identipay.wallet.security.KeyStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

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
    private val keyStoreManager: KeyStoreManager,
    private val userDao: UserDao,
    private val apiService: IdentiPayApiService
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

    fun registerUser(publicKeyBase64: String) {
        viewModelScope.launch {
            _onboardingState.value = OnboardingState.RegistrationInProgress
            Log.d("OnboardingViewModel", "Attempting registration with pubKey: ${publicKeyBase64.take(10)}...")

            try {
                val request = CreateUserRequestDto(PrimaryKey = publicKeyBase64)
                val response = apiService.createUser(request)

                if (response.isSuccessful) {
                    val createUserRequest : CreateUserResponseDto? = response.body()
                    if (createUserRequest != null) {
                        val userDid = createUserRequest.userDid
                        Log.i("OnboardingViewModel", "Registration successful. Received User ID: ${createUserRequest.userId}, DID: $userDid")

                        val userData = UserDataEntity(
                            keystoreAlias = keyAlias,
                            userDid = userDid,
                            onboardingComplete = true
                        )
                        userDao.insertOrUpdateUserData(userData)
                        Log.d("OnboardingViewModel", "User data saved to Room DB.")

                        _onboardingState.value = OnboardingState.RegistrationComplete(userDid)
                    } else {
                        Log.e("OnboardingViewModel", "Registration API succeeded but response body was null.")
                        _onboardingState.value = OnboardingState.RegistrationFailed
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e("OnboardingViewModel", "Registration API failed: ${response.code()} - $errorBody")
                    _onboardingState.value = OnboardingState.RegistrationFailed
                }
            } catch (e: IOException) {
                Log.e("OnboardingViewModel", "Network exception during registration", e)
                _onboardingState.value = OnboardingState.RegistrationFailed
            }
            catch (e: Exception) {
                Log.e("OnboardingViewModel", "Generic exception during registration", e)
                _onboardingState.value = OnboardingState.RegistrationFailed
            }

        }
    }

    fun resetState() {
        _onboardingState.value = OnboardingState.Idle
    }
}