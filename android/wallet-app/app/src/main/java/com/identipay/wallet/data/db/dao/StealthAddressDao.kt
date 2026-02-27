package com.identipay.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.identipay.wallet.data.db.entity.StealthAddressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StealthAddressDao {

    @Query("SELECT * FROM stealth_addresses ORDER BY createdAt DESC")
    fun getAll(): Flow<List<StealthAddressEntity>>

    @Query("SELECT * FROM stealth_addresses ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<StealthAddressEntity>

    @Query("SELECT * FROM stealth_addresses WHERE stealthAddress = :address")
    suspend fun getByAddress(address: String): StealthAddressEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: StealthAddressEntity)

    @Query("UPDATE stealth_addresses SET balanceUsdc = :balance WHERE stealthAddress = :address")
    suspend fun updateBalance(address: String, balance: Long)

    @Query("SELECT SUM(balanceUsdc) FROM stealth_addresses")
    fun getTotalBalance(): Flow<Long?>

    @Query("SELECT * FROM stealth_addresses WHERE balanceUsdc > 0 ORDER BY balanceUsdc DESC")
    suspend fun getWithBalance(): List<StealthAddressEntity>

    @Query("DELETE FROM stealth_addresses WHERE stealthAddress = :address")
    suspend fun deleteByAddress(address: String)
}
