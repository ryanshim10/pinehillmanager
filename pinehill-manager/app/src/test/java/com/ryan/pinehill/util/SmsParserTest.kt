package com.ryan.pinehill.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    @Test
    fun kakaoBankDeposit_isParsed() {
        val text = "[Web발신] [카카오뱅크] 심*민(8205) 08/23 14:20 입금 500,000원 박진환 잔액 9,851,574원"

        val parsed = SmsParser.parseDepositSms(text)

        assertTrue(SmsParser.isKakaoBankDeposit(text))
        assertEquals(500_000L, parsed?.amount)
        assertEquals("08/23", parsed?.dateStr)
        assertEquals("14:20", parsed?.timeStr)
        assertEquals("박진환", parsed?.senderName)
    }

    @Test
    fun nonKakaoBankDeposit_isRejected() {
        val text = "[Web발신] [다른은행] 08/23 14:20 입금 500,000원 박진환 잔액 9,851,574원"

        assertFalse(SmsParser.isKakaoBankDeposit(text))
        assertNull(SmsParser.parseDepositSms(text))
    }

    @Test
    fun kakaoWithdrawal_isNotDeposit() {
        val text = "[Web발신] [카카오뱅크] 심*민(8205) 08/23 14:20 출금 50,000원 관리비 잔액 9,801,574원"

        assertFalse(SmsParser.isKakaoBankDeposit(text))
        assertNull(SmsParser.parseDepositSms(text))
    }
}
