package com.identipay.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.identipay.wallet.data.db.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM pool_notes WHERE isSpent = 0 ORDER BY createdAt DESC")
    suspend fun getUnspent(): List<NoteEntity>

    @Query("SELECT * FROM pool_notes ORDER BY createdAt DESC")
    fun getAll(): Flow<List<NoteEntity>>

    @Query("SELECT SUM(amount) FROM pool_notes WHERE isSpent = 0")
    suspend fun getTotalUnspent(): Long?

    @Query("UPDATE pool_notes SET isSpent = 1, nullifier = :nullifier, withdrawTxDigest = :txDigest WHERE id = :noteId")
    suspend fun markSpent(noteId: Long, nullifier: String, txDigest: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Query("SELECT * FROM pool_notes WHERE id IN (:ids) AND isSpent = 0")
    suspend fun getByIds(ids: List<Long>): List<NoteEntity>
}
