package com.fintrack.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access contract for reading and writing SMS-derived transactions.
 */
@Dao
interface TransactionDao {
    /**
     * Streams all stored transactions ordered from newest to oldest.
     */
    @Query("SELECT * FROM sms_transactions ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<SmsTransactionEntity>>

    /**
     * Inserts parsed transactions while ignoring duplicates.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(transactions: List<SmsTransactionEntity>)
}
