package com.example.sms

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.PaymentTransaction
import com.example.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object SmsInboxScanner {
    private const val TAG = "SmsInboxScanner"

    suspend fun scanRecentSms(context: Context, hoursBack: Int = 48): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        try {
            val resolver = context.contentResolver
            val uri = Uri.parse("content://sms/inbox")
            val sinceTime = System.currentTimeMillis() - (hoursBack * 60 * 60 * 1000L)
            val projection = arrayOf("_id", "address", "body", "date")
            val selection = "date >= ?"
            val selectionArgs = arrayOf(sinceTime.toString())
            val sortOrder = "date DESC"

            val cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor?.use {
                val addressIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")

                val db = AppDatabase.getDatabase(context)
                val dao = db.paymentTransactionDao()
                val settings = SettingsRepository(context)

                val isBkashEnabled = settings.bkashEnabled.first()
                val isNagadEnabled = settings.nagadEnabled.first()
                val isRocketEnabled = settings.rocketEnabled.first()
                val isUpayEnabled = settings.upayEnabled.first()
                val minAmountSetting = settings.minAmount.first().toDoubleOrNull() ?: 0.0
                val deviceId = settings.deviceId.first()

                while (it.moveToNext()) {
                    val address = it.getString(addressIdx) ?: ""
                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)

                    val parsedData = SmsParser.parseMessage(address, body) ?: continue

                    val isEnabled = when (parsedData.mfsName.lowercase()) {
                        "bkash" -> isBkashEnabled
                        "nagad" -> isNagadEnabled
                        "rocket" -> isRocketEnabled
                        "upay" -> isUpayEnabled
                        else -> false
                    }
                    val amountDouble = parsedData.amount.replace(",", "").toDoubleOrNull() ?: 0.0
                    if (!isEnabled || amountDouble < minAmountSetting) continue

                    // Check if already in DB
                    val existing = dao.getTransactionByTrxId(parsedData.trxId)
                    if (existing == null) {
                        val transaction = PaymentTransaction(
                            mfsName = parsedData.mfsName,
                            amount = parsedData.amount,
                            senderNumber = parsedData.senderNumber,
                            trxId = parsedData.trxId,
                            timestamp = date,
                            body = body,
                            currentBalance = parsedData.currentBalance,
                            deviceId = deviceId,
                            isSynced = false,
                            errorLog = "Pending sync"
                        )
                        dao.insertTransaction(transaction)
                        importedCount++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning inbox SMS", e)
        }
        importedCount
    }
}
