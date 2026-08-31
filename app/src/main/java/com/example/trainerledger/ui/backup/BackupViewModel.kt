package com.example.trainerledger.ui.backup

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trainerledger.TrainerApplication
import com.example.trainerledger.data.backup.BackupFile
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as TrainerApplication).repository
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun exportBackup(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                    val backup = repo.exportSnapshot()
                    output.bufferedWriter().use { writer ->
                        writer.write(json.encodeToString(BackupFile.serializer(), backup))
                    }
                } ?: error("Не удалось открыть файл")
            }.isSuccess
            onDone(ok)
        }
    }

    fun importBackup(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                    val text = input.bufferedReader().use { it.readText() }
                    repo.restore(json.decodeFromString(BackupFile.serializer(), text))
                } ?: error("Не удалось открыть файл")
            }.isSuccess
            onDone(ok)
        }
    }
}
