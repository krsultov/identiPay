package com.identipay.wallet.data.local

import androidx.room.*

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUserData(userData: UserDataEntity)

    @Query("SELECT * FROM user_data WHERE id = 1")
    suspend fun getUserData(): UserDataEntity?

    @Query("DELETE FROM user_data")
    suspend fun clearUserData()
}