package com.example.sms

import com.example.data.PaymentTransactionDao
import kotlin.math.abs
import kotlin.math.max

/**
 * Fraud hardening for spoofed MFS SMS.
 *
 * A forged SMS can carry a plausible TrxID/amount, but it cannot know the real
 * account balance. When the SMS includes a balance figure, we compare it against
 * the previous recorded balance for the same provider, walking the full money
 * chain — BOTH incoming payments and outgoing withdrawals:
 *
 *     expected = previousBalance + receivedAmount            (direction = "in")
 *     expected = previousBalance - withdrawnAmount - fee     (direction = "out")
 *
 * Withdrawals (cash out, send money, merchant payments) are tracked as negative
 * legs so the chain stays continuous when the owner takes money out. If the
 * numbers don't line up (beyond tolerance), the transaction is flagged as
 * suspicious. It is STILL stored and forwarded to the webhook — the server
 * (StyleSphere /api/sms-webhook) makes the final call — but the
 * is_suspicious / suspicion_reason fields let the server hold it for review
 * instead of auto-matching it to an order.
 *
 * If the chain ever breaks (missed SMS, SIM swap, second phone), the owner can
 * tap "Reset balance baseline" in Settings: the next transaction skips the
 * check once and its balance becomes the new baseline.
 */
object BalanceCheck {

    /**
     * @return Pair(isSuspicious, reason). reason is null when not suspicious.
     */
    suspend fun evaluate(
        dao: PaymentTransactionDao,
        provider: String,
        amountStr: String,
        direction: String,
        feeStr: String?,
        balanceStr: String?,
        beforeTimestamp: Long
    ): Pair<Boolean, String?> {
        val balance = balanceStr?.replace(",", "")?.toDoubleOrNull()
            ?: return false to null // SMS has no balance figure: nothing to verify
        val amount = amountStr.replace(",", "").toDoubleOrNull()
            ?: return false to null
        val fee = feeStr?.replace(",", "")?.toDoubleOrNull() ?: 0.0

        val previous = dao.getLatestBalanceBefore(provider, beforeTimestamp)
            ?: return false to null // first tracked transaction: nothing to compare
        val previousBalance = previous.currentBalance?.replace(",", "")?.toDoubleOrNull()
            ?: return false to null

        val isOut = direction == "out"
        val expected = if (isOut) previousBalance - amount - fee else previousBalance + amount
        // Tolerance: incoming legs are exact (no fees); outgoing legs get a wider
        // band to absorb any fee figure the SMS didn't spell out.
        val tolerance = if (isOut) max(15.0, (amount + fee) * 0.05) else max(2.0, amount * 0.02)

        return if (abs(balance - expected) > tolerance) {
            val dirWord = if (isOut) "withdrawn" else "received"
            true to "Balance mismatch: SMS shows ৳${trim(balance)}, expected ~৳${trim(expected)} " +
                    "(prev ৳${trim(previousBalance)} ${if (isOut) "-" else "+"} ৳${trim(amount)} $dirWord" +
                    (if (isOut && fee > 0) " - fee ৳${trim(fee)}" else "") + ")"
        } else {
            false to null
        }
    }

    private fun trim(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)
}
