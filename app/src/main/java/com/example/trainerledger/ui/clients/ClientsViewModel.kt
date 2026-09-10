package com.example.trainerledger.ui.clients

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trainerledger.TrainerApplication
import com.example.trainerledger.domain.model.ClientRow
import com.example.trainerledger.domain.model.ClientSortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ClientsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as TrainerApplication).repository

    val sortOrder = MutableStateFlow(ClientSortOrder.BY_UPDATED)

    val clients: StateFlow<List<ClientRow>> = repo.observeClientRows(sortOrder)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSort(order: ClientSortOrder) {
        sortOrder.value = order
    }

    suspend fun saveClient(client: com.example.trainerledger.domain.model.Client) {
        repo.saveClient(client)
    }
}
