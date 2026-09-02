package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payment_transactions")
data class PaymentTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mfsName: String,
    val amount: String,
    val senderNumber: String,
    val trxId: String,
    val timestamp: Long,
    val body: String,
    val isSynced: Boolean = false,
    val deviceId: String? = null,
    val errorLog: String? = null,
    val currentBalance: String? = null
)
