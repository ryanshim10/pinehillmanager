package com.ryan.pinehill.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pinehill.data.AppDatabase
import com.ryan.pinehill.data.model.ContractRecord
import com.ryan.pinehill.data.model.Tenant
import com.ryan.pinehill.util.ContractDraft
import com.ryan.pinehill.util.ContractParser
import com.ryan.pinehill.util.DocumentOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingContractDraft(
    val uri: String,
    val sourceName: String,
    val draft: ContractDraft
)

class ContractsViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)

    val units = db.unitDao().getAllUnits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val contracts = db.contractDao().getAllContracts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pending = MutableStateFlow<List<PendingContractDraft>>(emptyList())
    val pending: StateFlow<List<PendingContractDraft>> = _pending.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun importDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            var ok = 0
            var failed = 0
            uris.distinct().take(100).forEach { uri ->
                runCatching {
                    val context = getApplication<Application>()
                    val text = DocumentOcr.extractText(context, uri)
                    val draft = ContractParser.parse(text)
                    _pending.value = _pending.value + PendingContractDraft(
                        uri = uri.toString(),
                        sourceName = DocumentOcr.displayName(context, uri),
                        draft = draft
                    )
                    ok++
                }.onFailure { failed++ }
            }
            _busy.value = false
            _message.value = "계약서 OCR $ok건 완료${if (failed > 0) " · 실패 $failed건" else ""}. 내용을 확인 후 저장하세요."
        }
    }

    fun importFolder(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            val context = getApplication<Application>()
            val uris = runCatching {
                DocumentOcr.collectFolderDocuments(context, treeUri)
            }.getOrDefault(emptyList())
            _busy.value = false
            if (uris.isEmpty()) {
                _message.value = "폴더에서 PDF/사진 계약서를 찾지 못했습니다."
            } else {
                importDocuments(uris)
            }
        }
    }

    fun savePending(pending: PendingContractDraft, record: ContractRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = record.copy(
                contractId = 0,
                documentUri = pending.uri,
                sourceName = pending.sourceName,
                rawOcrText = pending.draft.rawText,
                updatedAt = System.currentTimeMillis()
            )
            db.contractDao().insertContract(saved)
            ensureTenantUnitLink(saved)
            _pending.value = _pending.value.filterNot {
                it.uri == pending.uri && it.sourceName == pending.sourceName
            }
            _message.value = "${record.tenantName} · ${record.unitId.removePrefix("PINE-")}호 계약을 저장했습니다."
        }
    }

    fun updateContract(record: ContractRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = record.copy(updatedAt = System.currentTimeMillis())
            db.contractDao().updateContract(updated)
            ensureTenantUnitLink(updated)
            _message.value = "계약 내용을 수정했습니다. 같은 세입자가 여러 호실이면 호실별 관계로 유지됩니다."
        }
    }

    fun deleteContract(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            db.contractDao().deleteById(id)
            _message.value = "계약 이력을 삭제했습니다."
        }
    }

    private suspend fun ensureTenantUnitLink(record: ContractRecord): String {
        val key = tenantKey(record.tenantName, record.tenantPhone, record.unitId)
        db.tenantDao().insertTenant(
            Tenant(
                tenantKey = key,
                name = record.tenantName.trim(),
                phone = record.tenantPhone.trim(),
                unitId = record.unitId
            )
        )
        return key
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        fun tenantKey(name: String, phone: String, unitId: String): String {
            val normalizedName = name.replace(Regex("\\s+"), "").ifBlank { "unknown" }
            val normalizedPhone = phone.filter(Char::isDigit).ifBlank { "nophone" }
            return "${normalizedName}_${normalizedPhone}_$unitId"
        }
    }
}
