package com.identipay.wallet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class TransactionConfirmViewModelFactory(
    private val transactionId: String,
    private val baseFactory: ViewModelProvider.Factory
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionConfirmViewModel::class.java)) {
            val mainFactory = baseFactory as ViewModelFactory
            @Suppress("UNCHECKED_CAST")
            return TransactionConfirmViewModel(
                transactionIdString = transactionId,
                apiService = baseFactory.apiService,
                userDao = baseFactory.userDao,
                keyStoreManager = mainFactory.getKeyStoreManager()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}