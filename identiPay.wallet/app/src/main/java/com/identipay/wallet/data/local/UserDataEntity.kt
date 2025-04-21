package com.identipay.wallet.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_data")
data class UserDataEntity(
    @PrimaryKey val id: Int = 1,

    @ColumnInfo(name = "keystore_alias") val keystoreAlias: String,
    @ColumnInfo(name = "user_did") val userDid: String,
    @ColumnInfo(name = "onboarding_complete") val onboardingComplete: Boolean = true
)