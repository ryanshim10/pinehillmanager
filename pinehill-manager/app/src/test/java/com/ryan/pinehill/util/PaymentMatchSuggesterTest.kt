package com.ryan.pinehill.util

import com.ryan.pinehill.data.model.Payment
import com.ryan.pinehill.data.model.PaymentMatchRule
import com.ryan.pinehill.data.model.PaymentSource
import com.ryan.pinehill.data.model.PaymentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class PaymentMatchSuggesterTest {

    @Test
    fun sameSenderSingleRoom_mismatchedAmountStillSuggestsRoom() {
        val payment = payment(amount = 470_000L, day = 7)
        val rules = listOf(
            rule(amount = 500_000L, day = 5, unitId = "PINE-303")
        )

        assertEquals("PINE-303", PaymentMatchSuggester.suggestRule(payment, rules)?.unitId)
    }

    @Test
    fun sameSenderMultipleRooms_exactAmountSelectsCorrectRoom() {
        val payment = payment(amount = 650_000L, day = 9)
        val rules = listOf(
            rule(amount = 500_000L, day = 5, unitId = "PINE-301"),
            rule(amount = 650_000L, day = 10, unitId = "PINE-302"),
            rule(amount = 800_000L, day = 15, unitId = "PINE-303")
        )

        assertEquals("PINE-302", PaymentMatchSuggester.suggestRule(payment, rules)?.unitId)
    }

    @Test
    fun sameSenderMultipleRooms_ambiguousMismatchRequiresChoice() {
        val payment = payment(amount = 575_000L, day = 20)
        val rules = listOf(
            rule(amount = 500_000L, day = 5, unitId = "PINE-301"),
            rule(amount = 650_000L, day = 10, unitId = "PINE-302")
        )

        assertNull(PaymentMatchSuggester.suggestRule(payment, rules))
    }

    private fun payment(amount: Long, day: Int): Payment {
        val paidAt = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, day, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return Payment(
            tenantKey = null,
            unitId = "",
            month = "2026-08",
            paidAt = paidAt,
            amount = amount,
            senderName = "홍길동",
            source = PaymentSource.SMS,
            status = PaymentStatus.PENDING,
            rawSms = "[카카오뱅크] 08/$day 10:00 입금 ${amount}원 홍길동 잔액 1,000,000원"
        )
    }

    private fun rule(amount: Long, day: Int, unitId: String) = PaymentMatchRule(
        bankName = "카카오뱅크",
        senderName = "홍길동",
        amount = amount,
        dayOfMonth = day,
        unitId = unitId,
        tenantName = "홍길동"
    )
}
