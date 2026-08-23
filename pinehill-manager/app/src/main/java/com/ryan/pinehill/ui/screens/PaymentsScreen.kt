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
import com.ryan.pinehill.util.PaymentMatchSuggester
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
                PageButton("문자/확인", page == 1, Modifier.weight(1f)) { page = 1 }
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
                    onConfirm = viewModel::confirmPaymentToUnit,
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
    onConfirm: (Payment, String) -> Unit,
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
                "한 번 연결한 은행+입금자는 전부 한 묶음으로 불러옵니다. 정상 금액/일자는 자동 처리하고 다른 금액은 확인만 누르면 됩니다.",
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
            Text("저장된 입금자 규칙 ${rules.size}개", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        if (rules.isEmpty()) item {
            Text("처음 한 건만 호실을 지정하면 동일 입금자를 이후 자동으로 묶어 보여줍니다.", style = MaterialTheme.typography.bodySmall)
        }

        items(rules, key = { "rule-${it.ruleId}" }) { rule ->
            val room = roomLabel(rule.unitId, units)
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${rule.bankName} · ${rule.senderName}", fontWeight = FontWeight.Bold)
                        Text("정상패턴 ${PaymentsViewModel.formatWon(rule.amount)} · ${rule.dayOfMonth}일 → $room")
                        if (rule.tenantName.isNotBlank()) {
                            Text("세입자 ${rule.tenantName}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { onDeleteRule(rule) }) { Text("삭제") }
                }
            }
        }

        item {
            Text("확인 필요한 입금 ${pendingPayments.size}건", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "이미 연결한 입금자는 추천 호실이 표시됩니다. 금액이 달라도 맞는 입금이면 확인만 누르세요.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (pendingPayments.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) { Text("확인할 입금내역이 없습니다.", Modifier.padding(20.dp)) }
        }

        items(pendingPayments, key = { it.paymentId }) { payment ->
            val senderRules = PaymentMatchSuggester.sameSenderRules(payment, rules)
            val suggestedRule = PaymentMatchSuggester.suggestRule(payment, rules)
            val linkedUnitIds = senderRules.map { it.unitId }.distinct()
            val manuallySelected = selectedUnits[payment.paymentId]
            val selectedUnitId = manuallySelected ?: suggestedRule?.unitId
            val sameSenderCount = allPayments.count { PaymentsViewModel.sameSender(payment, it) }
            val amountDifferent = suggestedRule != null && payment.amount != suggestedRule.amount
            val selectableUnits = if (linkedUnitIds.isNotEmpty()) {
                units.filter { it.unitId in linkedUnitIds }
            } else {
                units.filter { it.status == UnitStatus.RENTED }
            }.sortedBy { it.roomNo }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "${PaymentsViewModel.bankName(payment).ifBlank { "은행미확인" }} · ${payment.senderName ?: "입금자 미확인"}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text("${PaymentsViewModel.formatWon(payment.amount)} · ${formatPaymentDate(payment.paidAt)}")
                    Text("귀속월 ${payment.month} · 입금일 ${PaymentsViewModel.day(payment)}일", style = MaterialTheme.typography.bodySmall)

                    if (senderRules.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text("동일 입금자 ${sameSenderCount}건 불러옴", fontWeight = FontWeight.Bold)
                                Text(
                                    "연결 호실: ${linkedUnitIds.joinToString(", ") { roomLabel(it, units) }}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (suggestedRule != null) {
                                    Text(
                                        "추천 ${roomLabel(suggestedRule.unitId, units)} · 기준 ${PaymentsViewModel.formatWon(suggestedRule.amount)} / ${suggestedRule.dayOfMonth}일",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (amountDifferent) {
                                        Text(
                                            "금액이 기존 패턴과 달라 자동 처리하지 않았습니다.",
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                } else {
                                    Text("여러 호실이라 금액/입금일만으로 구분이 안 됩니다.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { historySeed = payment },
                        enabled = payment.senderName?.isNotBlank() == true,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("동일 입금자 전체 히스토리 ($sameSenderCount)")
                    }

                    if (senderRules.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        UnitPicker(
                            paymentId = payment.paymentId,
                            selectedUnitId = selectedUnitId,
                            units = selectableUnits,
                            expandedPaymentId = expandedPaymentId,
                            onExpandedChange = { expandedPaymentId = it },
                            onSelected = { selectedUnits = selectedUnits + (payment.paymentId to it) }
                        )
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
                            Text("처음 연결 · 동일 입금자 묶기")
                        }
                    } else if (suggestedRule != null && manuallySelected == null) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onConfirm(payment, suggestedRule.unitId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("확인 → ${roomLabel(suggestedRule.unitId, units)}")
                        }
                        TextButton(
                            onClick = { expandedPaymentId = payment.paymentId },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("추천이 다르면 호실 변경") }
                        DropdownMenu(
                            expanded = expandedPaymentId == payment.paymentId,
                            onDismissRequest = { expandedPaymentId = null }
                        ) {
                            selectableUnits.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text("${unit.roomNo}호") },
                                    onClick = {
                                        selectedUnits = selectedUnits + (payment.paymentId to unit.unitId)
                                        expandedPaymentId = null
                                    }
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        UnitPicker(
                            paymentId = payment.paymentId,
                            selectedUnitId = selectedUnitId,
                            units = selectableUnits,
                            expandedPaymentId = expandedPaymentId,
                            onExpandedChange = { expandedPaymentId = it },
                            onSelected = { selectedUnits = selectedUnits + (payment.paymentId to it) }
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                selectedUnitId?.let {
                                    onConfirm(payment, it)
                                    selectedUnits = selectedUnits - payment.paymentId
                                }
                            },
                            enabled = selectedUnitId != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedUnitId?.let { "확인 → ${roomLabel(it, units)}" } ?: "호실 선택 후 확인")
                        }
                    }
                }
            }
        }
    }

    historySeed?.let { seed ->
        SenderHistoryDialog(
            seed = seed,
            payments = allPayments.filter { PaymentsViewModel.sameSender(seed, it) },
            rules = rules,
            units = units,
            onConfirm = onConfirm,
            onDismiss = { historySeed = null }
        )
    }
}

