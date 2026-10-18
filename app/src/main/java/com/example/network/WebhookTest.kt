package com.example.network

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Webhook TEST MODE diagnostic (StyleSphere server PR #34).
 *
 * POSTs { test: true, ... } to /api/sms-webhook with the same
 * x-webhook-secret header as real traffic. The server validates auth,
 * runs its parser + auto-match scoring as a DRY RUN and returns
 * diagnostics — it writes NOTHING to production (no sms_transactions
 * entry, no order updates).
 *
 * This call never touches the local transaction DB or the retry queue:
 * it is a one-off diagnostic, so a test can never be re-sent as a
 * real payment.
 *
 * Server contract:
 *  - 200: { status:'success', test:true, auth:'ok',
 *           parsed:{provider,trxId,amount,senderPhone,senderLast4},
 *           parseChecks:{providerParsed,trxIdParsed,amountParsed,senderPhoneParsed},
 *           autoMatch:{wouldConfirm,matchKind|reason,orderId,candidates},
 *           verifySimulation:{note,wouldRejectAs} }
 *  - 401: { status:'error', message:'Invalid webhook secret' }
 */
data class WebhookTestResult(
    val httpCode: Int = 0,
    val authOk: Boolean = false,
    val errorMessage: String? = null,
    val parsedProvider: String? = null,
    val parsedTrxId: String? = null,
    val parsedAmount: Double? = null,
    val parsedSenderPhone: String? = null,
    val parsedSenderLast4: String? = null,
    val providerParsed: Boolean = false,
    val trxIdParsed: Boolean = false,
    val amountParsed: Boolean = false,
    val senderPhoneParsed: Boolean = false,
    val wouldConfirm: Boolean? = null,
    val matchKind: String? = null,
    val matchOrderId: String? = null,
    val matchCandidates: Int = 0,
    val matchReason: String? = null,
    val verifyNote: String? = null,
    val wouldRejectAs: String? = null,
)

object WebhookTest {
    private const val TAG = "WebhookTest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        // Test payloads contain a fake phone number; keep bodies out of
        // release logcat just like real traffic.
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    suspend fun runTest(
        url: String,
        webhookSecret: String,
        deviceId: String,
        apiToken: String = "",
        customHeader: String = ""
    ): WebhookTestResult = withContext(Dispatchers.IO) {
        if (url.isBlank()) {
            return@withContext WebhookTestResult(errorMessage = "Webhook URL not configured")
        }
        try {
            val payload = JSONObject().apply {
                put("test", true)
                put("provider", "bKash")
                put("trx_id", "TEST123ABC")
                put("amount", 1500)
                put("sender_phone", "01580922455")
                put(
                    "full_sms",
                    "Cash In Tk 1,500.00 successful from 01580922455. TrxID TEST123ABC. Balance Tk 6,500.00."
                )
                put("device_id", deviceId)
            }
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val reqBuilder = Request.Builder().url(url).post(body)
            if (webhookSecret.isNotBlank()) {
                reqBuilder.addHeader("x-webhook-secret", webhookSecret)
            }
            if (apiToken.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $apiToken")
            }
            if (customHeader.isNotBlank()) {
                val parts = customHeader.split(":", limit = 2)
                if (parts.size == 2) {
                    reqBuilder.addHeader(parts[0].trim(), parts[1].trim())
                }
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                Log.d(TAG, "Test webhook HTTP ${response.code}")
                if (!response.isSuccessful) {
                    val msg = try {
                        JSONObject(raw).optString("message").ifBlank { "HTTP ${response.code}" }
                    } catch (_: Exception) {
                        "HTTP ${response.code}: ${response.message}"
                    }
                    return@withContext WebhookTestResult(
                        httpCode = response.code,
                        authOk = false,
                        errorMessage = msg
                    )
                }
                return@withContext parseDiag(response.code, raw)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Test webhook failed", e)
            return@withContext WebhookTestResult(errorMessage = e.message ?: "Network error")
        }
    }

    private fun JSONObject.optStr(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun parseDiag(httpCode: Int, raw: String): WebhookTestResult {
        return try {
            val root = JSONObject(raw)
            val parsed = root.optJSONObject("parsed")
            val checks = root.optJSONObject("parseChecks")
            val match = root.optJSONObject("autoMatch")
            val sim = root.optJSONObject("verifySimulation")
            WebhookTestResult(
                httpCode = httpCode,
                authOk = root.optString("auth") == "ok",
                parsedProvider = parsed?.optStr("provider"),
                parsedTrxId = parsed?.optStr("trxId"),
                parsedAmount = parsed?.takeIf { !it.isNull("amount") }?.optDouble("amount"),
                parsedSenderPhone = parsed?.optStr("senderPhone"),
                parsedSenderLast4 = parsed?.optStr("senderLast4"),
                providerParsed = checks?.optBoolean("providerParsed") == true,
                trxIdParsed = checks?.optBoolean("trxIdParsed") == true,
                amountParsed = checks?.optBoolean("amountParsed") == true,
                senderPhoneParsed = checks?.optBoolean("senderPhoneParsed") == true,
                wouldConfirm = match?.takeIf { !it.isNull("wouldConfirm") }?.optBoolean("wouldConfirm"),
                matchKind = match?.optStr("matchKind"),
                matchOrderId = match?.optStr("orderId"),
                matchCandidates = match?.optInt("candidates", 0) ?: 0,
                matchReason = match?.optStr("reason"),
                verifyNote = sim?.optStr("note"),
                wouldRejectAs = sim?.optStr("wouldRejectAs"),
            )
        } catch (e: Exception) {
            WebhookTestResult(
                httpCode = httpCode,
                errorMessage = "Could not parse server response: ${e.message}"
            )
        }
    }
}
