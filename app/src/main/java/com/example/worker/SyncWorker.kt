package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.SettingsRepository
import com.example.network.WebhookClient
import kotlinx.coroutines.flow.first

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "Starting background sync for failed transactions...")
        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.paymentTransactionDao()
        val settings = SettingsRepository(applicationContext)

        try {
            // Wait for dependencies (in real app we might inject these)
            val url = settings.webhookUrl.first()
            val token = settings.apiToken.first()
            val deviceId = settings.deviceId.first()
            val customHeader = settings.customHeader.first()

            val unsyncedTransactions = dao.getAllUnsyncedTransactions()

            if (unsyncedTransactions.isEmpty()) {
                Log.d("SyncWorker", "No unsynced transactions found.")
                return Result.success()
            }

            for (transaction in unsyncedTransactions) {
                val (isSuccess, errorMessage) = WebhookClient.postTransaction(
                    transaction, url, token, deviceId, customHeader
                )
                if (isSuccess) {
                    dao.insertTransaction(transaction.copy(isSynced = true, errorLog = null))
                    Log.d("SyncWorker", "Successfully synced transaction: ${transaction.trxId}")
                } else {
                    dao.insertTransaction(transaction.copy(errorLog = errorMessage))
                    Log.d("SyncWorker", "Failed to sync transaction: ${transaction.trxId} - $errorMessage")
                    // If one fails, we can retry the whole batch later
                    return Result.retry()
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error during sync", e)
            return Result.retry()
        }
    }
}
