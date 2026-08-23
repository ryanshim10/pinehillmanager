package com.ryan.pinehill.util

import com.ryan.pinehill.data.model.Payment
import com.ryan.pinehill.data.model.PaymentMatchRule
import kotlin.math.abs

object PaymentMatchSuggester {

    fun sameSenderRules(payment: Payment, rules: List<PaymentMatchRule>): List<PaymentMatchRule> {
        val bank = payment.rawSms?.let { SmsParser.parseDepositSms(it)?.bankName }.orEmpty()
        val sender = SmsParser.normalizeName(payment.senderName)
        if (bank.isBlank() || sender.isBlank()) return emptyList()

        return rules.filter { rule ->
            rule.enabled &&
                rule.bankName == bank &&
                SmsParser.normalizeName(rule.senderName) == sender
        }
    }

    fun suggestRule(payment: Payment, rules: List<PaymentMatchRule>): PaymentMatchRule? {
        val candidates = sameSenderRules(payment, rules)
        if (candidates.isEmpty()) return null

        val paymentDay = SmsParser.dayOfMonth(payment.paidAt)

        // 1) Same sender + exact amount is the strongest clue. If it points to one room,
        // suggest that room even when the deposit day is different.
        val exactAmount = candidates.filter { it.amount == payment.amount }
        chooseIfSingleUnit(exactAmount, payment.amount, paymentDay)?.let { return it }

        // 2) If amount differs, the regular deposit day can still identify one of several rooms.
        val exactDay = candidates.filter { it.dayOfMonth == paymentDay }
        chooseIfSingleUnit(exactDay, payment.amount, paymentDay)?.let { return it }

        // 3) If this bank+sender has only ever been connected to one room, suggest that room.
        if (candidates.map { it.unitId }.distinct().size == 1) {
            return closestRule(candidates, payment.amount, paymentDay)
        }

        // Multiple rooms and neither amount nor day disambiguates them: require a room choice.
        return null
    }

    private fun chooseIfSingleUnit(
        candidates: List<PaymentMatchRule>,
        amount: Long,
        day: Int
    ): PaymentMatchRule? {
        if (candidates.isEmpty()) return null
        return if (candidates.map { it.unitId }.distinct().size == 1) {
            closestRule(candidates, amount, day)
        } else null
    }

    private fun closestRule(
        candidates: List<PaymentMatchRule>,
        amount: Long,
        day: Int
    ): PaymentMatchRule? = candidates.minByOrNull { rule ->
        val amountGap = abs(rule.amount - amount) / 1_000L
        val dayGap = circularDayDistance(rule.dayOfMonth, day).toLong()
        amountGap + dayGap * 10L
    }

    private fun circularDayDistance(a: Int, b: Int): Int {
        val gap = abs(a - b)
        return minOf(gap, 31 - gap)
    }
}
