package com.example.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
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

        Log.d(TAG, "SMS Received!")
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        
        for (message in messages) {
            val sender = message.originatingAddress ?: continue
            val body = message.messageBody ?: continue
            val timestamp = message.timestampMillis

            Log.d(TAG, "Sender: $sender, Body: $body")
            
            val parsedData = SmsParser.parseMessage(sender, body)
            if (parsedData != null) {
                Log.d(TAG, "Parsed MFS Data: $parsedData")
                
                val transaction = PaymentTransaction(
                    mfsName = parsedData.mfsName,
                    amount = parsedData.amount,
                    senderNumber = parsedData.senderNumber,
                    trxId = parsedData.trxId,
                    timestamp = timestamp,
                    body = body,
                    currentBalance = parsedData.currentBalance
                )
                
                // Save to DB and POST to Webhook
                CoroutineScope(Dispatchers.IO).launch {
                    val settings = com.example.data.SettingsRepository(context)
                    val url = settings.webhookUrl.first()
                    val token = settings.apiToken.first()
                    
                    val isBkashEnabled = settings.bkashEnabled.first()
                    val isNagadEnabled = settings.nagadEnabled.first()
                    val isRocketEnabled = settings.rocketEnabled.first()
                    val isUpayEnabled = settings.upayEnabled.first()
                    
                    val deviceId = settings.deviceId.first()
                    val customHeader = settings.customHeader.first()
                    val minAmountSetting = settings.minAmount.first().toDoubleOrNull() ?: 0.0

                    val isEnabled = when (parsedData.mfsName.lowercase()) {
                        "bkash" -> isBkashEnabled
                        "nagad" -> isNagadEnabled
                        "rocket" -> isRocketEnabled
                        "upay" -> isUpayEnabled
                        else -> false
                    }
                    
                    val amountDouble = parsedData.amount.replace(",", "").toDoubleOrNull() ?: 0.0

                    if (isEnabled && amountDouble >= minAmountSetting) {
                        val db = AppDatabase.getDatabase(context)
                        val dao = db.paymentTransactionDao()
                        
                        val (isSuccess, errorMessage) = WebhookClient.postTransaction(transaction, url, token, deviceId, customHeader)
                        
                        val transactionToSave = transaction.copy(
                            isSynced = isSuccess,
                            deviceId = deviceId,
                            errorLog = errorMessage
                        )
                        dao.insertTransaction(transactionToSave)
                    } else {
                        Log.d(TAG, "Skipped: Provider disabled or amount less than min limit.")
                    }
                }
            }
        }
    }
}
