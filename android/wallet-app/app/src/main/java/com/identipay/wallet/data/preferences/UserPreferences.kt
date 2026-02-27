package com.identipay.wallet.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        val REGISTERED_NAME = stringPreferencesKey("registered_name")
        val IS_ONBOARDED = booleanPreferencesKey("is_onboarded")
        val HAS_SCANNED_PASSPORT = booleanPreferencesKey("has_scanned_passport")
        val USER_SALT = stringPreferencesKey("user_salt")
        val RECEIVE_COUNTER = intPreferencesKey("receive_counter")
    }

    val registeredName: Flow<String?> = dataStore.data.map { it[REGISTERED_NAME] }

    val isOnboarded: Flow<Boolean> = dataStore.data.map { it[IS_ONBOARDED] ?: false }

    val hasScannedPassport: Flow<Boolean> = dataStore.data.map { it[HAS_SCANNED_PASSPORT] ?: false }

    val userSalt: Flow<String?> = dataStore.data.map { it[USER_SALT] }

    suspend fun setRegisteredName(name: String) {
        dataStore.edit { it[REGISTERED_NAME] = name }
    }

    suspend fun setOnboarded(onboarded: Boolean) {
        dataStore.edit { it[IS_ONBOARDED] = onboarded }
    }

    suspend fun setHasScannedPassport(scanned: Boolean) {
        dataStore.edit { it[HAS_SCANNED_PASSPORT] = scanned }
    }

    suspend fun setUserSalt(salt: String) {
        dataStore.edit { it[USER_SALT] = salt }
    }

    /** Highest receive counter ever used (persisted so addresses can be re-derived). */
    val receiveCounter: Flow<Int> = dataStore.data.map { it[RECEIVE_COUNTER] ?: 0 }

    suspend fun getReceiveCounterOnce(): Int {
        return dataStore.data.map { it[RECEIVE_COUNTER] ?: 0 }.first()
    }

    suspend fun setReceiveCounter(counter: Int) {
        dataStore.edit { it[RECEIVE_COUNTER] = counter }
    }
}
