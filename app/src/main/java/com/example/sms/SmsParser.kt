package com.example.sms

import android.util.Log

object SmsParser {
    private const val TAG = "SmsParser"

    data class ParsedMfsData(
        val mfsName: String,
        val amount: String,
        val senderNumber: String,
        val trxId: String,
        val currentBalance: String? = null
    )

    fun parseMessage(sender: String, body: String): ParsedMfsData? {
        val mfsName = determineMfsName(sender, body) ?: return null

        val amount = extractAmount(body)
        val trxId = extractTrxId(body)
        val senderNumber = extractSenderNumber(body) ?: sender
        val currentBalance = extractBalance(body)

        if (amount != null && trxId != null) {
            return ParsedMfsData(
                mfsName = mfsName,
                amount = amount,
                senderNumber = senderNumber,
                trxId = trxId,
                currentBalance = currentBalance
            )
        } else {
            Log.d(TAG, "Failed to parse: amount=$amount, trxId=$trxId")
            return null
        }
    }

    private fun determineMfsName(sender: String, body: String): String? {
        val lowerSender = sender.lowercase()
        return when {
            lowerSender.contains("bkash") -> "bKash"
            lowerSender.contains("nagad") -> "Nagad"
            lowerSender.contains("16216") -> "Rocket" // Rocket uses 16216
            lowerSender.contains("upay") -> "Upay"
            else -> null
        }
    }

    private fun extractAmount(body: String): String? {
        // More robust BD MFS amount extraction
        val regex = Regex("(?i)(?:Tk|BDT|Amount|Received)[:\\s]*([0-9,.]+)")
        val matchResult = regex.find(body)
        return matchResult?.groupValues?.get(1)?.replace(",", "")
    }

    private fun extractTrxId(body: String): String? {
        // Matches TrxID 9J52GHI7, TrxId: 9J52GHI7, TxnId: etc.
        val regex = Regex("(?i)(?:TrxI[dD]?|TxnId)[:\\s]*([A-Za-z0-9]+)")
        val matchResult = regex.find(body)
        return matchResult?.groupValues?.get(1)
    }

    private fun extractSenderNumber(body: String): String? {
        // Matches 11 digit numbers following 'from' or 'Sender' or simply any 11 digit number
        val regex = Regex("(?i)(?:from|Sender)[:\\s]*([0-9]{11})")
        val matchResult = regex.find(body)
        if (matchResult != null) {
            return matchResult.groupValues[1]
        }
        
        // Fallback: look for any 11-digit number that starts with 01
        val fallbackRegex = Regex("(01[3-9][0-9]{8})")
        return fallbackRegex.find(body)?.groupValues?.get(1)
    }

    private fun extractBalance(body: String): String? {
        // Matches Balance Tk 1500.50, Balance: BDT 500 etc.
        val regex = Regex("(?i)Balance[\\s:]*(?:Tk|BDT)?[\\s:]*([0-9,.]+)")
        val matchResult = regex.find(body)
        return matchResult?.groupValues?.get(1)?.replace(",", "")
    }
}
