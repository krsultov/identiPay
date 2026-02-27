package com.identipay.wallet.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.identipay.wallet.data.db.dao.StealthAddressDao
import com.identipay.wallet.data.db.dao.TransactionDao
import com.identipay.wallet.data.db.entity.StealthAddressEntity
import com.identipay.wallet.data.db.entity.TransactionEntity

@Database(
    entities = [
        StealthAddressEntity::class,
        TransactionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stealthAddressDao(): StealthAddressDao
    abstract fun transactionDao(): TransactionDao
}
