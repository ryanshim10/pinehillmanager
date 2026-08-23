package com.ryan.pinehill.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.pinehill.data.model.ContractRecord
import com.ryan.pinehill.data.model.Payment
import com.ryan.pinehill.data.model.PaymentMatchRule
import com.ryan.pinehill.data.model.Unit as RentalUnit
import com.ryan.pinehill.data.model.UnitStatus
import com.ryan.pinehill.util.RentHistoryCalculator
import com.ryan.pinehill.util.RentMonthRow
import com.ryan.pinehill.util.RentMonthState
import com.ryan.pinehill.util.SmsParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsScreen(
    viewModel: UnitsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onUnitClick: (RentalUnit) -> kotlin.Unit = {}
) {
    val units by viewModel.units.collectAsStateWithLifecycle()
    val payments by viewModel.payments.collectAsStateWithLifecycle()
    val contracts by viewModel.contracts.collectAsStateWithLifecycle()
    val matchRules by viewModel.matchRules.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf<UnitStatus?>(null) }
    var selectedUnitId by remember { mutableStateOf<String?>(null) }

    val selectedUnit = units.firstOrNull { it.unitId == selectedUnitId }
    if (selectedUnit != null) {
        UnitRentHistoryDetail(
            unit = selectedUnit,
            payments = payments,
            contracts = contracts,
            matchRules = matchRules,
            onBack = { selectedUnitId = null }
        )
        return
    }

    val filteredUnits = units.filter { unit ->
        val matchesSearch = searchQuery.isEmpty() ||
            unit.unitId.contains(searchQuery, ignoreCase = true) ||
            unit.roomNo.toString().contains(searchQuery)
        val matchesStatus = selectedStatus == null || unit.status == selectedStatus
        matchesSearch && matchesStatus
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("호실별 월세 현황") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            UnitStatsCard(units = units)
            StatusFilterChips(
                selectedStatus = selectedStatus,
                onStatusSelected = { selectedStatus = it }
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("호실 검색 (예: 303)") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            Text(
                "호실을 누르면 최근 24개월 월세 납부내역을 확인할 수 있습니다.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredUnits, key = { it.unitId }) { unit ->
                    val currentRent = RentHistoryCalculator.build24Months(
                        unitId = unit.unitId,
                        payments = payments,
                        contracts = contracts
                    ).firstOrNull()
                    UnitCard(
                        unit = unit,
                        currentRent = currentRent,
                        onClick = {
                            selectedUnitId = unit.unitId
                            onUnitClick(unit)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitRentHistoryDetail(
    unit: RentalUnit,
    payments: List<Payment>,
    contracts: List<ContractRecord>,
    matchRules: List<PaymentMatchRule>,
    onBack: () -> kotlin.Unit
) {
    val unitContracts = contracts.filter { it.unitId == unit.unitId }
        .sortedByDescending { it.startDate.ifBlank { it.createdAt.toString() } }
    val rows = RentHistoryCalculator.build24Months(unit.unitId, payments, unitContracts)
    val current = rows.firstOrNull()
    val currentContract = current?.contract ?: unitContracts.firstOrNull()
    val linkedRules = matchRules.filter { it.unitId == unit.unitId && it.enabled }
    val linkedSenders = linkedRules
        .distinctBy { "${it.bankName}|${SmsParser.normalizeName(it.senderName)}" }

    val paidCount = rows.count { it.state == RentMonthState.PAID }
    val partialCount = rows.count { it.state == RentMonthState.PARTIAL }
    val unpaidCount = rows.count { it.state == RentMonthState.UNPAID }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${unit.roomNo}호 월세 히스토리") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← 호실") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${unit.roomNo}호", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text(unit.getStatusText())
                            }
                            current?.let { RentStateBadge(it.state) }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (currentContract != null) {
                            Text("세입자  ${currentContract.tenantName}", fontWeight = FontWeight.Bold)
                            Text("월세  ${formatWon(currentContract.monthlyRent)} · 매월 ${currentContract.paymentDay.takeIf { it > 0 }?.let { "${it}일" } ?: "납부일 미지정"}")
                            Text("보증금  ${formatWon(currentContract.deposit)}")
                            if (currentContract.startDate.isNotBlank() || currentContract.endDate.isNotBlank()) {
                                Text("계약  ${currentContract.startDate.ifBlank { "?" }} ~ ${currentContract.endDate.ifBlank { "?" }}")
                            }
                        } else {
                            Text("등록된 계약 정보가 없습니다.")
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("최근 24개월 요약", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("완납 ${paidCount}개월 · 부분입금 ${partialCount}개월 · 미입금 ${unpaidCount}개월")
                        Text(
                            "문자로 호실에 확정한 입금은 아래 월별 내역에 그대로 연결됩니다.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Text("문자 입금자 연결", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            if (linkedSenders.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            "아직 이 호실에 연결된 문자 입금자가 없습니다. 월세 → 문자/규칙에서 한 번 연결하면 여기에 표시됩니다.",
                            Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(linkedSenders, key = { "sender-${it.ruleId}" }) { rule ->
                    val sameSenderRules = linkedRules.filter {
                        it.bankName == rule.bankName &&
                            SmsParser.normalizeName(it.senderName) == SmsParser.normalizeName(rule.senderName)
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("${rule.bankName} · ${rule.senderName}", fontWeight = FontWeight.Bold)
                            Text(
                                sameSenderRules.joinToString(" / ") {
                                    "${formatWon(it.amount)} · ${it.dayOfMonth}일"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text("월별 월세 납부내역", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("최신월부터 최근 24개월", style = MaterialTheme.typography.bodySmall)
            }

            items(rows, key = { it.month }) { row ->
                RentMonthCard(row)
            }

            if (unitContracts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("이 호실 계약 이력", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                items(unitContracts, key = { "contract-${it.contractId}" }) { contract ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(contract.tenantName, fontWeight = FontWeight.Bold)
                            Text("${contract.startDate.ifBlank { "시작일 ?" }} ~ ${contract.endDate.ifBlank { "종료일 ?" }}")
                            Text("보증금 ${formatWon(contract.deposit)} · 월세 ${formatWon(contract.monthlyRent)} · ${contract.paymentDay.takeIf { it > 0 }?.let { "매월 ${it}일" } ?: "납부일 미지정"}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RentMonthCard(row: RentMonthRow) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(row.month, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                RentStateBadge(row.state)
            }

            Spacer(Modifier.height(5.dp))
            when (row.state) {
                RentMonthState.NO_CONTRACT -> Text("해당 월 계약정보 없음", style = MaterialTheme.typography.bodySmall)
                RentMonthState.PAYMENT_ONLY -> Text("계약정보는 없지만 매칭된 입금이 있습니다.", style = MaterialTheme.typography.bodySmall)
                else -> Text("월세 ${formatWon(row.expectedRent)} · 확인입금 ${formatWon(row.paidAmount)}")
            }

            if (row.contract != null) {
                Text(
                    "세입자 ${row.contract.tenantName}${row.contract.paymentDay.takeIf { it > 0 }?.let { " · 납부일 ${it}일" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (row.payments.isEmpty()) {
                if (row.state == RentMonthState.UNPAID) {
                    Text("입금 기록 없음", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
            } else {
                Spacer(Modifier.height(8.dp))
                row.payments.forEach { payment ->
                    val parsed = payment.rawSms?.let { SmsParser.parseDepositSms(it) }
                    val bank = parsed?.bankName.orEmpty().ifBlank { "은행" }
                    val sender = payment.senderName ?: parsed?.senderName ?: "입금자 미확인"
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${formatPaymentDate(payment.paidAt)} · $bank")
                            Text(sender, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(formatWon(payment.amount), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RentStateBadge(state: RentMonthState) {
    val label = when (state) {
        RentMonthState.PAID -> "완납"
        RentMonthState.PARTIAL -> "부분입금"
        RentMonthState.UNPAID -> "미입금"
        RentMonthState.BEFORE_DUE -> "납부일 전"
        RentMonthState.NO_CONTRACT -> "계약없음"
        RentMonthState.PAYMENT_ONLY -> "입금기록"
    }
    val color = when (state) {
        RentMonthState.PAID -> Color(0xFF2E7D32)
        RentMonthState.PARTIAL -> Color(0xFFF57C00)
        RentMonthState.UNPAID -> Color(0xFFC62828)
        RentMonthState.BEFORE_DUE -> Color(0xFF1565C0)
        RentMonthState.NO_CONTRACT -> Color(0xFF616161)
        RentMonthState.PAYMENT_ONLY -> Color(0xFF6A1B9A)
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun UnitStatsCard(units: List<RentalUnit>) {
    val total = units.size
    val rented = units.count { it.status == UnitStatus.RENTED }
    val vacant = units.count { it.status == UnitStatus.VACANT }
    val maintenance = units.count { it.status == UnitStatus.MAINTENANCE }
    val lawsuit = units.count { it.status == UnitStatus.LAWSUIT }

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("총 $total 세대", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("임대중", rented, Color(0xFF4CAF50))
                StatItem("공실", vacant, Color(0xFF9E9E9E))
                StatItem("정비중", maintenance, Color(0xFFFF9800))
                StatItem("소송", lawsuit, Color(0xFFF44336))
            }
        }
    }
}

@Composable
fun StatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Text(text = label, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusFilterChips(
    selectedStatus: UnitStatus?,
    onStatusSelected: (UnitStatus?) -> kotlin.Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedStatus == null,
            onClick = { onStatusSelected(null) },
            label = { Text("전체") }
        )
        FilterChip(
            selected = selectedStatus == UnitStatus.RENTED,
            onClick = { onStatusSelected(UnitStatus.RENTED) },
            label = { Text("임대중") }
        )
        FilterChip(
            selected = selectedStatus == UnitStatus.VACANT,
            onClick = { onStatusSelected(UnitStatus.VACANT) },
            label = { Text("공실") }
        )
    }
}

@Composable
fun UnitCard(
    unit: RentalUnit,
    currentRent: RentMonthRow?,
    onClick: () -> kotlin.Unit
) {
    val statusColor = when (unit.status) {
        UnitStatus.RENTED -> Color(0xFF4CAF50)
        UnitStatus.VACANT -> Color(0xFF9E9E9E)
        UnitStatus.MAINTENANCE -> Color(0xFFFF9800)
        UnitStatus.LAWSUIT -> Color(0xFFF44336)
        UnitStatus.OTHER -> Color(0xFF607D8B)
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).background(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = unit.roomNo.toString(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("${unit.roomNo}호", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("${unit.floor}층 ${unit.roomType ?: ""} · ${unit.getStatusText()}", fontSize = 13.sp)
                currentRent?.contract?.let {
                    Text(
                        "${it.tenantName} · 월세 ${formatWon(it.monthlyRent)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("눌러서 24개월 확인", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }

            currentRent?.let { RentStateBadge(it.state) }
        }
    }
}

private fun formatWon(amount: Long): String = String.format(Locale.KOREA, "%,d원", amount)

private fun formatPaymentDate(timestamp: Long?): String =
    if (timestamp == null) "입금시각 미확인"
    else SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(timestamp))
