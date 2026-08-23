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
import com.ryan.pinehill.data.model.Payment
import com.ryan.pinehill.data.model.PaymentStatus
import com.ryan.pinehill.data.model.UnitStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(
    viewModel: PaymentsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val units by viewModel.units.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val monthPayments by viewModel.monthPayments.collectAsStateWithLifecycle()
    val pendingPayments by viewModel.pendingPayments.collectAsStateWithLifecycle()
    val historyUnitId by viewModel.historyUnitId.collectAsStateWithLifecycle()
    val historyPayments by viewModel.historyPayments.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()

    var page by remember { mutableIntStateOf(0) } // 0=월세현황, 1=문자매칭, 2=호실별2년

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.READ_SMS] == true) viewModel.importSmsInbox()
    }

    fun importSms() {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            viewModel.importSmsInbox()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("월세 입금 확인") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PageButton("월세 현황", page == 0, Modifier.weight(1f)) { page = 0 }
                PageButton("문자 매칭", page == 1, Modifier.weight(1f)) { page = 1 }
                PageButton("호실별 2년", page == 2, Modifier.weight(1f)) { page = 2 }
            }

            when (page) {
                0 -> MonthlyRentPage(
                    selectedMonth = selectedMonth,
                    units = units,
                    payments = monthPayments,
                    onPreviousMonth = { viewModel.moveMonth(-1) },
                    onNextMonth = { viewModel.moveMonth(1) },
                    onShowHistory = { unitId ->
                        viewModel.selectHistoryUnit(unitId)
                        page = 2
                    }
                )
                1 -> SmsMatchingPage(
                    pendingPayments = pendingPayments,
                    units = units,
                    importMessage = importMessage,
                    onImport = { importSms() },
                    onMatch = { payment, unitId -> viewModel.matchPaymentToUnit(payment, unitId) },
                    onDismissMessage = { viewModel.clearMessage() }
                )
                else -> UnitHistoryPage(
                    units = units,
                    selectedUnitId = historyUnitId,
                    payments = historyPayments,
                    onSelectUnit = viewModel::selectHistoryUnit
                )
            }
        }
    }
}

@Composable
private fun PageButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(text, maxLines = 1) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(text, maxLines = 1) }
}

