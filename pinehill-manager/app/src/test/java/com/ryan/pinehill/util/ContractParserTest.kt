package com.ryan.pinehill.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ContractParserTest {

    @Test
    fun contractText_extractsCoreRentalFields() {
        val text = """
            부동산 임대차계약서
            임차인 성명: 홍길동
            연락처: 010-1234-5678
            목적물 303호
            보증금 10,000,000원
            월세 500,000원
            매월 5일 지급
            계약기간 2026년 03월 01일 부터 2028년 02월 29일 까지
        """.trimIndent()

        val parsed = ContractParser.parse(text)

        assertEquals("홍길동", parsed.tenantName)
        assertEquals("010-1234-5678", parsed.tenantPhone)
        assertEquals(303, parsed.roomNo)
        assertEquals(10_000_000L, parsed.deposit)
        assertEquals(500_000L, parsed.monthlyRent)
        assertEquals(5, parsed.paymentDay)
        assertEquals("2026-03-01", parsed.startDate)
        assertEquals("2028-02-29", parsed.endDate)
    }
}
