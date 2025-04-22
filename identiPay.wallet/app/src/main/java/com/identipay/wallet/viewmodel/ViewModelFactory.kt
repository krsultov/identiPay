package com.identipay.wallet.viewmodel

import android.content.Context
import com.identipay.wallet.data.local.AppDatabase
import com.identipay.wallet.data.local.UserDao
import com.identipay.wallet.network.IdentiPayApiService
import com.identipay.wallet.network.RetrofitClient
import com.identipay.wallet.security.KeyStoreManager

class ViewModelFactory(
    private val applicationContext: Context,
    private val keyStoreManager: KeyStoreManager
) : androidx.lifecycle.ViewModelProvider.Factory {

    private val database by lazy { AppDatabase.getDatabase(applicationContext) }
    private val userDao by lazy { database.userDao() }

    private val apiService by lazy { RetrofitClient.instance }

    fun getApiService(): IdentiPayApiService = apiService
    fun getUserDao(): UserDao = userDao
    fun getKeyStoreManager(): KeyStoreManager = keyStoreManager

    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return when {
            // Handle OnboardingViewModel
            modelClass.isAssignableFrom(OnboardingViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                OnboardingViewModel(keyStoreManager, userDao, apiService) as T
            }
            // Handle DashboardViewModel
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> {
                @Suppress("UNCHECKED_CAST")
                DashboardViewModel(userDao) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}