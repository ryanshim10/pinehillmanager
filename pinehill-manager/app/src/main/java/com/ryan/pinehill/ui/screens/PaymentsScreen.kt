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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.pinehill.data.model.Payment
import com.ryan.pinehill.data.model.PaymentMatchRule
import com.ryan.pinehill.data.model.PaymentStatus
import com.ryan.pinehill.data.model.Unit as RentalUnit
import com.ryan.pinehill.data.model.UnitStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(viewModel: PaymentsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val units by viewModel.units.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val monthPayments by viewModel.monthPayments.collectAsStateWithLifecycle()
    val pendingPayments by viewModel.pendingPayments.collectAsStateWithLifecycle()
    val historyUnitId by viewModel.historyUnitId.collectAsStateWithLifecycle()
    val historyPayments by viewModel.historyPayments.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()
    var page by remember { mutableIntStateOf(0) }

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
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                PageButton("월세", page == 0, Modifier.weight(1f)) { page = 0 }
                PageButton("문자/규칙", page == 1, Modifier.weight(1f)) { page = 1 }
                PageButton("호실 2년", page == 2, Modifier.weight(1f)) { page = 2 }
            }

            when (page) {
                0 -> MonthlyRentPage(
                    selectedMonth = selectedMonth,
                    units = units,
                    payments = monthPayments,
                    onPreviousMonth = { viewModel.moveMonth(-1) },
                    onNextMonth = { viewModel.moveMonth(1) },
                    onShowHistory = {
                        viewModel.selectHistoryUnit(it)
                        page = 2
                    }
                )

                1 -> SmsMatchingPage(
                    pendingPayments = pendingPayments,
                    allPayments = allPayments,
                    rules = rules,
                    units = units,
                    importMessage = importMessage,
                    onImport = ::importSms,
                    onMatch = viewModel::matchPaymentToUnit,
                    onDeleteRule = viewModel::deleteRule,
                    onDismissMessage = viewModel::clearMessage
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
private fun PageButton(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(text, maxLines = 1) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(text, maxLines = 1) }
}

@Composable
private fun MonthlyRentPage(
    selectedMonth: String,
    units: List<RentalUnit>,
    payments: List<Payment>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onShowHistory: (String) -> Unit
) {
    val rentUnits = units.filter { it.status == UnitStatus.RENTED }
    val paid = payments.filter { it.status == PaymentStatus.PAID || it.status == PaymentStatus.PARTIAL }
    val paidUnitIds = paid.map { it.unitId }.filter(String::isNotBlank).toSet()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = onPreviousMonth) { Text("‹ 이전") }
                Text(selectedMonth, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onNextMonth) { Text("다음 ›") }
            }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
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
                            val senders = unitPayments.mapNotNull { it.senderName }.distinct().joinToString(", ")
                            Text(
                                buildString {
                                    append(PaymentsViewModel.formatWon(unitPayments.sumOf { it.amount }))
                                    if (senders.isNotBlank()) append(" · $senders")
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text("눌러서 2년 입금내역 보기", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isPaid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            if (isPaid) "입금완료" else "미입금",
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmsMatchingPage(
    pendingPayments: List<Payment>,
    allPayments: List<Payment>,
    rules: List<PaymentMatchRule>,
    units: List<RentalUnit>,
    importMessage: String?,
    onImport: () -> Unit,
    onMatch: (Payment, String) -> Unit,
    onDeleteRule: (PaymentMatchRule) -> Unit,
    onDismissMessage: () -> Unit
) {
    var expandedPaymentId by remember { mutableStateOf<Long?>(null) }
    var selectedUnits by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var historySeed by remember { mutableStateOf<Payment?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text("최근 1년 은행 입금문자 검색")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "같은 은행+입금자 전체 내역은 한 히스토리로 보고, 자동 호실귀속은 금액+입금일까지 포함한 규칙으로 구분합니다.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (importMessage != null) item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(importMessage, Modifier.weight(1f))
                    TextButton(onClick = onDismissMessage) { Text("닫기") }
                }
            }
        }

        item {
            Text("저장된 자동매칭 규칙 ${rules.size}개", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        if (rules.isEmpty()) item {
            Text("입금내역 하나를 호실에 지정하면 규칙이 자동 생성됩니다.", style = MaterialTheme.typography.bodySmall)
        }

        items(rules, key = { "rule-${it.ruleId}" }) { rule ->
            val room = units.firstOrNull { it.unitId == rule.unitId }?.roomNo?.let { "${it}호" } ?: rule.unitId
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${rule.bankName} · ${rule.senderName}", fontWeight = FontWeight.Bold)
                        Text("${PaymentsViewModel.formatWon(rule.amount)} · 매월 ${rule.dayOfMonth}일 → $room")
                        if (rule.tenantName.isNotBlank()) {
                            Text("세입자 ${rule.tenantName}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { onDeleteRule(rule) }) { Text("규칙삭제") }
                }
            }
        }

        item {
            Text("미매칭 입금 ${pendingPayments.size}건", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "먼저 같은 은행·입금자의 전체 과거 내역을 확인한 뒤 호실을 지정할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (pendingPayments.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) { Text("매칭할 입금내역이 없습니다.", Modifier.padding(20.dp)) }
        }

        items(pendingPayments, key = { it.paymentId }) { payment ->
            val selectedUnitId = selectedUnits[payment.paymentId]
            val sameSenderCount = allPayments.count { PaymentsViewModel.sameSender(payment, it) }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "${PaymentsViewModel.bankName(payment).ifBlank { "은행미확인" }} · ${payment.senderName ?: "입금자 미확인"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text("${PaymentsViewModel.formatWon(payment.amount)} · ${formatPaymentDate(payment.paidAt)} · ${PaymentsViewModel.day(payment)}일")
                    Text("귀속월 ${payment.month}", style = MaterialTheme.typography.bodySmall)

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { historySeed = payment },
                        enabled = payment.senderName?.isNotBlank() == true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("같은 은행·입금자 전체 이력 보기 ($sameSenderCount)")
                    }

                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedButton(
                            onClick = { expandedPaymentId = payment.paymentId },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                selectedUnitId?.let { id ->
                                    units.firstOrNull { it.unitId == id }?.let { "${it.roomNo}호 선택됨" }
                                } ?: "호실 선택"
                            )
                        }
                        DropdownMenu(
                            expanded = expandedPaymentId == payment.paymentId,
                            onDismissRequest = { expandedPaymentId = null }
                        ) {
                            units.filter { it.status == UnitStatus.RENTED }.sortedBy { it.roomNo }.forEach { unit ->
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
                    ) {
                        Text("이 조건으로 과거 동일입금 일괄 매칭")
                    }
                }
            }
        }
    }

    historySeed?.let { seed ->
        SenderHistoryDialog(
            seed = seed,
            payments = allPayments.filter { PaymentsViewModel.sameSender(seed, it) },
            units = units,
            onDismiss = { historySeed = null }
        )
    }
}

