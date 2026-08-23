package com.ryan.pinehill.util

import java.text.SimpleDateFormat
import java.util.*

data class ParsedPayment(
    val amount: Long,
    val dateStr: String,
    val timeStr: String,
    val senderName: String?,
    val rawText: String
)

data class ParsedExpense(
    val amount: Long,
    val dateStr: String,
    val timeStr: String,
    val counterparty: String?,
    val memo: String?,
    val rawText: String
)

object SmsParser {

    fun isKakaoBankMessage(text: String): Boolean =
        text.contains("카카오뱅크", ignoreCase = true)

    fun isKakaoBankDeposit(text: String): Boolean =
        isKakaoBankMessage(text) && text.contains("입금")

    fun parseDepositSms(text: String): ParsedPayment? {
        if (!isKakaoBankDeposit(text)) return null

        val dateTimeRegex = "(\\d{2})/(\\d{2})\\s+(\\d{2}):(\\d{2})".toRegex()
        val dateTimeMatch = dateTimeRegex.find(text)

        val amountRegex = "입금\\s+([\\d,]+)원".toRegex()
        val amountMatch = amountRegex.find(text)

        val senderRegex = "원\\s+(.+?)\\s+잔액".toRegex()
        val senderMatch = senderRegex.find(text)

        if (dateTimeMatch == null || amountMatch == null) return null
        val amount = amountMatch.groupValues[1].replace(",", "").toLongOrNull() ?: return null

        return ParsedPayment(
            amount = amount,
            dateStr = "${dateTimeMatch.groupValues[1]}/${dateTimeMatch.groupValues[2]}",
            timeStr = "${dateTimeMatch.groupValues[3]}:${dateTimeMatch.groupValues[4]}",
            senderName = senderMatch?.groupValues?.get(1)?.trim(),
            rawText = text
        )
    }

    fun parseWithdrawalSms(text: String): ParsedExpense? {
        if (!isKakaoBankMessage(text) || !text.contains("출금")) return null

        val dateTimeRegex = "(\\d{2})/(\\d{2})\\s+(\\d{2}):(\\d{2})".toRegex()
        val dateTimeMatch = dateTimeRegex.find(text)

        val amountRegex = "출금\\s+([\\d,]+)원".toRegex()
        val amountMatch = amountRegex.find(text)

        val counterpartyRegex = "원\\s+(.+?)(?:\\s+잔액|$)".toRegex()
        val counterpartyMatch = counterpartyRegex.find(text)

        if (dateTimeMatch == null || amountMatch == null) return null
        val amount = amountMatch.groupValues[1].replace(",", "").toLongOrNull() ?: return null
        val counterparty = counterpartyMatch?.groupValues?.get(1)?.trim()

        return ParsedExpense(
            amount = amount,
            dateStr = "${dateTimeMatch.groupValues[1]}/${dateTimeMatch.groupValues[2]}",
            timeStr = "${dateTimeMatch.groupValues[3]}:${dateTimeMatch.groupValues[4]}",
            counterparty = counterparty,
            memo = counterparty,
            rawText = text
        )
    }

    fun parseToTimestamp(dateStr: String, timeStr: String): Long {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val format = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.KOREA)
        return try {
            format.parse("$year/$dateStr $timeStr")?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    fun getMonthString(dateStr: String): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val parts = dateStr.split("/")
        return if (parts.size == 2) {
            "$year-${parts[0].padStart(2, '0')}"
        } else {
            val now = Calendar.getInstance()
            "${now.get(Calendar.YEAR)}-${String.format("%02d", now.get(Calendar.MONTH) + 1)}"
        }
    }
}
