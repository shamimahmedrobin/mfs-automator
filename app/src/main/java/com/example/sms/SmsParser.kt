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
        val lowerBody = body.lowercase()
        return when {
            lowerSender.contains("bkash") || lowerSender.contains("16247") || lowerBody.contains("bkash") -> "bKash"
            lowerSender.contains("nagad") || lowerSender.contains("16167") || lowerBody.contains("nagad") -> "Nagad"
            lowerSender.contains("16216") || lowerSender.contains("rocket") || lowerSender.contains("dbbl") || 
                lowerBody.contains("16216") || lowerBody.contains("rocket") || lowerBody.contains("dbbl") -> "Rocket"
            lowerSender.contains("upay") || lowerSender.contains("16268") || lowerBody.contains("upay") -> "Upay"
            else -> null
        }
    }

    private fun extractAmount(body: String): String? {
        // 1. Primary: matches "received Tk 50", "Cash In Tk 500", "deposit of Tk 50", "payment Tk 100"
        val primaryRegex = Regex("(?i)(?:received|deposit of|cash in|payment of|payment)[\\s:]+(?:deposit of[\\s:]+)?(?:Tk\\.?|BDT)?[\\s:]*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)")
        val primaryMatch = primaryRegex.find(body)
        if (primaryMatch != null) {
            return primaryMatch.groupValues[1].replace(",", "")
        }

        // 2. Secondary: matches "Tk. 500", "Tk 500.00", "BDT 500"
        val secondaryRegex = Regex("(?i)(?:Tk\\.?|BDT)[\\s:]*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)")
        val secondaryMatch = secondaryRegex.find(body)
        if (secondaryMatch != null) {
            return secondaryMatch.groupValues[1].replace(",", "")
        }

        // 3. Tertiary: matches "Amount: 500"
        val tertiaryRegex = Regex("(?i)Amount[\\s:]*(?:Tk\\.?|BDT)?[\\s:]*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)")
        val tertiaryMatch = tertiaryRegex.find(body)
        return tertiaryMatch?.groupValues?.get(1)?.replace(",", "")
    }

    private fun extractTrxId(body: String): String? {
        // Matches TrxID 75XCY7X1, Trx ID: 75XCY7X1, TxnId: 12345, Txn ID: 12345, Transaction ID: etc.
        val regex = Regex("(?i)(?:Trx\\s*ID|Txn\\s*ID|Transaction\\s*ID|Trans\\s*ID|TxID|TrxId|TxnId)[\\s:]*([A-Za-z0-9]+)")
        val matchResult = regex.find(body)
        return matchResult?.groupValues?.get(1)
    }

    private fun extractSenderNumber(body: String): String? {
        // 1. Matches 'from 018...', 'from A/C: 017...', 'Sender: 017...'
        val regex = Regex("(?i)(?:from(?:\\s*A/C)?[\\s:]*|sender[\\s:]*)(\\+?8801[3-9][0-9]{8,9}|01[3-9][0-9]{8,9})")
        val matchResult = regex.find(body)
        if (matchResult != null) {
            return matchResult.groupValues[1]
        }
        
        // 2. Fallback: look for any BD 11-12 digit mobile or Rocket account number
        val fallbackRegex = Regex("(\\+?8801[3-9][0-9]{8,9}|01[3-9][0-9]{8,9})")
        return fallbackRegex.find(body)?.groupValues?.get(1)
    }

    private fun extractBalance(body: String): String? {
        // Matches Balance Tk 1500.50, Balance: BDT 500, Balance: 500.00 etc.
        val regex = Regex("(?i)Balance[\\s:]*(?:Tk\\.?|BDT)?[\\s:]*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)")
        val matchResult = regex.find(body)
        return matchResult?.groupValues?.get(1)?.replace(",", "")
    }
}
