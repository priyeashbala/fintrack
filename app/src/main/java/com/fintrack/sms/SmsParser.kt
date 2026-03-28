package com.fintrack.sms

import com.fintrack.data.SmsTransactionEntity
import java.util.Locale

/**
 * Parses raw bank/payment SMS text into normalized transaction entities.
 */
object SmsParser {
    private val amountRegex = Regex("(?:INR|Rs\\.?|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)

    /**
     * Extracts transaction type, amount, and channel from one SMS body.
     * Returns null when message does not look like a financial transaction.
     */
    fun parse(sender: String, body: String, timestampMillis: Long): SmsTransactionEntity? {
        val normalized = body.lowercase(Locale.ENGLISH)
        val type = when {
            normalized.contains("credited") || normalized.contains("received") || normalized.contains("deposited") -> "INCOME"
            normalized.contains("debited") || normalized.contains("spent") || normalized.contains("paid") || normalized.contains("withdrawn") -> "EXPENSE"
            else -> null
        } ?: return null

        val amount = amountRegex.find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?: return null

        val channel = when {
            normalized.contains("upi") -> "UPI"
            normalized.contains("netbanking") -> "NETBANKING"
            normalized.contains("neft") -> "NEFT"
            normalized.contains("rtgs") -> "RTGS"
            normalized.contains("imps") -> "IMPS"
            normalized.contains("card") || normalized.contains("pos") -> "CARD"
            normalized.contains("atm") -> "ATM"
            else -> "OTHER"
        }

        return SmsTransactionEntity(
            sender = sender,
            body = body,
            amount = amount,
            type = type,
            channel = channel,
            title = sender.ifBlank { "Bank SMS" },
            occurredAt = timestampMillis
        )
    }
}
