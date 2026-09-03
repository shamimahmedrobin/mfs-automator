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
    
    fun retrySync(transaction: PaymentTransaction) {
        viewModelScope.launch {
            val url = settings.webhookUrl.first()
            val token = settings.apiToken.first()
            val deviceId = settings.deviceId.first()
            val customHeader = settings.customHeader.first()
            
            val (isSuccess, errorMessage) = com.example.network.WebhookClient.postTransaction(
                transaction, url, token, deviceId, customHeader
            )
            
            dao.insertTransaction(transaction.copy(
                isSynced = isSuccess,
                errorLog = errorMessage
            ))
        }
    }
}
