package com.ryan.pinehill.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.pinehill.data.AppDatabase
import com.ryan.pinehill.util.MarkdownBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun exportTo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            val result = runCatching {
                val markdown = MarkdownBackup.export(db)
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter(Charsets.UTF_8).use { it.write(markdown) }
                "MD 백업 저장 완료"
            }.getOrElse { "백업 실패: ${it.message}" }
            _busy.value = false
            _message.value = result
        }
    }

    fun restoreFrom(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _busy.value = true
            val result = runCatching {
                val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
                MarkdownBackup.restore(db, text).message
            }.getOrElse { "복원 실패: ${it.message}" }
            _busy.value = false
            _message.value = result
        }
    }

    fun clearMessage() { _message.value = null }
}
