package com.example.service
 
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.worker.SyncWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received broadcast action on device boot/restart: $action")

        val validBootActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.REBOOT"
        )

        if (action in validBootActions) {
            try {
                // 1. Automatically launch the 24/7 Foreground Service
                MfsForegroundService.startService(context)
                Log.d(TAG, "MfsForegroundService successfully triggered from BootReceiver")

                // 2. Schedule periodic WorkManager for background retry & sync
                val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "SyncWorker",
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
                Log.d(TAG, "SyncWorker periodic work registered on boot")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting services on boot: ${e.message}", e)
            }
        }
    }
}
