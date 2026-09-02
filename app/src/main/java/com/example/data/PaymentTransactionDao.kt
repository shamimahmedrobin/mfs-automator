package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentTransactionDao {
    @Query("SELECT * FROM payment_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<PaymentTransaction>>

    @Query("SELECT * FROM payment_transactions WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getAllUnsyncedTransactions(): List<PaymentTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: PaymentTransaction)
    
    @Query("UPDATE payment_transactions SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Int)

    @Query("DELETE FROM payment_transactions")
    suspend fun clearAllTransactions()
}
