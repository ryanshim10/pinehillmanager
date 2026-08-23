package com.ryan.pinehill.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.ryan.pinehill.data.AppDatabase
import com.ryan.pinehill.data.model.Expense
import com.ryan.pinehill.data.model.ExpenseCategory
import com.ryan.pinehill.data.model.ExpenseSource
import com.ryan.pinehill.data.model.Payment
import com.ryan.pinehill.data.model.PaymentSource
import com.ryan.pinehill.data.model.PaymentStatus
import com.ryan.pinehill.data.model.Tenant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        Telephony.Sms.Intents.getMessagesFromIntent(intent)?.forEach { sms ->
            processBankSms(
                context = context,
                text = sms.displayMessageBody,
                address = sms.displayOriginatingAddress,
                receivedAt = sms.timestampMillis
            )
        }
    }

    private fun processBankSms(
        context: Context,
        text: String,
        address: String?,
        receivedAt: Long
    ) {
        val database = AppDatabase.getDatabase(context)

        CoroutineScope(Dispatchers.IO).launch {
            val parsedPayment = SmsParser.parseDepositSms(text, address)
            if (parsedPayment != null) {
                val canonicalRaw = if (text.contains(parsedPayment.bankName, ignoreCase = true)) {
                    text
                } else {
                    "[${parsedPayment.bankName}] $text"
                }

                if (
                    database.paymentDao().countSmsByRawText(text) > 0 ||
                    database.paymentDao().countSmsByRawText(canonicalRaw) > 0
                ) return@launch

                val day = SmsParser.dayOfMonth(receivedAt)
                val sender = SmsParser.normalizeName(parsedPayment.senderName)
                val rule = database.paymentMatchRuleDao().getEnabledRulesNow().firstOrNull {
                    it.bankName == parsedPayment.bankName &&
                        SmsParser.normalizeName(it.senderName) == sender &&
                        it.amount == parsedPayment.amount &&
                        it.dayOfMonth == day
                }

                val tenantKey = rule?.let {
                    resolveTenantKey(database, it.unitId, parsedPayment.amount, day)
                }

                val month = SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date(receivedAt))
                database.paymentDao().insertPayment(
                    Payment(
                        tenantKey = tenantKey,
                        unitId = rule?.unitId.orEmpty(),
                        month = month,
                        paidAt = receivedAt,
                        amount = parsedPayment.amount,
                        senderName = parsedPayment.senderName,
                        source = PaymentSource.SMS,
                        status = if (rule != null) PaymentStatus.PAID else PaymentStatus.PENDING,
                        statusOverride = rule != null,
                        rawSms = canonicalRaw
                    )
                )
                Log.d(
                    TAG,
                    "Bank deposit ${parsedPayment.bankName} ${parsedPayment.amount}, autoMatch=${rule?.unitId}"
                )
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

    private suspend fun resolveTenantKey(
        database: AppDatabase,
        unitId: String,
        amount: Long,
        day: Int
    ): String? {
        val contracts = database.contractDao().getContractsByUnitNow(unitId)
        val contract = contracts.firstOrNull {
            it.monthlyRent == amount && (it.paymentDay == 0 || it.paymentDay == day)
        } ?: contracts.firstOrNull { it.monthlyRent == amount }
            ?: contracts.firstOrNull()
            ?: return null

        val normalizedName = contract.tenantName.replace(Regex("\\s+"), "").ifBlank { "unknown" }
        val normalizedPhone = contract.tenantPhone.filter(Char::isDigit).ifBlank { "nophone" }
        val key = "${normalizedName}_${normalizedPhone}_$unitId"

        database.tenantDao().insertTenant(
            Tenant(
                tenantKey = key,
                name = contract.tenantName,
                phone = contract.tenantPhone,
                unitId = unitId
            )
        )
        return key
    }
}
