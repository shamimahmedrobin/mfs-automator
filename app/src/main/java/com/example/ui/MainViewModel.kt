package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PaymentTransaction
import com.example.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).paymentTransactionDao()
    val settings = SettingsRepository(application)

    val transactions: StateFlow<List<PaymentTransaction>> = dao.getAllTransactions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun scanInbox(hoursBack: Int = 72, onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val count = com.example.sms.SmsInboxScanner.scanRecentSms(getApplication(), hoursBack)
            onComplete?.invoke(count)
        }
    }
        
    fun clearHistory() {
        viewModelScope.launch {
            dao.clearAllTransactions()
        }
    }
    
    /**
     * Webhook TEST MODE dry-run: POSTs { test: true, ... } to the configured
     * webhook URL and returns the server's diagnostic. Never touches the
     * local transaction DB or the retry queue.
     */
    fun testWebhook(onResult: (com.example.network.WebhookTestResult) -> Unit) {
        viewModelScope.launch {
            val url = settings.webhookUrl.first()
            val webhookSecret = settings.webhookSecret.first()
            val deviceId = settings.deviceId.first()
            val apiToken = settings.apiToken.first()
            val customHeader = settings.customHeader.first()
            val result = com.example.network.WebhookTest.runTest(
                url, webhookSecret, deviceId, apiToken, customHeader
            )
            onResult(result)
        }
    }

    fun retrySync(transaction: PaymentTransaction) {        viewModelScope.launch {
            val url = settings.webhookUrl.first()
            val token = settings.apiToken.first()
            val deviceId = settings.deviceId.first()
            val customHeader = settings.customHeader.first()
            val webhookSecret = settings.webhookSecret.first()
            
            val (isSuccess, errorMessage) = com.example.network.WebhookClient.postTransaction(
                transaction, url, token, deviceId, customHeader, webhookSecret
            )
            
            dao.insertTransaction(transaction.copy(
                isSynced = isSuccess,
                errorLog = errorMessage
            ))
        }
    }
}
