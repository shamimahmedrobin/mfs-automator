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
        // Normalize Bengali digits (০-৯) to English so amount/TrxID regexes work
        // on Bangla SMS text as well.
        val normalizedBody = normalizeDigits(body)
        val mfsName = determineMfsName(sender, normalizedBody) ?: return null

        val amount = extractAmount(normalizedBody)
        val trxId = extractTrxId(normalizedBody)
        val senderNumber = extractSenderNumber(normalizedBody) ?: sender
        val currentBalance = extractBalance(normalizedBody)

        if (amount != null && trxId != null) {
            return ParsedMfsData(
                mfsName = mfsName,
                amount = amount,
                senderNumber = senderNumber,
                trxId = normalizeTrxId(trxId),
                currentBalance = currentBalance
            )
        } else {
            Log.d(TAG, "Failed to parse: amount=$amount, trxId=$trxId")
            return null
        }
    }

    /**
     * Converts Bengali digits to English digits, e.g. "৫০০" -> "500".
     */
    fun normalizeDigits(text: String): String {
        val bnDigits = "০১২৩৪৫৬৭৮৯"
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val idx = bnDigits.indexOf(ch)
            sb.append(if (idx >= 0) ('0' + idx) else ch)
        }
        return sb.toString()
    }

    /**
     * Canonical TrxID form used for dedupe: uppercase, alphanumeric only.
     * Matches the normalization done server-side in StyleSphere's
     * /api/sms-webhook, so "9hx8k2" and "9HX8K2" dedupe to one record.
     */
    fun normalizeTrxId(trxId: String): String {
        return trxId.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
    }

    private fun determineMfsName(sender: String, body: String): String? {
        val lowerSender = sender.lowercase()
        val lowerBody = body.lowercase()

        // 1. Sender-level strict priority (Originating address is authoritative)
        if (lowerSender.contains("16216") || lowerSender.contains("rocket") || lowerSender.contains("dbbl") || lowerSender.contains("nexus")) {
            return "Rocket"
        }
        if (lowerSender.contains("bkash") || lowerSender.contains("16247")) {
            return "bKash"
        }
        if (lowerSender.contains("nagad") || lowerSender.contains("16167")) {
            return "Nagad"
        }
        if (lowerSender.contains("upay") || lowerSender.contains("16268") || lowerSender.contains("ucb")) {
            return "Upay"
        }

        // 2. Body-level fallback if sender is generic number or shortcode
        return when {
            lowerBody.contains("rocket") || lowerBody.contains("16216") || lowerBody.contains("dbbl") || lowerBody.contains("রকেট") -> "Rocket"
            lowerBody.contains("nagad") || lowerBody.contains("16167") || lowerBody.contains("নগদ") -> "Nagad"
            lowerBody.contains("upay") || lowerBody.contains("16268") || lowerBody.contains("উপায়") -> "Upay"
            lowerBody.contains("bkash") || lowerBody.contains("16247") || lowerBody.contains("বিকাশ") -> "bKash"
            else -> null
        }
    }

    private fun extractAmount(body: String): String? {
        // 1. Primary: matches "received Tk 50", "Cash In Tk 500", "deposit of Tk 50", "credited by Tk 500", "payment Tk 100"
        val primaryRegex = Regex("(?i)(?:received|deposit of|cash in|payment of|payment|credited by|credited with)[\\s:]+(?:deposit of[\\s:]+)?(?:Tk\\.?|BDT)?[\\s:]*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)")
        val primaryMatch = primaryRegex.find(body)
        if (primaryMatch != null) {
            return primaryMatch.groupValues[1].replace(",", "")
        }

        // 1b. Bangla style: matches "500 টাকা", "৳500", "টাকা 500"
        // (digits are already normalized to English by normalizeDigits)
        val banglaRegex = Regex("([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)\\s*(?:টাকা|৳)|(?:টাকা|৳)\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)")
        val banglaMatch = banglaRegex.find(body)
        if (banglaMatch != null) {
            val value = banglaMatch.groupValues[1].ifEmpty { banglaMatch.groupValues[2] }
            if (value.isNotEmpty()) return value.replace(",", "")
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
        val regex = Regex("(?i)(?:Trx\\s*ID|Txn\\s*ID|Transaction\\s*ID|Trans\\s*ID|Tran\\s*ID|TxID|TrxId|TxnId)[\\s:]*([A-Za-z0-9]+)")
        val matchResult = regex.find(body)
        return matchResult?.groupValues?.get(1)
    }

    private fun extractSenderNumber(body: String): String? {
        // 1. Matches 'from 018...', 'from A/C: 017...', 'from Account: 017...', 'Sender: 017...'
        val regex = Regex("(?i)(?:from(?:\\s*(?:A/C|Account))?[\\s:]*|sender[\\s:]*)(\\+?8801[3-9][0-9]{8,10}|01[3-9][0-9]{8,10})")
        val matchResult = regex.find(body)
        if (matchResult != null) {
            return matchResult.groupValues[1]
        }
        
        // 2. Fallback: look for any BD 11-12 digit mobile or Rocket account number
        val fallbackRegex = Regex("(\\+?8801[3-9][0-9]{8,10}|01[3-9][0-9]{8,10})")
        return fallbackRegex.find(body)?.groupValues?.get(1)
    }

    private fun extractBalance(body: String): String? {
        // Matches Balance Tk 1500.50, Balance: BDT 500, Balance: ৳500, Balance: 500.00 etc.
        val regex = Regex("(?i)Balance[\\s:]*(?:Tk\\.?|BDT|৳)?[\\s:]*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)")
        val matchResult = regex.find(body)
        return matchResult?.groupValues?.get(1)?.replace(",", "")
    }
}
