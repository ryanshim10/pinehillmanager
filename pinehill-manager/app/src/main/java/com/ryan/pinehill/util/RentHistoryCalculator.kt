package com.ryan.pinehill.util

import com.ryan.pinehill.data.model.ContractRecord
import com.ryan.pinehill.data.model.Payment
import com.ryan.pinehill.data.model.PaymentStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class RentMonthState {
    PAID,
    PARTIAL,
    UNPAID,
    BEFORE_DUE,
    NO_CONTRACT,
    PAYMENT_ONLY
}

data class RentMonthRow(
    val month: String,
    val state: RentMonthState,
    val expectedRent: Long,
    val paidAmount: Long,
    val contract: ContractRecord?,
    val payments: List<Payment>
)

object RentHistoryCalculator {
    fun build24Months(
        unitId: String,
        payments: List<Payment>,
        contracts: List<ContractRecord>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<RentMonthRow> {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date(nowMillis))
        val currentDay = now.get(Calendar.DAY_OF_MONTH)
        val monthFormat = SimpleDateFormat("yyyy-MM", Locale.KOREA)

        val unitPayments = payments.filter {
            it.unitId == unitId && (it.status == PaymentStatus.PAID || it.status == PaymentStatus.PARTIAL)
        }
        val unitContracts = contracts.filter { it.unitId == unitId }

        return (0 until 24).map { offset ->
            val cal = (now.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -offset)
            }
            val month = monthFormat.format(cal.time)
            val contract = activeContract(unitContracts, month)
            val monthPayments = unitPayments.filter { it.month == month }
                .sortedByDescending { it.paidAt ?: it.createdAt }
            val paidAmount = monthPayments.sumOf { it.amount }
            val expected = contract?.monthlyRent ?: 0L

            val state = when {
                contract == null && paidAmount > 0L -> RentMonthState.PAYMENT_ONLY
                contract == null -> RentMonthState.NO_CONTRACT
                expected <= 0L && paidAmount > 0L -> RentMonthState.PAID
                expected <= 0L -> RentMonthState.NO_CONTRACT
                paidAmount >= expected -> RentMonthState.PAID
                paidAmount > 0L -> RentMonthState.PARTIAL
                month == currentMonth && contract.paymentDay > 0 && currentDay < contract.paymentDay -> RentMonthState.BEFORE_DUE
                else -> RentMonthState.UNPAID
            }

            RentMonthRow(
                month = month,
                state = state,
                expectedRent = expected,
                paidAmount = paidAmount,
                contract = contract,
                payments = monthPayments
            )
        }
    }

    fun activeContract(contracts: List<ContractRecord>, month: String): ContractRecord? =
        contracts
            .filter { contract ->
                val start = normalizeMonth(contract.startDate)
                val end = normalizeMonth(contract.endDate)
                (start == null || month >= start) && (end == null || month <= end)
            }
            .maxWithOrNull(compareBy<ContractRecord> { normalizeMonth(it.startDate).orEmpty() }.thenBy { it.createdAt })

    fun normalizeMonth(value: String): String? {
        val match = Regex("(\\d{4})\\D+(\\d{1,2})").find(value) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        if (month !in 1..12) return null
        return "%04d-%02d".format(Locale.US, year, month)
    }
}
