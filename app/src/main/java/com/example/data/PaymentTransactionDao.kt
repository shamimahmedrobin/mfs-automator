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

    @Query("SELECT * FROM payment_transactions WHERE trxId = :trxId LIMIT 1")
    suspend fun getTransactionByTrxId(trxId: String): PaymentTransaction?

    /**
     * Latest transaction for a provider that carried a balance figure, before a
     * given timestamp. Used by BalanceCheck to verify the new SMS's balance is
     * consistent with the previous balance + received amount.
     */
    @Query("SELECT * FROM payment_transactions WHERE mfsName = :provider AND currentBalance IS NOT NULL AND timestamp < :beforeTimestamp ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestBalanceBefore(provider: String, beforeTimestamp: Long): PaymentTransaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: PaymentTransaction): Long
    
    @Query("UPDATE payment_transactions SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: Int)

    @Query("DELETE FROM payment_transactions")
    suspend fun clearAllTransactions()
}