@Composable
private fun SenderHistoryDialog(
    seed: Payment,
    payments: List<Payment>,
    units: List<RentalUnit>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().heightIn(max = 680.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("입금자 히스토리", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${PaymentsViewModel.bankName(seed).ifBlank { "은행미확인" }} · ${seed.senderName ?: "입금자 미확인"}",
                    fontWeight = FontWeight.Bold
                )
                Text("최근 앱에 저장된 동일 입금자 ${payments.size}건", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(payments.sortedByDescending { it.paidAt ?: it.createdAt }, key = { it.paymentId }) { payment ->
                        val room = if (payment.unitId.isBlank()) {
                            "미매칭"
                        } else {
                            units.firstOrNull { it.unitId == payment.unitId }?.roomNo?.let { "${it}호" } ?: payment.unitId
                        }
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(formatPaymentDate(payment.paidAt), fontWeight = FontWeight.Medium)
                                    Text("${payment.month} · $room", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(PaymentsViewModel.formatWon(payment.amount), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
            }
        }
    }
}

@Composable
private fun UnitHistoryPage(
    units: List<RentalUnit>,
    selectedUnitId: String?,
    payments: List<Payment>,
    onSelectUnit: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedUnit = units.firstOrNull { it.unitId == selectedUnitId }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("호실별 최근 24개월 입금내역", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedUnit?.let { "${it.roomNo}호" } ?: "호실 선택")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    units.sortedBy { it.roomNo }.forEach { unit ->
                        DropdownMenuItem(
                            text = { Text("${unit.roomNo}호") },
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
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${selectedUnit.roomNo}호", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("최근 24개월 ${payments.size}건 · ${PaymentsViewModel.formatWon(payments.sumOf { it.amount })}")
                    }
                }
            }

            if (payments.isEmpty()) item {
                Text("최근 24개월에 매칭된 입금내역이 없습니다.")
            }

            items(payments, key = { it.paymentId }) { payment ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${payment.month} · ${PaymentsViewModel.bankName(payment).ifBlank { "은행" }}",
                                fontWeight = FontWeight.Bold
                            )
                            Text("${formatPaymentDate(payment.paidAt)} · ${payment.senderName ?: "입금자 미확인"}")
                        }
                        Text(
                            PaymentsViewModel.formatWon(payment.amount),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun formatPaymentDate(timestamp: Long?): String =
    if (timestamp == null) "입금시각 미확인"
    else SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(timestamp))
