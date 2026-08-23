package com.ryan.pinehill.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.pinehill.data.model.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(viewModel: PaymentsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val units by viewModel.units.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val monthPayments by viewModel.monthPayments.collectAsStateWithLifecycle()
    val pendingPayments by viewModel.pendingPayments.collectAsStateWithLifecycle()
    val historyUnitId by viewModel.historyUnitId.collectAsStateWithLifecycle()
    val historyPayments by viewModel.historyPayments.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()
    var page by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.READ_SMS] == true) viewModel.importSmsInbox()
    }
    fun importSms() {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) viewModel.importSmsInbox()
        else permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
    }

    Scaffold(topBar = { TopAppBar(title = { Text("월세 입금 확인") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PageButton("월세", page == 0, Modifier.weight(1f)) { page = 0 }
                PageButton("문자/규칙", page == 1, Modifier.weight(1f)) { page = 1 }
                PageButton("호실 2년", page == 2, Modifier.weight(1f)) { page = 2 }
            }
            when (page) {
                0 -> MonthlyRentPage(selectedMonth, units, monthPayments, { viewModel.moveMonth(-1) }, { viewModel.moveMonth(1) }) {
                    viewModel.selectHistoryUnit(it); page = 2
                }
                1 -> SmsMatchingPage(
                    pendingPayments, rules, units, importMessage, ::importSms,
                    viewModel::matchPaymentToUnit, viewModel::deleteRule, viewModel::clearMessage
                )
                else -> UnitHistoryPage(units, historyUnitId, historyPayments, viewModel::selectHistoryUnit)
            }
        }
    }
}

@Composable
private fun PageButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick, modifier) { Text(text, maxLines = 1) }
    else OutlinedButton(onClick, modifier) { Text(text, maxLines = 1) }
}

