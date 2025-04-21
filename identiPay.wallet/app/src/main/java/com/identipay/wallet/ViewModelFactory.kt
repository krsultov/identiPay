package com.identipay.wallet

import android.content.Context
import com.identipay.wallet.data.local.AppDatabase
import com.identipay.wallet.network.RetrofitClient
import com.identipay.wallet.security.KeyStoreManager
import com.identipay.wallet.viewmodel.OnboardingViewModel

class ViewModelFactory(
    private val applicationContext: Context,
    private val keyStoreManager: KeyStoreManager
) : androidx.lifecycle.ViewModelProvider.Factory {

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val userDao by lazy { database.userDao() }

    private val apiService by lazy { RetrofitClient.instance }

    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(keyStoreManager, userDao, apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}