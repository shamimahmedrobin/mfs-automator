package com.example.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import android.util.Log
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.PaymentTransaction
import com.example.network.WebhookClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
        // Full SMS bodies contain customer phone numbers — never log them in release builds.
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Full SMS - Sender: $sender, Body: $body")
        }

        val parsedData = SmsParser.parseMessage(sender, body)
        if (parsedData == null) {
            Log.d(TAG, "Not a recognized MFS transaction SMS.")
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (_: Exception) {}
            return
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Parsed MFS Data: $parsedData")
        }

        // 3. Keep receiver alive with goAsync()
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = com.example.data.SettingsRepository(context)
                val isBkashEnabled = settings.bkashEnabled.first()
                val isNagadEnabled = settings.nagadEnabled.first()
                val isRocketEnabled = settings.rocketEnabled.first()
                val isUpayEnabled = settings.upayEnabled.first()
                
                val deviceId = settings.deviceId.first()
                val customHeader = settings.customHeader.first()
                val webhookSecret = settings.webhookSecret.first()
                val minAmountSetting = settings.minAmount.first().toDoubleOrNull() ?: 0.0
                // Owner tapped "Reset balance baseline": skip the check once and
                // adopt this transaction's balance as the new baseline.
                val skipBalanceCheck = settings.baselineReset.first()
                if (skipBalanceCheck) {
                    settings.saveBaselineReset(false)
                    Log.d(TAG, "Balance baseline reset by owner — skipping check for ${parsedData.trxId}.")
                }

                val isEnabled = when (parsedData.mfsName.lowercase()) {
                    "bkash" -> isBkashEnabled
                    "nagad" -> isNagadEnabled
                    "rocket" -> isRocketEnabled
                    "upay" -> isUpayEnabled
                    else -> false
                }

                val amountDouble = parsedData.amount.replace(",", "").toDoubleOrNull() ?: 0.0

                if (!isEnabled || amountDouble < minAmountSetting) {
                    Log.d(TAG, "Skipped: Provider disabled or amount ($amountDouble) less than min limit ($minAmountSetting).")
                    return@launch
                }

                val db = AppDatabase.getDatabase(context)
                val dao = db.paymentTransactionDao()

                // Check duplicate trxId (normalized to uppercase/alphanumeric in SmsParser,
                // plus a UNIQUE index in the DB as a hard guard against races).
                val existing = dao.getTransactionByTrxId(parsedData.trxId)
                if (existing != null) {
                    Log.d(TAG, "Transaction with TrxID ${parsedData.trxId} already exists in DB. Skipping.")
                    return@launch
                }

                // Fraud check: does the SMS balance line up with the money chain?
                // Withdrawals are tracked as negative legs, so the chain stays
                // continuous when the owner takes money out. Flagged records are
                // still stored and forwarded — the server decides.
                val (isSuspicious, suspicionReason) = if (skipBalanceCheck) {
                    false to null
                } else {
                    BalanceCheck.evaluate(
                        dao,
                        parsedData.mfsName,
                        parsedData.amount,
                        parsedData.direction,
                        parsedData.fee,
                        parsedData.currentBalance,
                        timestamp
                    )
                }
                if (isSuspicious) {
                    Log.w(TAG, "Suspicious transaction ${parsedData.trxId}: $suspicionReason")
                }

                val initialTransaction = PaymentTransaction(
                    mfsName = parsedData.mfsName,
                    amount = parsedData.amount,
                    senderNumber = parsedData.senderNumber,
                    trxId = parsedData.trxId,
                    timestamp = timestamp,
                    body = body,
                    currentBalance = parsedData.currentBalance,
                    deviceId = deviceId,
                    isSynced = false,
                    errorLog = "Sync in progress...",
                    isSuspicious = isSuspicious,
                    suspicionReason = suspicionReason,
                    direction = parsedData.direction,
                    fee = parsedData.fee
                )

                // CRITICAL: SAVE TO DATABASE FIRST! Even in zero/low network, the transaction is safely recorded.
                val insertedRowId = dao.insertTransaction(initialTransaction)
                Log.d(TAG, "Transaction saved locally to DB with rowId: $insertedRowId")

                // Now attempt Webhook sync
                val url = settings.webhookUrl.first()
                val token = settings.apiToken.first()

                if (url.isNotBlank()) {
                    val (isSuccess, errorMessage) = WebhookClient.postTransaction(
                        initialTransaction, url, token, deviceId, customHeader, webhookSecret
                    )
                    val updated = initialTransaction.copy(
                        id = if (insertedRowId > 0) insertedRowId.toInt() else initialTransaction.id,
                        isSynced = isSuccess,
                        errorLog = if (isSuccess) null else errorMessage
                    )
                    dao.insertTransaction(updated)
                    Log.d(TAG, "Webhook sync result for ${parsedData.trxId}: success=$isSuccess, error=$errorMessage")
                } else {
                    val updated = initialTransaction.copy(
                        id = if (insertedRowId > 0) insertedRowId.toInt() else initialTransaction.id,
                        isSynced = false,
                        errorLog = "Webhook URL not configured"
                    )
                    dao.insertTransaction(updated)
                }
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
