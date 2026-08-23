package com.ryan.pinehill.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.pinehill.data.model.ContractRecord
import com.ryan.pinehill.data.model.Unit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractsScreen(viewModel: ContractsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val units by viewModel.units.collectAsStateWithLifecycle()
    val contracts by viewModel.contracts.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    var editPending by remember { mutableStateOf<PendingContractDraft?>(null) }
    var editSaved by remember { mutableStateOf<ContractRecord?>(null) }

    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.importDocuments(uris)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::importFolder)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("세입자 · 계약 이력") }) }) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { filesLauncher.launch(arrayOf("application/pdf", "image/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text("파일/사진") }
                    OutlinedButton(onClick = { folderLauncher.launch(null) }, modifier = Modifier.weight(1f)) { Text("폴더 OCR") }
                }
                Text("PDF·사진을 선택하면 계약 내용을 OCR해 수정 가능한 초안을 만듭니다.", style = MaterialTheme.typography.bodySmall)
            }

            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (message != null) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(message!!, Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearMessage) { Text("닫기") }
                    }
                }
            }

            if (pending.isNotEmpty()) {
                item { Text("OCR 검토 대기 ${pending.size}건", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                items(pending.size) { index ->
                    val p = pending[index]
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(p.sourceName.ifBlank { "계약서" }, fontWeight = FontWeight.Bold)
                            Text("${p.draft.tenantName.ifBlank { "세입자 미확인" }} · ${p.draft.roomNo?.let { "${it}호" } ?: "호실 미확인"}")
                            Text("보증금 ${money(p.draft.deposit)} / 월세 ${money(p.draft.monthlyRent)} / 납부 ${p.draft.paymentDay.takeIf { it > 0 }?.let { "${it}일" } ?: "미확인"}")
                            Button(onClick = { editPending = p }, modifier = Modifier.fillMaxWidth()) { Text("내용 확인·수정 후 저장") }
                        }
                    }
                }
            }

            item { Text("세입자별 계약 히스토리", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            if (contracts.isEmpty()) item {
                Card(Modifier.fillMaxWidth()) { Text("저장된 계약이 없습니다.", Modifier.padding(20.dp)) }
            }
            contracts.groupBy { it.tenantName.ifBlank { "세입자 미확인" } }
                .toSortedMap().forEach { (tenant, history) ->
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(tenant, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                val unitText = history.mapNotNull { c -> units.firstOrNull { it.unitId == c.unitId }?.roomNo }.distinct().sorted().joinToString(", ") { "${it}호" }
                                Text("계약 ${history.size}건${if (unitText.isNotBlank()) " · $unitText" else ""}")
                            }
                        }
                    }
                    items(history.sortedByDescending { it.startDate }.size) { idx ->
                        val c = history.sortedByDescending { it.startDate }[idx]
                        val room = units.firstOrNull { it.unitId == c.unitId }?.roomNo?.let { "${it}호" } ?: c.unitId
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(room, fontWeight = FontWeight.Bold)
                                    Text("${c.startDate.ifBlank { "시작일?" }} ~ ${c.endDate.ifBlank { "종료일?" }}")
                                }
                                Text("보증금 ${money(c.deposit)} · 월세 ${money(c.monthlyRent)} · 매월 ${c.paymentDay.takeIf { it > 0 } ?: "?"}일")
                                if (c.tenantPhone.isNotBlank()) Text(c.tenantPhone, style = MaterialTheme.typography.bodySmall)
                                if (c.sourceName.isNotBlank()) Text("원본: ${c.sourceName}", style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { editSaved = c }, modifier = Modifier.weight(1f)) { Text("수정") }
                                    TextButton(onClick = { viewModel.deleteContract(c.contractId) }, modifier = Modifier.weight(1f)) { Text("삭제") }
                                }
                            }
                        }
                    }
                }
        }
    }

    editPending?.let { pendingDraft ->
        ContractEditorDialog(
            initial = ContractRecord(
                tenantName = pendingDraft.draft.tenantName,
                tenantPhone = pendingDraft.draft.tenantPhone,
                unitId = units.firstOrNull { it.roomNo == pendingDraft.draft.roomNo }?.unitId.orEmpty(),
                deposit = pendingDraft.draft.deposit,
                monthlyRent = pendingDraft.draft.monthlyRent,
                paymentDay = pendingDraft.draft.paymentDay,
                startDate = pendingDraft.draft.startDate,
                endDate = pendingDraft.draft.endDate,
                notes = ""
            ),
            units = units,
            title = "OCR 계약 검토",
            onDismiss = { editPending = null },
            onSave = {
                viewModel.savePending(pendingDraft, it)
                editPending = null
            }
        )
    }

    editSaved?.let { saved ->
        ContractEditorDialog(
            initial = saved,
            units = units,
            title = "계약 내용 수정",
            onDismiss = { editSaved = null },
            onSave = {
                viewModel.updateContract(it.copy(
                    contractId = saved.contractId,
                    documentUri = saved.documentUri,
                    sourceName = saved.sourceName,
                    rawOcrText = saved.rawOcrText,
                    createdAt = saved.createdAt
                ))
                editSaved = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContractEditorDialog(
    initial: ContractRecord,
    units: List<Unit>,
    title: String,
    onDismiss: () -> Unit,
    onSave: (ContractRecord) -> Unit
) {
    var tenantName by remember(initial) { mutableStateOf(initial.tenantName) }
    var phone by remember(initial) { mutableStateOf(initial.tenantPhone) }
    var unitId by remember(initial) { mutableStateOf(initial.unitId) }
    var deposit by remember(initial) { mutableStateOf(initial.deposit.takeIf { it > 0 }?.toString().orEmpty()) }
    var rent by remember(initial) { mutableStateOf(initial.monthlyRent.takeIf { it > 0 }?.toString().orEmpty()) }
    var day by remember(initial) { mutableStateOf(initial.paymentDay.takeIf { it > 0 }?.toString().orEmpty()) }
    var start by remember(initial) { mutableStateOf(initial.startDate) }
    var end by remember(initial) { mutableStateOf(initial.endDate) }
    var notes by remember(initial) { mutableStateOf(initial.notes) }
    var expanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().heightIn(max = 720.dp)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(tenantName, { tenantName = it }, label = { Text("세입자 이름") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("전화번호") }, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(units.firstOrNull { it.unitId == unitId }?.let { "${it.roomNo}호" } ?: "호실 선택")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        units.sortedBy { it.roomNo }.forEach { unit ->
                            DropdownMenuItem(text = { Text("${unit.roomNo}호") }, onClick = { unitId = unit.unitId; expanded = false })
                        }
                    }
                }
                OutlinedTextField(deposit, { deposit = it.filter(Char::isDigit) }, label = { Text("보증금(원)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rent, { rent = it.filter(Char::isDigit) }, label = { Text("월세(원)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(day, { day = it.filter(Char::isDigit).take(2) }, label = { Text("입금/납부일(1~31)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(start, { start = it }, label = { Text("계약 시작 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(end, { end = it }, label = { Text("계약 종료 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("메모") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, Modifier.weight(1f)) { Text("취소") }
                    Button(
                        onClick = {
                            onSave(initial.copy(
                                tenantName = tenantName.trim(), tenantPhone = phone.trim(), unitId = unitId,
                                deposit = deposit.toLongOrNull() ?: 0, monthlyRent = rent.toLongOrNull() ?: 0,
                                paymentDay = (day.toIntOrNull() ?: 0).coerceIn(0,31), startDate = start.trim(), endDate = end.trim(), notes = notes.trim()
                            ))
                        },
                        enabled = tenantName.isNotBlank() && unitId.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("저장") }
                }
            }
        }
    }
}

private fun money(value: Long): String = if (value <= 0) "미확인" else String.format(Locale.KOREA, "%,d원", value)