@Composable
private fun MonthlyRentPage(
    selectedMonth: String,
    units: List<com.ryan.pinehill.data.model.Unit>,
    payments: List<Payment>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onShowHistory: (String) -> Unit
) {
    val rentUnits = units.filter { it.status == UnitStatus.RENTED }
    val paidUnitIds = payments.filter { it.status == PaymentStatus.PAID || it.status == PaymentStatus.PARTIAL }
        .map { it.unitId }.filter { it.isNotBlank() }.toSet()
    val paidCount = rentUnits.count { it.unitId in paidUnitIds }
    val totalPaid = payments.filter { it.status == PaymentStatus.PAID || it.status == PaymentStatus.PARTIAL }.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                    Text("${selectedMonth} 월세 수납", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("입금완료 $paidCount / ${rentUnits.size}호")
                    Text("미입금 ${rentUnits.size - paidCount}호")
                    Text("확인된 입금액 ${PaymentsViewModel.formatWon(totalPaid)}")
                }
            }
        }
        items(rentUnits, key = { it.unitId }) { unit ->
            val unitPayments = payments.filter { it.unitId == unit.unitId && (it.status == PaymentStatus.PAID || it.status == PaymentStatus.PARTIAL) }
            val isPaid = unitPayments.isNotEmpty()
            val paidAmount = unitPayments.sumOf { it.amount }
            val senderNames = unitPayments.mapNotNull { it.senderName }.distinct().joinToString(", ")

            Card(modifier = Modifier.fillMaxWidth().clickable { onShowHistory(unit.unitId) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${unit.roomNo}호", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (isPaid) {
                            Text(
                                buildString {
                                    append(PaymentsViewModel.formatWon(paidAmount))
                                    if (senderNames.isNotBlank()) append(" · $senderNames")
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else Text("눌러서 2년 입금내역 보기", style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isPaid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(if (isPaid) "입금완료" else "미입금", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmsMatchingPage(
    pendingPayments: List<Payment>,
    units: List<com.ryan.pinehill.data.model.Unit>,
    importMessage: String?,
    onImport: () -> Unit,
    onMatch: (Payment, String) -> Unit,
    onDismissMessage: () -> Unit
) {
    var expandedPaymentId by remember { mutableStateOf<Long?>(null) }
    var selectedUnits by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("최근 1년 카카오뱅크 입금문자 검색")
            }
        }
        if (importMessage != null) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(importMessage, Modifier.weight(1f))
                        TextButton(onClick = onDismissMessage) { Text("닫기") }
                    }
                }
            }
        }
        item {
            Text("미매칭 입금 ${pendingPayments.size}건", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("카카오뱅크 입금문자만 가져옵니다. 직접 호실을 지정하면 해당 월 월세로 인정됩니다.", style = MaterialTheme.typography.bodySmall)
        }
        if (pendingPayments.isEmpty()) {
            item { Card(Modifier.fillMaxWidth()) { Text("매칭할 입금내역이 없습니다.", Modifier.padding(20.dp)) } }
        }
        items(pendingPayments, key = { it.paymentId }) { payment ->
            val selectedUnitId = selectedUnits[payment.paymentId]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(payment.senderName ?: "입금자 미확인", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(formatPaymentDate(payment.paidAt))
                            Text("귀속월 ${payment.month}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(PaymentsViewModel.formatWon(payment.amount), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(12.dp))
                    Box {
                        OutlinedButton(onClick = { expandedPaymentId = payment.paymentId }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedUnitId?.let { id -> units.firstOrNull { it.unitId == id }?.let { "${it.roomNo}호 선택됨" } } ?: "호실 선택")
                        }
                        DropdownMenu(expanded = expandedPaymentId == payment.paymentId, onDismissRequest = { expandedPaymentId = null }) {
                            units.filter { it.status == UnitStatus.RENTED }.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text("${unit.roomNo}호 ${unit.roomType ?: ""}") },
                                    onClick = {
                                        selectedUnits = selectedUnits + (payment.paymentId to unit.unitId)
                                        expandedPaymentId = null
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            selectedUnitId?.let {
                                onMatch(payment, it)
                                selectedUnits = selectedUnits - payment.paymentId
                            }
                        },
                        enabled = selectedUnitId != null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("이 호실 월세로 매칭") }
                }
            }
        }
    }
}

@Composable
private fun UnitHistoryPage(
    units: List<com.ryan.pinehill.data.model.Unit>,
    selectedUnitId: String?,
    payments: List<Payment>,
    onSelectUnit: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedUnit = units.firstOrNull { it.unitId == selectedUnitId }
    val total = payments.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("호실별 최근 24개월 입금내역", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text("문자에서 직접 매칭한 입금내역을 최대 2년까지 보여줍니다.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedUnit?.let { "${it.roomNo}호" } ?: "호실 선택")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    units.sortedBy { it.roomNo }.forEach { unit ->
                        DropdownMenuItem(
                            text = { Text("${unit.roomNo}호 ${unit.roomType ?: ""}") },
                            onClick = {
                                onSelectUnit(unit.unitId)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        if (selectedUnit != null) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${selectedUnit.roomNo}호", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("최근 24개월 매칭 ${payments.size}건")
                        Text("누적 확인금액 ${PaymentsViewModel.formatWon(total)}")
                    }
                }
            }
            if (payments.isEmpty()) {
                item { Card(Modifier.fillMaxWidth()) { Text("최근 24개월에 매칭된 입금내역이 없습니다.", Modifier.padding(20.dp)) } }
            }
            items(payments, key = { it.paymentId }) { payment ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(payment.month, fontWeight = FontWeight.Bold)
                            Text(formatPaymentDate(payment.paidAt))
                            Text(payment.senderName ?: "입금자 미확인", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(PaymentsViewModel.formatWon(payment.amount), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

private fun formatPaymentDate(timestamp: Long?): String {
    if (timestamp == null) return "입금시각 미확인"
    return SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(timestamp))
}
