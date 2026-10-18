package com.example.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val TAG = "SmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        Log.d(TAG, "Incoming SMS broadcast received!")
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // 1. Acquire WakeLock so device doesn't sleep while processing
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MfsReader:SmsReceiverWakeLock"
        )
        wakeLock?.acquire(15000L) // Max 15 seconds

        // 2. Concat multi-part SMS messages
        val fullBody = StringBuilder()
        var sender = ""
        var timestamp = System.currentTimeMillis()

        for (message in messages) {
            val addr = message.originatingAddress
            if (!addr.isNullOrBlank() && sender.isEmpty()) {
                sender = addr
            }
            fullBody.append(message.messageBody ?: "")
            timestamp = message.timestampMillis
        }

        val body = fullBody.toString()

        // 3. Keep receiver alive with goAsync(); the shared SmsProcessor runs
        // the full pipeline (parse → checks → DB → webhook) on IO.
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = SmsProcessor.processIncomingSms(context, sender, body, timestamp)
                Log.d(TAG, "Processed SMS from $sender: accepted=${result.accepted}, ${result.reason}")
            } catch (e: Exception) {
                Log.e(TAG, "Error in SmsReceiver background processing", e)
            } finally {
                try {
                    if (wakeLock?.isHeld == true) wakeLock.release()
                } catch (_: Exception) {}
                pendingResult.finish()
            }
        }
    }
}
