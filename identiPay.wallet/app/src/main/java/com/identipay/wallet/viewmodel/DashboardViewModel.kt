package com.identipay.wallet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.identipay.wallet.data.local.UserDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log

class DashboardViewModel(
    private val userDao: UserDao
) : ViewModel() {

    companion object { private const val TAG = "DashboardViewModel" }

    private val _userDid = MutableStateFlow<String?>("Loading DID...")
    val userDid: StateFlow<String?> = _userDid.asStateFlow()

    init {
        loadUserData()
    }

    private fun loadUserData() {
        viewModelScope.launch {
            try {
                val userData = userDao.getUserData()
                if (userData != null) {
                    _userDid.value = userData.userDid
                    Log.i(TAG, "User DID loaded: ${userData.userDid}")
                } else {
                    _userDid.value = "Error: DID not found"
                    Log.e(TAG, "User data not found in local database.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user data from database", e)
                _userDid.value = "Error loading DID"
            }
        }
    }
}