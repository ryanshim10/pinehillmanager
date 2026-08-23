package com.ryan.pinehill.util

import com.ryan.pinehill.data.model.ContractRecord
import com.ryan.pinehill.data.model.Payment
import com.ryan.pinehill.data.model.PaymentSource
import com.ryan.pinehill.data.model.PaymentStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class RentHistoryCalculatorTest {

    private val unitId = "PINE-303"

    @Test
    fun matchedSmsPayment_marksMonthPaid() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 24, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val contract = ContractRecord(
            tenantName = "홍길동",
            unitId = unitId,
            monthlyRent = 500_000,
            paymentDay = 5,
            startDate = "2026-01-01",
            endDate = "2027-01-31"
        )
        val payment = Payment(
            unitId = unitId,
            tenantKey = null,
            month = "2026-08",
            paidAt = now,
            amount = 500_000,
            senderName = "홍길동",
            source = PaymentSource.SMS,
            status = PaymentStatus.PAID
        )

        val row = RentHistoryCalculator.build24Months(unitId, listOf(payment), listOf(contract), now).first()

        assertEquals("2026-08", row.month)
        assertEquals(RentMonthState.PAID, row.state)
        assertEquals(500_000L, row.paidAmount)
    }

    @Test
    fun partialPayment_isVisibleAsPartial() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 24, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val contract = ContractRecord(
            tenantName = "홍길동",
            unitId = unitId,
            monthlyRent = 500_000,
            paymentDay = 5,
            startDate = "2026-01-01",
            endDate = "2027-01-31"
        )
        val payment = Payment(
            unitId = unitId,
            tenantKey = null,
            month = "2026-08",
            paidAt = now,
            amount = 300_000,
            senderName = "홍길동",
            source = PaymentSource.SMS,
            status = PaymentStatus.PAID
        )

        val row = RentHistoryCalculator.build24Months(unitId, listOf(payment), listOf(contract), now).first()

        assertEquals(RentMonthState.PARTIAL, row.state)
        assertEquals(300_000L, row.paidAmount)
    }

    @Test
    fun noPaymentAfterDue_isUnpaid() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 24, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val contract = ContractRecord(
            tenantName = "홍길동",
            unitId = unitId,
            monthlyRent = 500_000,
            paymentDay = 5,
            startDate = "2026-01-01",
            endDate = "2027-01-31"
        )

        val row = RentHistoryCalculator.build24Months(unitId, emptyList(), listOf(contract), now).first()

        assertEquals(RentMonthState.UNPAID, row.state)
    }

    @Test
    fun beforePaymentDay_isNotYetMarkedUnpaid() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.AUGUST, 3, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val contract = ContractRecord(
            tenantName = "홍길동",
            unitId = unitId,
            monthlyRent = 500_000,
            paymentDay = 5,
            startDate = "2026-01-01",
            endDate = "2027-01-31"
        )

        val row = RentHistoryCalculator.build24Months(unitId, emptyList(), listOf(contract), now).first()

        assertEquals(RentMonthState.BEFORE_DUE, row.state)
    }
}
