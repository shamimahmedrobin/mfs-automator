package com.example.network

import android.util.Log
import com.example.BuildConfig
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
    private const val TAG = "WebhookClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        // Never log full bodies in release builds: payloads contain customer
        // phone numbers and raw MFS SMS text.
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    suspend fun postTransaction(
        transaction: PaymentTransaction,
        url: String,
        token: String,
        deviceId: String = "",
        customHeader: String = "",
        webhookSecret: String = ""
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext Pair(false, "Webhook URL not configured")
        }
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
                // StyleSphere's /api/sms-webhook reads the full SMS text from
                // full_sms / message / body — raw_sms alone is ignored there.
                put("full_sms", transaction.body)
                put("is_suspicious", transaction.isSuspicious)
                // "in" = customer payment (matchable to orders), "out" = owner
                // withdrawal — the server must NOT match these to orders.
                put("direction", transaction.direction)
                if (!transaction.fee.isNullOrBlank()) {
                    put("fee", transaction.fee)
                }
                if (!transaction.suspicionReason.isNullOrBlank()) {
                    put("suspicion_reason", transaction.suspicionReason)
                }
            }

            val requestBody = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)

            if (webhookSecret.isNotBlank()) {
                requestBuilder.addHeader("x-webhook-secret", webhookSecret)
            }
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
