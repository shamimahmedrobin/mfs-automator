package com.example.network

import android.util.Log
import com.example.data.PaymentTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WebhookClient {
    private const val WEBHOOK_URL = "https://yourdomain.com/api/payment-callback"
    private const val TAG = "WebhookClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    suspend fun postTransaction(
        transaction: PaymentTransaction,
        url: String,
        token: String,
        deviceId: String = "",
        customHeader: String = ""
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val cleanAmountStr = transaction.amount.replace(",", "").trim()
            val numericAmount: Number? = cleanAmountStr.toDoubleOrNull()?.let {
                if (it % 1.0 == 0.0) it.toLong() else it
            }

            val last4 = if (transaction.senderNumber.length >= 4) {
                transaction.senderNumber.takeLast(4)
            } else {
                transaction.senderNumber
            }

            val activeDeviceId = if (deviceId.isNotBlank()) deviceId else (transaction.deviceId ?: "")

            val jsonObject = JSONObject().apply {
                put("provider", transaction.mfsName)
                put("trx_id", transaction.trxId)
                if (numericAmount != null) {
                    put("amount", numericAmount)
                } else {
                    put("amount", cleanAmountStr)
                }
                put("sender", transaction.senderNumber)
                put("sender_last4", last4)
                put("timestamp", transaction.timestamp)
                put("device_id", activeDeviceId)
                put("raw_sms", transaction.body)
            }

            val requestBody = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val requestBuilder = Request.Builder()
                .url(if (url.isNotBlank()) url else "https://yourdomain.com/api/payment-callback")
                .post(requestBody)
                
            if (token.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            if (customHeader.isNotBlank()) {
                val parts = customHeader.split(":", limit = 2)
                if (parts.size == 2) {
                    requestBuilder.addHeader(parts[0].trim(), parts[1].trim())
                }
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                Log.d(TAG, "Response code: ${response.code}")
                if (response.isSuccessful) {
                    return@withContext Pair(true, null)
                } else {
                    return@withContext Pair(false, "HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error posting webhook: ${e.message}", e)
            return@withContext Pair(false, e.message ?: "Unknown error")
        }
    }
}