@Composable
private fun MonthlyRentPage(
    selectedMonth: String,
    units: List<Unit>,
    payments: List<Payment>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onShowHistory: (String) -> Unit
) {
    val rentUnits = units.filter { it.status == UnitStatus.RENTED }
    val paid = payments.filter { it.status == PaymentStatus.PAID || it.status == PaymentStatus.PARTIAL }
    val paidUnitIds = paid.map { it.unitId }.filter(String::isNotBlank).toSet()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = onPreviousMonth) { Text("‹ 이전") }
                Text(selectedMonth, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onNextMonth) { Text("다음 ›") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("$selectedMonth 월세 수납", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("입금완료 ${rentUnits.count { it.unitId in paidUnitIds }} / ${rentUnits.size}호")
                    Text("미입금 ${rentUnits.count { it.unitId !in paidUnitIds }}호")
                    Text("확인된 입금액 ${PaymentsViewModel.formatWon(paid.sumOf { it.amount })}")
                }
            }
        }
        items(rentUnits, key = { it.unitId }) { unit ->
            val unitPayments = paid.filter { it.unitId == unit.unitId }
            val isPaid = unitPayments.isNotEmpty()
            Card(Modifier.fillMaxWidth().clickable { onShowHistory(unit.unitId) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${unit.roomNo}호", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (isPaid) {
                            Text(
                                "${PaymentsViewModel.formatWon(unitPayments.sumOf { it.amount })} · ${unitPayments.mapNotNull { it.senderName }.distinct().joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else Text("눌러서 2년 입금내역 보기", style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isPaid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    ) { Text(if (isPaid) "입금완료" else "미입금", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun SmsMatchingPage(
    pendingPayments: List<Payment>,
    rules: List<PaymentMatchRule>,
    units: List<Unit>,
    importMessage: String?,
    onImport: () -> Unit,
    onMatch: (Payment, String) -> Unit,
    onDeleteRule: (PaymentMatchRule) -> Unit,
    onDismissMessage: () -> Unit
) {
    var expandedPaymentId by remember { mutableStateOf<Long?>(null) }
    var selectedUnits by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("최근 1년 은행 입금문자 검색") }
            Text("은행명 + 입금자명 + 금액 + 입금일을 하나의 규칙으로 저장합니다.", style = MaterialTheme.typography.bodySmall)
        }
        if (importMessage != null) item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(importMessage, Modifier.weight(1f)); TextButton(onClick = onDismissMessage) { Text("닫기") }
                }
            }
        }
        item { Text("저장된 자동매칭 규칙 ${rules.size}개", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (rules.isEmpty()) item { Text("입금내역 하나를 호실에 지정하면 규칙이 자동 생성됩니다.", style = MaterialTheme.typography.bodySmall) }
        items(rules, key = { "rule-${it.ruleId}" }) { rule ->
            val room = units.firstOrNull { it.unitId == rule.unitId }?.roomNo?.let { "${it}호" } ?: rule.unitId
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${rule.bankName} · ${rule.senderName}", fontWeight = FontWeight.Bold)
                        Text("${PaymentsViewModel.formatWon(rule.amount)} · 매월 ${rule.dayOfMonth}일 → $room")
                        if (rule.tenantName.isNotBlank()) Text("세입자 ${rule.tenantName}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onDeleteRule(rule) }) { Text("규칙삭제") }
                }
            }
        }

        item {
            Text("미매칭 입금 ${pendingPayments.size}건", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("한 건을 지정하면 같은 은행·이름·금액·입금일의 최근 1년 과거 입금도 함께 매칭됩니다.", style = MaterialTheme.typography.bodySmall)
        }
        if (pendingPayments.isEmpty()) item { Card(Modifier.fillMaxWidth()) { Text("매칭할 입금내역이 없습니다.", Modifier.padding(20.dp)) } }
        items(pendingPayments, key = { it.paymentId }) { payment ->
            val selectedUnitId = selectedUnits[payment.paymentId]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${PaymentsViewModel.bankName(payment).ifBlank { "은행미확인" }} · ${payment.senderName ?: "입금자 미확인"}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("${PaymentsViewModel.formatWon(payment.amount)} · ${formatPaymentDate(payment.paidAt)} · ${PaymentsViewModel.day(payment)}일")
                    Text("귀속월 ${payment.month}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedButton(onClick = { expandedPaymentId = payment.paymentId }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedUnitId?.let { id -> units.firstOrNull { it.unitId == id }?.let { "${it.roomNo}호 선택됨" } } ?: "호실 선택")
                        }
                        DropdownMenu(expanded = expandedPaymentId == payment.paymentId, onDismissRequest = { expandedPaymentId = null }) {
                            units.filter { it.status == UnitStatus.RENTED }.sortedBy { it.roomNo }.forEach { unit ->
                                DropdownMenuItem(text = { Text("${unit.roomNo}호 ${unit.roomType ?: ""}") }, onClick = {
                                    selectedUnits = selectedUnits + (payment.paymentId to unit.unitId); expandedPaymentId = null
                                })
                            }
                        }
                    }
                    Button(
                        onClick = { selectedUnitId?.let { onMatch(payment, it); selectedUnits = selectedUnits - payment.paymentId } },
                        enabled = selectedUnitId != null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("이 규칙으로 과거 동일입금 일괄 매칭") }
                }
            }
        }
    }
}

@Composable
private fun UnitHistoryPage(units: List<Unit>, selectedUnitId: String?, payments: List<Payment>, onSelectUnit: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedUnit = units.firstOrNull { it.unitId == selectedUnitId }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("호실별 최근 24개월 입금내역", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedUnit?.let { "${it.roomNo}호" } ?: "호실 선택") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    units.sortedBy { it.roomNo }.forEach { unit -> DropdownMenuItem(text = { Text("${unit.roomNo}호") }, onClick = { onSelectUnit(unit.unitId); expanded = false }) }
                }
            }
        }
        if (selectedUnit != null) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${selectedUnit.roomNo}호", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("최근 24개월 ${payments.size}건 · ${PaymentsViewModel.formatWon(payments.sumOf { it.amount })}")
                    }
                }
            }
            if (payments.isEmpty()) item { Text("최근 24개월에 매칭된 입금내역이 없습니다.") }
            items(payments, key = { it.paymentId }) { payment ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${payment.month} · ${PaymentsViewModel.bankName(payment).ifBlank { "은행" }}", fontWeight = FontWeight.Bold)
                            Text("${formatPaymentDate(payment.paidAt)} · ${payment.senderName ?: "입금자 미확인"}")
                        }
                        Text(PaymentsViewModel.formatWon(payment.amount), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private fun formatPaymentDate(timestamp: Long?): String = if (timestamp == null) "입금시각 미확인"
else SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(timestamp))
