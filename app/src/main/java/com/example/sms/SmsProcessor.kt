package com.example.sms

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.PaymentTransaction
import com.example.network.WebhookClient
import kotlinx.coroutines.flow.first

/**
 * Shared SMS processing pipeline.
 *
 * Both the real [SmsReceiver] (system SMS broadcast) and the manual
 * "Send Test SMS" screen use this exact same path: parse → provider
 * checks → dedupe → balance check → save to DB → webhook forward.
 *
 * NOTE: test SMS injected from the app go through the REAL pipeline —
 * the server will treat them as genuine payments and may confirm a
 * matching order. Only use with test orders.
 */
object SmsProcessor {
    private const val TAG = "SmsProcessor"

    data class ProcessResult(
        val accepted: Boolean,
        val reason: String,
        val trxId: String? = null,
        val synced: Boolean = false
    )

    suspend fun processIncomingSms(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long = System.currentTimeMillis()
    ): ProcessResult {
        // Full SMS bodies contain customer phone numbers — never log them in release builds.
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Processing SMS - Sender: $sender, Body: $body")
        }

        val parsedData = SmsParser.parseMessage(sender, body)
            ?: return ProcessResult(false, "Not a recognized MFS transaction SMS.")

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Parsed MFS Data: $parsedData")
        }

        val settings = com.example.data.SettingsRepository(context)
        val isBkashEnabled = settings.bkashEnabled.first()
        val isNagadEnabled = settings.nagadEnabled.first()
        val isRocketEnabled = settings.rocketEnabled.first()
        val isUpayEnabled = settings.upayEnabled.first()
        val isBanglaQrEnabled = settings.banglaQrEnabled.first()

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
            "banglaqr" -> isBanglaQrEnabled
            else -> false
        }

        val amountDouble = parsedData.amount.replace(",", "").toDoubleOrNull() ?: 0.0

        if (!isEnabled || amountDouble < minAmountSetting) {
            return ProcessResult(
                false,
                "Skipped: ${parsedData.mfsName} listener is off or amount ($amountDouble) below minimum ($minAmountSetting)."
            )
        }

        // Manual sender allowlist: only read SMS from the configured
        // sender name(s)/number(s) for this provider.
        val senderFilter = settings.senderFilter(parsedData.mfsName).first()
        if (!SmsParser.senderAllowed(sender, senderFilter)) {
            return ProcessResult(
                false,
                "Skipped: sender '$sender' is not in the allowlist for ${parsedData.mfsName}."
            )
        }

        val db = AppDatabase.getDatabase(context)
        val dao = db.paymentTransactionDao()

        // Check duplicate trxId (normalized to uppercase/alphanumeric in SmsParser,
        // plus a UNIQUE index in the DB as a hard guard against races).
        val existing = dao.getTransactionByTrxId(parsedData.trxId)
        if (existing != null) {
            return ProcessResult(
                false,
                "TrxID ${parsedData.trxId} already exists in the app. Use a fresh TrxID for each test.",
                trxId = parsedData.trxId
            )
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
            return ProcessResult(
                accepted = true,
                reason = if (isSuccess) "Sent to server ✓" else "Saved locally, server sync failed: $errorMessage",
                trxId = parsedData.trxId,
                synced = isSuccess
            )
        } else {
            val updated = initialTransaction.copy(
                id = if (insertedRowId > 0) insertedRowId.toInt() else initialTransaction.id,
                isSynced = false,
                errorLog = "Webhook URL not configured"
            )
            dao.insertTransaction(updated)
            return ProcessResult(
                accepted = true,
                reason = "Saved locally, but Webhook URL is not configured.",
                trxId = parsedData.trxId,
                synced = false
            )
        }
    }
}
