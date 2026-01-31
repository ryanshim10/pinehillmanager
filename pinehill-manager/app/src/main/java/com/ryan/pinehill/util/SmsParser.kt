package com.ryan.pinehill.util

import com.ryan.pinehill.data.model.PaymentSource
import com.ryan.pinehill.data.model.ExpenseSource
import java.text.SimpleDateFormat
import java.util.*

data class ParsedPayment(
    val amount: Long,
    val dateStr: String,      // MM/dd
    val timeStr: String,      // HH:mm
    val senderName: String?,
    val rawText: String
)

data class ParsedExpense(
    val amount: Long,
    val dateStr: String,
    val timeStr: String,
    val counterparty: String?,  // 받는 사람/처
    val memo: String?,
    val rawText: String
)

object SmsParser {
    
    // 카카오뱅크 입금 파싱
    fun parseDepositSms(text: String): ParsedPayment? {
        // 예: "[Web발신] [카카오뱅크] 심*민(8205) 01/23 11:59 입금 450,000원 박진환 잔액 9,851,574원"
        
        if (!text.contains("입금")) return null
        
        // 날짜/시간: 01/23 11:59
        val dateTimeRegex = "(\\d{2})/(\\d{2})\\s+(\\d{2}):(\\d{2})".toRegex()
        val dateTimeMatch = dateTimeRegex.find(text)
        
        // 금액: 450,000원
        val amountRegex = "입금\\s+([\\d,]+)원".toRegex()
        val amountMatch = amountRegex.find(text)
        
        // 별명: 원 앞에 있는 이름
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
    
    // 카카오뱅크 출금 파싱
    fun parseWithdrawalSms(text: String): ParsedExpense? {
        // 예: "[Web발신] [카카오뱅크] 심*민(8205) 01/26 12:23 출금 20,000원 홍원표(경동나비엔용 잔액 9,733,979원"
        
        if (!text.contains("출금")) return null
        
        val dateTimeRegex = "(\\d{2})/(\\d{2})\\s+(\\d{2}):(\\d{2})".toRegex()
        val dateTimeMatch = dateTimeRegex.find(text)
        
        val amountRegex = "출금\\s+([\\d,]+)원".toRegex()
        val amountMatch = amountRegex.find(text)
        
        // 상대방: 원 앞에 있는 텍스트 (괄호 포함 가능)
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
    
    // 문자 날짜를 timestamp로 변환 (현재 연도 기준)
    fun parseToTimestamp(dateStr: String, timeStr: String): Long {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val format = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.KOREA)
        return try {
            format.parse("$year/$dateStr $timeStr")?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
    
    // YYYY-MM 형태의 month 문자열 반환
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
