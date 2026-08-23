package com.ryan.pinehill.util

import java.text.SimpleDateFormat
import java.util.*

data class ParsedPayment(
    val bankName: String,
    val amount: Long,
    val dateStr: String,
    val timeStr: String,
    val senderName: String?,
    val rawText: String
)

data class ParsedExpense(
    val bankName: String,
    val amount: Long,
    val dateStr: String,
    val timeStr: String,
    val counterparty: String?,
    val memo: String?,
    val rawText: String
)

object SmsParser {
    private val bankAliases = linkedMapOf(
        "카카오뱅크" to listOf("카카오뱅크", "KAKAOBANK"),
        "KB국민은행" to listOf("KB국민", "국민은행", "KB은행"),
        "신한은행" to listOf("신한은행", "신한"),
        "하나은행" to listOf("하나은행", "KEB하나"),
        "우리은행" to listOf("우리은행"),
        "NH농협은행" to listOf("NH농협", "농협은행", "농협"),
        "IBK기업은행" to listOf("IBK기업", "기업은행"),
        "토스뱅크" to listOf("토스뱅크", "TOSS BANK"),
        "케이뱅크" to listOf("케이뱅크", "K뱅크", "K BANK"),
        "SC제일은행" to listOf("SC제일", "제일은행"),
        "iM뱅크" to listOf("iM뱅크", "대구은행"),
        "부산은행" to listOf("부산은행", "BNK부산"),
        "경남은행" to listOf("경남은행", "BNK경남"),
        "광주은행" to listOf("광주은행"),
        "전북은행" to listOf("전북은행"),
        "제주은행" to listOf("제주은행"),
        "새마을금고" to listOf("새마을금고", "MG새마을"),
        "신협" to listOf("신협"),
        "우체국" to listOf("우체국")
    )

    fun detectBankName(text: String, address: String? = null): String? {
        val haystack = "$text ${address.orEmpty()}"
        bankAliases.forEach { (canonical, aliases) ->
            if (aliases.any { haystack.contains(it, ignoreCase = true) }) return canonical
        }
        return "\\[([^\\]]*(?:은행|뱅크|농협|신협|금고|우체국)[^\\]]*)]".toRegex(RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun isBankDeposit(text: String, address: String? = null): Boolean =
        !text.contains("출금") && text.contains("입금") && detectBankName(text, address) != null

    fun isKakaoBankMessage(text: String): Boolean = detectBankName(text) == "카카오뱅크"
    fun isKakaoBankDeposit(text: String): Boolean = isKakaoBankMessage(text) && text.contains("입금")

    fun parseDepositSms(text: String, address: String? = null): ParsedPayment? {
        val bankName = detectBankName(text, address) ?: return null
        if (!text.contains("입금") || text.contains("출금")) return null

        val dateTimeMatch = "(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})".toRegex().find(text)
        val amountMatch = "입금(?:액)?\\s*[:]?\\s*([\\d,]+)\\s*원".toRegex().find(text)
            ?: "([\\d,]+)\\s*원\\s*입금".toRegex().find(text)
            ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toLongOrNull() ?: return null

        val senderName = listOf(
            "원\\s+(.+?)(?:\\s+잔액|\\s+현재잔액|$)".toRegex(),
            "입금자\\s*[:]?\\s*([^\\n]+)".toRegex(),
            "보낸분\\s*[:]?\\s*([^\\n]+)".toRegex()
        ).firstNotNullOfOrNull { regex -> regex.find(text)?.groupValues?.getOrNull(1)?.trim() }
            ?.replace(Regex("\\s{2,}"), " ")?.take(40)

        return ParsedPayment(
            bankName = bankName,
            amount = amount,
            dateStr = dateTimeMatch?.let { "%02d/%02d".format(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.orEmpty(),
            timeStr = dateTimeMatch?.let { "%02d:%02d".format(it.groupValues[3].toInt(), it.groupValues[4].toInt()) }.orEmpty(),
            senderName = senderName,
            rawText = text
        )
    }

    fun parseWithdrawalSms(text: String, address: String? = null): ParsedExpense? {
        val bankName = detectBankName(text, address) ?: return null
        if (!text.contains("출금")) return null
        val dateTimeMatch = "(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})".toRegex().find(text)
        val amountMatch = "출금\\s*([\\d,]+)\\s*원".toRegex().find(text) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toLongOrNull() ?: return null
        val counterparty = "원\\s+(.+?)(?:\\s+잔액|$)".toRegex().find(text)?.groupValues?.getOrNull(1)?.trim()
        return ParsedExpense(bankName, amount,
            dateTimeMatch?.let { "%02d/%02d".format(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.orEmpty(),
            dateTimeMatch?.let { "%02d:%02d".format(it.groupValues[3].toInt(), it.groupValues[4].toInt()) }.orEmpty(),
            counterparty, counterparty, text)
    }

    fun parseToTimestamp(dateStr: String, timeStr: String): Long {
        if (dateStr.isBlank() || timeStr.isBlank()) return System.currentTimeMillis()
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val format = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.KOREA)
        return runCatching { format.parse("$year/$dateStr $timeStr")?.time }.getOrNull() ?: System.currentTimeMillis()
    }

    fun getMonthString(dateStr: String): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val parts = dateStr.split("/")
        return if (parts.size == 2 && parts[0].isNotBlank()) "$year-${parts[0].padStart(2, '0')}"
        else SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date())
    }

    fun dayOfMonth(timestamp: Long?): Int = Calendar.getInstance().apply {
        timeInMillis = timestamp ?: System.currentTimeMillis()
    }.get(Calendar.DAY_OF_MONTH)

    fun normalizeName(name: String?): String = name.orEmpty().replace(Regex("\\s+"), "").trim()
}
