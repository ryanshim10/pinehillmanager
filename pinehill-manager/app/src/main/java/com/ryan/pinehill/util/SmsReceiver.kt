package com.ryan.pinehill.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.ryan.pinehill.data.AppDatabase
import com.ryan.pinehill.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SmsReceiver : BroadcastReceiver() {
    companion object { const val TAG = "SmsReceiver" }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        Telephony.Sms.Intents.getMessagesFromIntent(intent)?.forEach { sms ->
            processBankSms(context, sms.displayMessageBody, sms.displayOriginatingAddress, sms.timestampMillis)
        }
    }

    private fun processBankSms(context: Context, text: String, address: String?, receivedAt: Long) {
        val database = AppDatabase.getDatabase(context)
        CoroutineScope(Dispatchers.IO).launch {
            if (database.paymentDao().countSmsByRawText(text) > 0) return@launch

            val parsedPayment = SmsParser.parseDepositSms(text, address)
            if (parsedPayment != null) {
                val day = SmsParser.dayOfMonth(receivedAt)
                val sender = SmsParser.normalizeName(parsedPayment.senderName)
                val rule = database.paymentMatchRuleDao().getEnabledRulesNow().firstOrNull {
                    it.bankName == parsedPayment.bankName &&
                        SmsParser.normalizeName(it.senderName) == sender &&
                        it.amount == parsedPayment.amount &&
                        it.dayOfMonth == day
                }
                val month = SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date(receivedAt))
                database.paymentDao().insertPayment(
                    Payment(
                        tenantKey = null,
                        unitId = rule?.unitId.orEmpty(),
                        month = month,
                        paidAt = receivedAt,
                        amount = parsedPayment.amount,
                        senderName = parsedPayment.senderName,
                        source = PaymentSource.SMS,
                        status = if (rule != null) PaymentStatus.PAID else PaymentStatus.PENDING,
                        statusOverride = rule != null,
                        rawSms = text
                    )
                )
                Log.d(TAG, "Bank deposit ${parsedPayment.bankName} ${parsedPayment.amount}, autoMatch=${rule?.unitId}")
                return@launch
            }

            val parsedExpense = SmsParser.parseWithdrawalSms(text, address) ?: return@launch
            database.expenseDao().insertExpense(
                Expense(
                    spentAt = receivedAt,
                    amount = parsedExpense.amount,
                    category = ExpenseCategory.OTHER,
                    memo = parsedExpense.memo.orEmpty(),
                    unitId = null,
                    month = SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date(receivedAt)),
                    source = ExpenseSource.SMS,
                    rawSms = text
                )
            )
        }
    }
}
