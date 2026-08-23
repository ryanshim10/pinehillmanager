package com.ryan.pinehill.ui.screens

import android.app.Application
import android.provider.Telephony
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pinehill.data.AppDatabase
import com.ryan.pinehill.data.model.Payment
import com.ryan.pinehill.data.model.PaymentMatchRule
import com.ryan.pinehill.data.model.PaymentSource
import com.ryan.pinehill.data.model.PaymentStatus
import com.ryan.pinehill.data.model.Tenant
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
    private val ruleDao = database.paymentMatchRuleDao()

    val units = unitDao.getAllUnits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules = ruleDao.getRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allPayments = paymentDao.getAllPayments()
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
                val cursor = resolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(
                        Telephony.Sms._ID,
                        Telephony.Sms.BODY,
                        Telephony.Sms.DATE,
                        Telephony.Sms.ADDRESS
                    ),
                    "${Telephony.Sms.DATE} >= ?",
                    arrayOf(oneYearAgo().toString()),
                    "${Telephony.Sms.DATE} DESC"
                )

                var scanned = 0
                var bankDeposits = 0
                var imported = 0

                cursor?.use { c ->
                    val bodyIndex = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateIndex = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    val addressIndex = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)

                    while (c.moveToNext()) {
                        scanned++
                        val body = c.getString(bodyIndex) ?: continue
                        val address = c.getString(addressIndex)
                        val parsed = SmsParser.parseDepositSms(body, address) ?: continue
                        bankDeposits++

                        val canonicalRaw = canonicalRawSms(parsed.bankName, body)
                        if (
                            paymentDao.countSmsByRawText(body) > 0 ||
                            paymentDao.countSmsByRawText(canonicalRaw) > 0
                        ) continue

                        val receivedAt = c.getLong(dateIndex)
                        paymentDao.insertPayment(
                            Payment(
                                tenantKey = null,
                                unitId = "",
                                month = SimpleDateFormat("yyyy-MM", Locale.KOREA).format(Date(receivedAt)),
                                paidAt = receivedAt,
                                amount = parsed.amount,
                                senderName = parsed.senderName,
                                source = PaymentSource.SMS,
                                status = PaymentStatus.PENDING,
                                rawSms = canonicalRaw
                            )
                        )
                        imported++
                    }
                }

                val autoMatched = applyAllRules()
                _importMessage.value = when {
                    bankDeposits == 0 -> "최근 1년 문자 ${scanned}건 중 인식 가능한 은행 입금문자가 없습니다."
                    else -> "은행 입금 ${bankDeposits}건 확인 · 새로 ${imported}건 · 규칙 자동매칭 ${autoMatched}건"
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
            val parsed = payment.rawSms?.let { SmsParser.parseDepositSms(it) }
            val bank = parsed?.bankName ?: "은행미확인"
            val sender = SmsParser.normalizeName(parsed?.senderName ?: payment.senderName)
            val day = SmsParser.dayOfMonth(payment.paidAt)
            val tenantKey = resolveTenantKey(unitId, payment.amount, day)

            if (sender.isBlank()) {
                paymentDao.updatePayment(
                    payment.copy(
                        tenantKey = tenantKey,
                        unitId = unitId,
                        status = PaymentStatus.PAID,
                        statusOverride = true
                    )
                )
                _historyUnitId.value = unitId
                _importMessage.value = "입금자명을 읽지 못해 이 1건만 ${unitId.removePrefix("PINE-")}호에 매칭했습니다."
                return@launch
            }

            val existing = ruleDao.findSameKey(bank, sender, payment.amount, day)
            if (existing != null && existing.unitId != unitId) {
                paymentDao.updatePayment(
                    payment.copy(
                        tenantKey = tenantKey,
                        unitId = unitId,
                        status = PaymentStatus.PAID,
                        statusOverride = true
                    )
                )
                _historyUnitId.value = unitId
                _importMessage.value = "주의: $bank · $sender · ${formatWon(payment.amount)} · ${day}일 규칙이 이미 ${existing.unitId.removePrefix("PINE-")}호에 있습니다. SMS만으로 두 호실을 구분할 수 없어 이번 1건만 수동 매칭했습니다."
                return@launch
            }

            val contract = database.contractDao().getContractsByUnitNow(unitId)
                .firstOrNull {
                    it.monthlyRent == payment.amount && (it.paymentDay == 0 || it.paymentDay == day)
                }
                ?: database.contractDao().getContractsByUnitNow(unitId)
                    .firstOrNull { it.monthlyRent == payment.amount }

            ruleDao.deleteSameKey(bank, sender, payment.amount, day)
            val rule = PaymentMatchRule(
                bankName = bank,
                senderName = sender,
                amount = payment.amount,
                dayOfMonth = day,
                unitId = unitId,
                tenantName = contract?.tenantName ?: payment.senderName.orEmpty()
            )
            ruleDao.insertRule(rule)

            val matched = applyRule(rule)
            _selectedMonth.value = payment.month
            _historyUnitId.value = unitId
            _importMessage.value = "$bank · ${payment.senderName ?: sender} · ${formatWon(payment.amount)} · ${day}일 → ${unitId.removePrefix("PINE-")}호 규칙 저장, 과거 ${matched}건 일괄 매칭"
        }
    }

    fun deleteRule(rule: PaymentMatchRule) {
        viewModelScope.launch(Dispatchers.IO) {
            ruleDao.deleteRule(rule)
            _importMessage.value = "매칭 규칙을 삭제했습니다. 이미 확정된 과거 입금은 유지됩니다."
        }
    }

    private suspend fun applyAllRules(): Int {
        var count = 0
        ruleDao.getEnabledRulesNow().forEach { count += applyRule(it) }
        return count
    }

    private suspend fun applyRule(rule: PaymentMatchRule): Int {
        var count = 0
        val tenantKey = resolveTenantKey(rule.unitId, rule.amount, rule.dayOfMonth)

        paymentDao.getPendingPaymentsNow().forEach { payment ->
            val parsed = payment.rawSms?.let { SmsParser.parseDepositSms(it) } ?: return@forEach
            val matches = parsed.bankName == rule.bankName &&
                SmsParser.normalizeName(parsed.senderName) == SmsParser.normalizeName(rule.senderName) &&
                payment.amount == rule.amount &&
                SmsParser.dayOfMonth(payment.paidAt) == rule.dayOfMonth

            if (matches) {
                paymentDao.updatePayment(
                    payment.copy(
                        tenantKey = tenantKey,
                        unitId = rule.unitId,
                        status = PaymentStatus.PAID,
                        statusOverride = true
                    )
                )
                count++
            }
        }
        return count
    }

    private suspend fun resolveTenantKey(unitId: String, amount: Long, day: Int): String? {
        val contracts = database.contractDao().getContractsByUnitNow(unitId)
        val contract = contracts.firstOrNull {
            it.monthlyRent == amount && (it.paymentDay == 0 || it.paymentDay == day)
        } ?: contracts.firstOrNull { it.monthlyRent == amount }
            ?: contracts.firstOrNull()
            ?: return null

        val key = tenantKey(contract.tenantName, contract.tenantPhone, unitId)
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

        private fun canonicalRawSms(bankName: String, body: String): String =
            if (body.contains(bankName, ignoreCase = true)) body else "[$bankName] $body"

        private fun tenantKey(name: String, phone: String, unitId: String): String {
            val normalizedName = name.replace(Regex("\\s+"), "").ifBlank { "unknown" }
            val normalizedPhone = phone.filter(Char::isDigit).ifBlank { "nophone" }
            return "${normalizedName}_${normalizedPhone}_$unitId"
        }

        fun formatWon(amount: Long): String = String.format(Locale.KOREA, "%,d원", amount)

        fun bankName(payment: Payment): String =
            payment.rawSms?.let { SmsParser.parseDepositSms(it)?.bankName }.orEmpty()

        fun day(payment: Payment): Int = SmsParser.dayOfMonth(payment.paidAt)

        fun sameSender(a: Payment, b: Payment): Boolean =
            bankName(a) == bankName(b) &&
                SmsParser.normalizeName(a.senderName) == SmsParser.normalizeName(b.senderName) &&
                SmsParser.normalizeName(a.senderName).isNotBlank()
    }
}
