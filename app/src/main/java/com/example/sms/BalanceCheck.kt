package com.example.sms

import com.example.data.PaymentTransactionDao
import kotlin.math.abs
import kotlin.math.max

/**
 * Fraud hardening for spoofed MFS SMS.
 *
 * A forged SMS can carry a plausible TrxID/amount, but it cannot know the real
 * account balance. When the SMS includes a balance figure, we compare it against
 * the previous recorded balance for the same provider:
 *
 *     expected ≈ previousBalance + receivedAmount
 *
 * If the numbers don't line up (beyond a small tolerance), the transaction is
 * flagged as suspicious. It is STILL stored and forwarded to the webhook — the
 * server (StyleSphere /api/sms-webhook) makes the final call — but the
 * is_suspicious / suspicion_reason fields let the server hold it for review
 * instead of auto-matching it to an order.
 */
object BalanceCheck {

    /**
     * @return Pair(isSuspicious, reason). reason is null when not suspicious.
     */
    suspend fun evaluate(
        dao: PaymentTransactionDao,
        provider: String,
        amountStr: String,
        balanceStr: String?,
        beforeTimestamp: Long
    ): Pair<Boolean, String?> {
        val balance = balanceStr?.replace(",", "")?.toDoubleOrNull()
            ?: return false to null // SMS has no balance figure: nothing to verify
        val amount = amountStr.replace(",", "").toDoubleOrNull()
            ?: return false to null

        val previous = dao.getLatestBalanceBefore(provider, beforeTimestamp)
            ?: return false to null // first tracked transaction: nothing to compare
        val previousBalance = previous.currentBalance?.replace(",", "")?.toDoubleOrNull()
            ?: return false to null

        val expected = previousBalance + amount
        // Tolerance: ৳2 or 2% of the amount, whichever is larger (rounding/fees).
        val tolerance = max(2.0, amount * 0.02)

        return if (abs(balance - expected) > tolerance) {
            true to "Balance mismatch: SMS shows ৳${trim(balance)}, expected ~৳${trim(expected)} " +
                    "(prev ৳${trim(previousBalance)} + received ৳${trim(amount)})"
        } else {
            false to null
        }
    }

    private fun trim(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)
}