@Composable
private fun UnitPicker(
    paymentId: Long,
    selectedUnitId: String?,
    units: List<RentalUnit>,
    expandedPaymentId: Long?,
    onExpandedChange: (Long?) -> Unit,
    onSelected: (String) -> Unit
) {
    Box {
        OutlinedButton(
            onClick = { onExpandedChange(paymentId) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedUnitId?.let { roomLabel(it, units) } ?: "호실 선택")
        }
        DropdownMenu(
            expanded = expandedPaymentId == paymentId,
            onDismissRequest = { onExpandedChange(null) }
        ) {
            units.forEach { unit ->
                DropdownMenuItem(
                    text = { Text("${unit.roomNo}호 ${unit.roomType ?: ""}") },
                    onClick = {
                        onSelected(unit.unitId)
                        onExpandedChange(null)
                    }
                )
            }
        }
    }
}

@Composable
private fun SenderHistoryDialog(
    seed: Payment,
    payments: List<Payment>,
    rules: List<PaymentMatchRule>,
    units: List<RentalUnit>,
    onConfirm: (Payment, String) -> Unit,
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
                Text("동일 은행·입금자 ${payments.size}건", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(payments.sortedByDescending { it.paidAt ?: it.createdAt }, key = { it.paymentId }) { payment ->
                        val suggestedRule = PaymentMatchSuggester.suggestRule(payment, rules)
                        val room = if (payment.unitId.isBlank()) "확인필요" else roomLabel(payment.unitId, units)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(formatPaymentDate(payment.paidAt), fontWeight = FontWeight.Medium)
                                        Text("${payment.month} · $room", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(PaymentsViewModel.formatWon(payment.amount), fontWeight = FontWeight.Bold)
                                }
                                if (payment.status == PaymentStatus.PENDING && suggestedRule != null) {
                                    Spacer(Modifier.height(6.dp))
                                    Button(
                                        onClick = { onConfirm(payment, suggestedRule.unitId) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("확인 → ${roomLabel(suggestedRule.unitId, units)}")
                                    }
                                } else if (payment.status == PaymentStatus.PENDING) {
                                    Text(
                                        "여러 호실 후보라 문자/확인 화면에서 호실을 골라주세요.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
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
            Spacer(Modifier.height(8.dp))
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

private fun roomLabel(unitId: String, units: List<RentalUnit>): String =
    units.firstOrNull { it.unitId == unitId }?.roomNo?.let { "${it}호" } ?: unitId.removePrefix("PINE-") + "호"

private fun formatPaymentDate(timestamp: Long?): String =
    if (timestamp == null) "입금시각 미확인"
    else SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(timestamp))
