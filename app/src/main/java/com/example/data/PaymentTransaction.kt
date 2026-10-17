package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "payment_transactions",
    // TrxIDs are normalized (uppercase, alphanumeric) in SmsParser before insert,
    // so the unique index is the hard dedupe guard against double-posting.
    indices = [Index(value = ["trxId"], unique = true)]
)
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
    val currentBalance: String? = null,
    // Fraud flag from the balance cross-check (see BalanceCheck): the record is
    // still forwarded to the server — the server decides how to treat it.
    val isSuspicious: Boolean = false,
    val suspicionReason: String? = null,
    // "in" = money received, "out" = withdrawn/spent. Outgoing legs keep the
    // balance chain continuous; the server must NOT match them to orders.
    val direction: String = "in",
    val fee: String? = null
)
