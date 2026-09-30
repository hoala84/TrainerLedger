package com.example.trainerledger.ui.clients

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trainerledger.TrainerApplication
import com.example.trainerledger.domain.model.ClientRow
import com.example.trainerledger.domain.model.ClientSortOrder
import com.example.trainerledger.domain.model.Payment
import com.example.trainerledger.domain.model.Workout
import com.example.trainerledger.domain.model.WorkoutType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClientsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as TrainerApplication).repository

    private val sortOrder = MutableStateFlow(ClientSortOrder.BY_UPDATED)

    val clients: StateFlow<List<ClientRow>> = repo.observeClientRows(sortOrder)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun saveClient(client: com.example.trainerledger.domain.model.Client) {
        repo.saveClient(client)
    }

    suspend fun workouts(clientId: Long): List<Workout> = repo.observeWorkouts(clientId).first()

    fun addWorkout(clientId: Long, date: Long, comment: String, type: WorkoutType) = viewModelScope.launch {
        repo.addWorkout(clientId, date, comment, type)
    }

    suspend fun addPayment(clientId: Long, date: Long, amount: Double, count: Int, debtCount: Int) {
        repo.savePayment(Payment(clientId = clientId, date = date, amount = amount, workoutCount = count), debtCount)
    }
}
