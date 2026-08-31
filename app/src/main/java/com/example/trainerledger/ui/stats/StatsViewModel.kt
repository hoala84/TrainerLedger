package com.example.trainerledger.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trainerledger.TrainerApplication
import com.example.trainerledger.domain.model.Client
import com.example.trainerledger.domain.model.ClientSortOrder
import com.example.trainerledger.domain.model.PeriodStats
import com.example.trainerledger.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri

data class StatsFilter(
    val from: Long = DateUtils.startOfMonth(),
    val to: Long = DateUtils.startOfDay(),
    val clientId: Long? = null,
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as TrainerApplication).repository

    val filter = MutableStateFlow(StatsFilter())

    val clients: StateFlow<List<Client>> = repo.observeClientRows(MutableStateFlow(ClientSortOrder.ALPHABETICAL))
        .map { rows -> rows.map { it.client } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val stats: StateFlow<PeriodStats?> = combine(filter, repo.observeDataRevision()) { current, _ -> current }
        .flatMapLatest { current ->
            flow { emit(repo.periodStats(current.from, current.to, current.clientId)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPeriod(from: Long, to: Long) {
        filter.value = filter.value.copy(from = from, to = to)
    }

    fun setClient(clientId: Long?) {
        filter.value = filter.value.copy(clientId = clientId)
    }

    fun exportExcel(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                    val current = filter.value
                    val data = repo.excelData(current.from, current.to)
                    com.example.trainerledger.data.export.ExcelExporter.write(
                        output = output,
                        from = current.from,
                        to = current.to,
                        clients = if (current.clientId == null) {
                            data.clients
                        } else {
                            data.clients.filter { it.id == current.clientId }
                        },
                        payments = if (current.clientId == null) {
                            data.payments
                        } else {
                            data.payments.filter { it.clientId == current.clientId }
                        },
                        workouts = if (current.clientId == null) {
                            data.workouts
                        } else {
                            data.workouts.filter { it.clientId == current.clientId }
                        },
                    )
                } ?: error("Не удалось открыть файл")
            }.isSuccess
            onDone(ok)
        }
    }
}
