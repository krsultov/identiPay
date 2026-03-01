package com.identipay.wallet.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.identipay.wallet.data.db.dao.NoteDao
import com.identipay.wallet.data.db.dao.StealthAddressDao
import com.identipay.wallet.data.db.dao.TransactionDao
import com.identipay.wallet.data.db.entity.NoteEntity
import com.identipay.wallet.data.db.entity.StealthAddressEntity
import com.identipay.wallet.data.db.entity.TransactionEntity

@Database(
    entities = [
        StealthAddressEntity::class,
        TransactionEntity::class,
        NoteEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stealthAddressDao(): StealthAddressDao
    abstract fun transactionDao(): TransactionDao
    abstract fun noteDao(): NoteDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pool_notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `noteCommitment` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `ownerKey` TEXT NOT NULL,
                        `salt` TEXT NOT NULL,
                        `leafIndex` INTEGER NOT NULL,
                        `isSpent` INTEGER NOT NULL DEFAULT 0,
                        `nullifier` TEXT,
                        `depositTxDigest` TEXT NOT NULL,
                        `withdrawTxDigest` TEXT,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN buyerStealthAddress TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN merchantPublicKey TEXT")
            }
        }
    }
}
