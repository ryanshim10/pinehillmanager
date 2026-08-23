package com.ryan.pinehill.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(viewModel: BackupViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestoreUri = uri
    }

    Scaffold(topBar = { TopAppBar(title = { Text("백업 · 복원") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Markdown 백업", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("호실, 세입자, 월세 입금, 지출, 계약 이력, 자동매칭 규칙을 하나의 .md 파일에 저장합니다.")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    "백업 파일에는 이름·전화번호·입금내역·계약 OCR 내용이 포함될 수 있으니 안전한 위치에 보관하세요.",
                    Modifier.padding(14.dp)
                )
            }
            Button(
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.KOREA).format(Date())
                    exportLauncher.launch("pinehill_backup_$stamp.md")
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("MD 백업 만들기") }

            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("text/markdown", "text/plain", "application/octet-stream")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("MD 백업에서 복원") }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (message != null) {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp)) {
                        Text(message!!, Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearMessage) { Text("닫기") }
                    }
                }
            }
            Text("복원 방식", fontWeight = FontWeight.Bold)
            Text("선택한 Pinehill MD 백업을 기준으로 현재 앱 데이터를 교체합니다. 복원 전에 현재 상태를 먼저 백업하는 것을 권장합니다.", style = MaterialTheme.typography.bodySmall)
        }
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("백업을 복원할까요?") },
            text = { Text("현재 앱 데이터는 백업 파일의 내용으로 교체됩니다. 이 작업 전 현재 상태를 별도 백업해 두는 것이 안전합니다.") },
            confirmButton = {
                Button(onClick = { viewModel.restoreFrom(uri); pendingRestoreUri = null }) { Text("복원") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text("취소") } }
        )
    }
}
