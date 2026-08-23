package com.ryan.pinehill.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    @Test
    fun kakaoBankDeposit_isParsedWithBankAndSender() {
        val text = "[Web발신] [카카오뱅크] 심*민(8205) 08/23 14:20 입금 500,000원 박진환 잔액 9,851,574원"

        val parsed = SmsParser.parseDepositSms(text)

        assertTrue(SmsParser.isKakaoBankDeposit(text))
        assertEquals("카카오뱅크", parsed?.bankName)
        assertEquals(500_000L, parsed?.amount)
        assertEquals("08/23", parsed?.dateStr)
        assertEquals("14:20", parsed?.timeStr)
        assertEquals("박진환", parsed?.senderName)
    }

    @Test
    fun otherKnownBankDeposit_isAlsoParsed() {
        val text = "[KB국민은행] 08/23 09:10 입금 450,000원 홍길동 잔액 3,000,000원"

        val parsed = SmsParser.parseDepositSms(text)

        assertEquals("KB국민은행", parsed?.bankName)
        assertEquals(450_000L, parsed?.amount)
        assertEquals("홍길동", parsed?.senderName)
    }

    @Test
    fun bankCanBeDetectedFromSmsAddress() {
        val text = "08/23 09:10 입금 450,000원 홍길동 잔액 3,000,000원"

        val parsed = SmsParser.parseDepositSms(text, "카카오뱅크")

        assertEquals("카카오뱅크", parsed?.bankName)
        assertEquals(450_000L, parsed?.amount)
    }

    @Test
    fun withdrawal_isNotDeposit() {
        val text = "[Web발신] [카카오뱅크] 심*민(8205) 08/23 14:20 출금 50,000원 관리비 잔액 9,801,574원"

        assertFalse(SmsParser.isBankDeposit(text))
        assertNull(SmsParser.parseDepositSms(text))
    }

    @Test
    fun senderNameNormalization_ignoresSpaces() {
        assertEquals("홍길동", SmsParser.normalizeName(" 홍 길동 "))
    }
}
