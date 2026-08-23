package com.ryan.pinehill.ui.screens

import android.app.Application
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pinehill.data.AppDatabase
import com.ryan.pinehill.data.model.Payment
import com.ryan.pinehill.data.model.PaymentSource
import com.ryan.pinehill.data.model.PaymentStatus
import com.ryan.pinehill.util.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PaymentsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val paymentDao = database.paymentDao()
    private val unitDao = database.unitDao()

    val units = unitDao.getAllUnits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedMonth = MutableStateFlow(currentMonth())
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    val monthPayments = _selectedMonth
        .flatMapLatest { paymentDao.getPaymentsByMonth(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingPayments = paymentDao.getPendingPayments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _historyUnitId = MutableStateFlow<String?>(null)
    val historyUnitId: StateFlow<String?> = _historyUnitId.asStateFlow()

    val historyPayments = _historyUnitId
        .flatMapLatest { unitId ->
            if (unitId.isNullOrBlank()) flowOf(emptyList())
            else paymentDao.getMatchedPaymentsByUnitSince(unitId, twoYearsAgo())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage.asStateFlow()

    fun moveMonth(delta: Int) {
        val format = SimpleDateFormat("yyyy-MM", Locale.KOREA)
        val calendar = Calendar.getInstance()
        runCatching { format.parse(_selectedMonth.value) }.getOrNull()?.let { calendar.time = it }
        calendar.add(Calendar.MONTH, delta)
        _selectedMonth.value = format.format(calendar.time)
    }

    fun selectHistoryUnit(unitId: String?) {
        _historyUnitId.value = unitId
    }

    fun importSmsInbox() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                val oneYearAgo = oneYearAgo()
                val cursor = resolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.ADDRESS),
                    "${Telephony.Sms.DATE} >= ?",
                    arrayOf(oneYearAgo.toString()),
                    "${Telephony.Sms.DATE} DESC"
                )

                var scanned = 0
                var kakaoDeposits = 0
                var imported = 0
                cursor?.use { c ->
                    val bodyIndex = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateIndex = c.getColumnIndexOrThrow(Telephony.Sms.DATE)

                    while (c.moveToNext()) {
                        scanned++
                        val body = c.getString(bodyIndex) ?: continue
                        if (!SmsParser.isKakaoBankDeposit(body)) continue

                        val parsed = SmsParser.parseDepositSms(body) ?: continue
                        kakaoDeposits++
                        if (paymentDao.countSmsByRawText(body) > 0) continue

                        val receivedAt = c.getLong(dateIndex)
                        val month = SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date(receivedAt))
                        paymentDao.insertPayment(
                            Payment(
                                tenantKey = null,
                                unitId = "",
                                month = month,
                                paidAt = receivedAt,
                                amount = parsed.amount,
                                senderName = parsed.senderName,
                                source = PaymentSource.SMS,
                                status = PaymentStatus.PENDING,
                                rawSms = body
                            )
                        )
                        imported++
                    }
                }

                _importMessage.value = when {
                    kakaoDeposits == 0 -> "최근 1년 문자 ${scanned}건을 확인했지만 카카오뱅크 입금문자를 찾지 못했습니다."
                    else -> "최근 1년 카카오뱅크 입금 ${kakaoDeposits}건 확인 · 새로 ${imported}건 추가"
                }
            } catch (_: SecurityException) {
                _importMessage.value = "문자 읽기 권한이 필요합니다."
            } catch (e: Exception) {
                _importMessage.value = "문자 불러오기 실패: ${e.message ?: "알 수 없는 오류"}"
            }
        }
    }

    fun matchPaymentToUnit(payment: Payment, unitId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            paymentDao.updatePayment(
                payment.copy(
                    unitId = unitId,
                    status = PaymentStatus.PAID,
                    statusOverride = true
                )
            )
            _selectedMonth.value = payment.month
            _historyUnitId.value = unitId
            _importMessage.value = "${payment.senderName ?: "입금"} ${formatWon(payment.amount)} → ${unitId.removePrefix("PINE-")}호 매칭 완료"
        }
    }

    fun clearMessage() {
        _importMessage.value = null
    }

    companion object {
        private fun currentMonth(): String =
            SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date())

        private fun oneYearAgo(): Long = Calendar.getInstance().apply {
            add(Calendar.YEAR, -1)
        }.timeInMillis

        private fun twoYearsAgo(): Long = Calendar.getInstance().apply {
            add(Calendar.YEAR, -2)
        }.timeInMillis

        fun formatWon(amount: Long): String = String.format(Locale.KOREA, "%,d원", amount)
    }
}
