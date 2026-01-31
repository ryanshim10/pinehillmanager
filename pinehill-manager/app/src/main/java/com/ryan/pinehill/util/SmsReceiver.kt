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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    
    companion object {
        const val TAG = "SmsReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            messages?.forEach { sms ->
                val sender = sms.displayOriginatingAddress
                val body = sms.displayMessageBody
                
                Log.d(TAG, "SMS from: $sender, body: $body")
                
                // 카카오뱅크 문자만 처리
                if (sender?.contains("카카오뱅크") == true || body.contains("[카카오뱅크]")) {
                    processKakaoBankSms(context, body)
                }
            }
        }
    }
    
    private fun processKakaoBankSms(context: Context, text: String) {
        val database = AppDatabase.getDatabase(context)
        val paymentDao = database.paymentDao()
        val expenseDao = database.expenseDao()
        
        CoroutineScope(Dispatchers.IO).launch {
            // 입금 파싱 시도
            val parsedPayment = SmsParser.parseDepositSms(text)
            if (parsedPayment != null) {
                val payment = Payment(
                    tenantKey = null,  // 미확정
                    unitId = "",       // 미확정
                    month = SmsParser.getMonthString(parsedPayment.dateStr),
                    paidAt = SmsParser.parseToTimestamp(parsedPayment.dateStr, parsedPayment.timeStr),
                    amount = parsedPayment.amount,
                    senderName = parsedPayment.senderName,
                    source = PaymentSource.SMS,
                    status = PaymentStatus.PENDING,
                    rawSms = text
                )
                paymentDao.insertPayment(payment)
                Log.d(TAG, "Payment inserted: ${payment.amount}")
                return@launch
            }
            
            // 출금 파싱 시도
            val parsedExpense = SmsParser.parseWithdrawalSms(text)
            if (parsedExpense != null) {
                val expense = Expense(
                    spentAt = SmsParser.parseToTimestamp(parsedExpense.dateStr, parsedExpense.timeStr),
                    amount = parsedExpense.amount,
                    category = ExpenseCategory.OTHER,  // 공용지출 기본
                    memo = parsedExpense.memo ?: "",
                    unitId = null,  // 공용
                    month = SmsParser.getMonthString(parsedExpense.dateStr),
                    source = ExpenseSource.SMS,
                    rawSms = text
                )
                expenseDao.insertExpense(expense)
                Log.d(TAG, "Expense inserted: ${expense.amount}")
            }
        }
    }
}
